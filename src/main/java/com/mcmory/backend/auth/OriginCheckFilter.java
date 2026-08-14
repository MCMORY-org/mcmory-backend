package com.mcmory.backend.auth;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.mcmory.backend.global.apiPayload.code.BaseErrorCode;
import com.mcmory.backend.global.apiPayload.code.CommonErrorCode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ADR-013 결정 7: 액세스 토큰을 쿠키에 넣으면 브라우저가 요청에 쿠키를 자동으로 실어 CSRF 노출면이 생김. 같은 오리진 배포와
 * SameSite=Lax로 크게 줄지만 상태 변경 요청에 Origin 검사를 더함. 별도 CSRF 토큰은 두지 않음.
 *
 * Origin 헤더가 아예 없는 요청(서버 간 호출, 스모크 스크립트, 앱 네이티브 호출)은 통과시킴 — 브라우저가 붙이는 헤더의 부재를 근거로 막으면 정상
 * 클라이언트가 죽음.
 */
@Component
public class OriginCheckFilter extends OncePerRequestFilter {

	private static final Set<String> STATE_CHANGING = Set.of("POST", "PUT", "PATCH", "DELETE");

	/** 프론트 로컬 dev 서버(Vite 5173, Next 3000). CorsConfig도 이 목록을 씀 */
	public static final List<String> LOCAL_DEV_ORIGINS = List.of("http://localhost:5173", "http://127.0.0.1:5173",
			"http://localhost:3000", "http://127.0.0.1:3000");

	private final List<String> allowedOrigins;

	public OriginCheckFilter(@Value("${app.auth.allowed-origins:}") List<String> allowedOrigins) {
		List<String> origins = new ArrayList<>(allowedOrigins);
		origins.addAll(LOCAL_DEV_ORIGINS);
		this.allowedOrigins = List.copyOf(origins);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String origin = request.getHeader(HttpHeaders.ORIGIN);

		if (STATE_CHANGING.contains(request.getMethod()) && StringUtils.hasText(origin)
				&& !isAllowed(request, origin)) {
			// 필터는 DispatcherServlet 앞이라 GlobalExceptionHandler가 잡지 못함. 봉투를 여기서 직접 써야
			// 응답 계약이 계층과 무관하게 유지됨 — 안 그러면 이 403만 날것으로 새어 프론트 파싱이 깨짐
			writeEnvelope(response, CommonErrorCode.FORBIDDEN_ORIGIN);
			return;
		}

		chain.doFilter(request, response);
	}

	private void writeEnvelope(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter()
			.write("{\"isSuccess\":false,\"code\":\"" + errorCode.getCode() + "\",\"message\":\""
					+ errorCode.getMessage() + "\",\"result\":null}");
	}

	private boolean isAllowed(HttpServletRequest request, String origin) {
		if (this.allowedOrigins.contains(origin)) {
			return true;
		}
		try {
			URI uri = URI.create(origin);
			// 같은 호스트면 같은 오리진으로 본다. 프록시 뒤에서는 포트가 달라 보일 수 있어 호스트만 비교함
			return request.getServerName().equalsIgnoreCase(uri.getHost());
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

}
