package com.mcmory.backend.common;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프론트 로컬 dev 서버(Vite 5173)에서 붙을 때의 CORS 계약임. 쿠키 인증이라 allow-credentials가 함께 와야 하고, 허용 밖
 * 오리진에는 헤더를 주지 않되 봉투 계약은 유지돼야 함.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsIntegrationTest extends HttpIntegrationSupport {

	@Test
	void 로컬_프론트_오리진의_프리플라이트가_통과한다() {
		Response response = preflight("/api/v1/auth/login", "http://localhost:5173");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.headers().firstValue("access-control-allow-origin")).hasValue("http://localhost:5173");
		// 액세스 토큰이 쿠키라 이 헤더가 없으면 프론트가 로그인 상태를 유지하지 못함
		assertThat(response.headers().firstValue("access-control-allow-credentials")).hasValue("true");
	}

	@Test
	void 허용_밖_오리진에는_CORS_헤더를_주지_않는다() {
		Response response = preflight("/api/v1/auth/login", "http://evil.example.com");

		assertThat(response.headers().firstValue("access-control-allow-origin")).isEmpty();
		// CorsFilter가 직접 막으면 날것의 403이 나간다. 그 회귀를 헤더 부재만으로는 못 잡음
		assertThat(response.status()).isNotEqualTo(403);
	}

}
