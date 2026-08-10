package com.mcmory.backend.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 액세스 쿠키의 JWT를 읽어 SecurityContext를 채움. 토큰이 없거나 무효면 아무것도 하지 않고 통과시킴 — 401 판정은 각 API가
 * 함(프로토타입 라우트가 그랬고 `/api/v1/auth/me`는 미인증에도 200이어야 하므로 필터에서 막으면 안 됨).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final CookieUtils cookies;

	private final Tokens tokens;

	public JwtAuthenticationFilter(CookieUtils cookies, Tokens tokens) {
		this.cookies = cookies;
		this.tokens = tokens;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String accessToken = this.cookies.read(request, CookieUtils.ACCESS_COOKIE);
		if (accessToken != null) {
			Long memberId = this.tokens.readMemberId(accessToken);
			if (memberId != null) {
				var authentication = new UsernamePasswordAuthenticationToken(memberId, null,
						List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		chain.doFilter(request, response);
	}

}
