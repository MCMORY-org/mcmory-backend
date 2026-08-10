package com.mcmory.backend.common;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오류 응답 계약임. 검증 대상은 상태 코드가 아니라 **어떤 오류든 `error` 키를 가진 우리 형태로 나가는가**다.
 *
 * 이 테스트가 있는 이유: 스프링 기본 오류(`{"timestamp","status","error","path"}`)도 400과 500을 내므로 상태 코드만
 * 보는 검사는 새어나간 응답을 잡지 못한다. 실제로 두 번 놓쳤다 — 요청 본문 파싱 실패와 필드 누락 NPE.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorContractIntegrationTest extends HttpIntegrationSupport {

	@BeforeEach
	void 미인증_상태로_시작() {
		clearCookies();
	}

	@Test
	void 매핑되지_않은_경로는_404이고_봉투_형태다() {
		Response response = get("/api/v1/그런거없음");

		assertThat(response.status()).isEqualTo(404);
		assertThat(봉투인가(response)).as(response.text()).isTrue();
	}

	@Test
	void 깨진_JSON_본문은_400이고_봉투_형태다() {
		Response response = post("/api/v1/auth/login", "{이건 JSON이 아니다");

		assertThat(response.status()).isEqualTo(400);
		assertThat(봉투인가(response)).as(response.text()).isTrue();
	}

	@Test
	void 경로_변수_타입이_어긋나면_400이고_봉투_형태다() {
		// 인증보다 먼저 바인딩에서 걸린다. 여기서 500이 나가면 클라이언트가 자기 잘못을 서버 장애로 오인함
		Response response = get("/api/v1/recommend/숫자아님");

		assertThat(response.status()).isEqualTo(400);
		assertThat(봉투인가(response)).as(response.text()).isTrue();
	}

	@Test
	void 지원하지_않는_메서드는_405이고_봉투_형태다() {
		Response response = get("/api/v1/auth/login");

		assertThat(response.status()).isEqualTo(405);
		assertThat(봉투인가(response)).as(response.text()).isTrue();
	}

	@Test
	void 미인증_보호_경로는_401이고_봉투_형태다() {
		Response response = get("/api/v1/owned");

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.code()).isEqualTo("AUTH401_1");
		assertThat(response.body().get("error").asString()).isEqualTo("로그인이 필요합니다");
	}

	@Test
	void 성공_응답도_봉투로_나간다() {
		Response response = get("/api/v1/stores");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.text()).contains("\"isSuccess\":true").contains("\"code\":\"200\"");
		// 봉투를 벗긴 result가 실제 데이터여야 함
		assertThat(response.body().get("list").isArray()).isTrue();
	}

	@Test
	void 필터가_막는_403도_봉투로_나간다() {
		// 필터는 DispatcherServlet 앞이라 GlobalExceptionHandler의 사각지대임 — 봉투를 직접 써야 하고,
		// 그 사실을 지키는 검증이 여기다(이 테스트가 없어서 실제로 날것인 채 문서만 봉투라고 적혀 있었음)
		Response response = postFromOrigin("/api/v1/auth/login", "{}", "http://evil.example.com");

		assertThat(response.status()).isEqualTo(403);
		assertThat(봉투인가(response)).as(response.text()).isTrue();
		assertThat(response.code()).isEqualTo("COMMON403");
	}

	@Test
	void 오류_응답에_내부_구조가_새지_않는다() {
		Response response = post("/api/v1/auth/login", "{이건 JSON이 아니다");

		// 예외 메시지를 그대로 실으면 클래스명과 파서 내부가 노출됨
		assertThat(response.text()).doesNotContain("Exception")
			.doesNotContain("com.mcmory")
			.doesNotContain("tools.jackson");
	}

	/** 봉투 계약임: isSuccess가 false이고 code가 있고 스프링 기본 오류의 timestamp가 없어야 함. */
	private boolean 봉투인가(Response response) {
		return response.text().contains("\"isSuccess\":false") && response.code() != null
				&& !response.text().contains("timestamp");
	}

}
