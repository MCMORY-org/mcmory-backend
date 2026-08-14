package com.mcmory.backend.config;

import java.util.ArrayList;
import java.util.List;

import com.mcmory.backend.auth.OriginCheckFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 프론트가 다른 오리진(로컬 dev 서버)에서 붙을 때 필요함. 액세스 토큰이 쿠키라 allowCredentials가 true여야 하고, 그러면
 * allowedOrigins에 와일드카드를 못 씀 — 그래서 목록을 명시함.
 *
 * 허용 목록은 app.auth.allowed-origins(Origin 검사와 같은 값)에 로컬 dev 오리진을 더한 것임. 로컬 오리진을 코드에 박은 이유는
 * 배포 ENV 시크릿이 통짜라 키 하나 추가가 위험하기 때문임(AGENTS.md 5장).
 */
@Configuration
public class CorsConfig {

	private final List<String> allowedOrigins;

	public CorsConfig(@Value("${app.auth.allowed-origins:}") List<String> configured) {
		List<String> origins = new ArrayList<>(configured);
		origins.addAll(OriginCheckFilter.LOCAL_DEV_ORIGINS);
		this.allowedOrigins = List.copyOf(origins);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(this.allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);

		// 허용 밖 오리진에는 설정을 주지 않고 그대로 흘려보냄(spring-web 7 기준 — 6.x는 null이면
		// 프리플라이트를 날것 403으로 막았으므로 다운그레이드하면 이 전제가 깨짐). CorsFilter가 직접 막으면 날것의
		// "Invalid CORS request" 403이 나가 봉투 계약이 깨짐 — 차단은 OriginCheckFilter가 맡음.
		// 응답에 ACAO 헤더가 없으니 브라우저는 어차피 결과를 못 읽음
		return (request) -> {
			String origin = request.getHeader(HttpHeaders.ORIGIN);
			return (origin != null && this.allowedOrigins.contains(origin)) ? config : null;
		};
	}

}
