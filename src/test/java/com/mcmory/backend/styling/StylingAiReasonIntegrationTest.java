package com.mcmory.backend.styling;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `aiReason` 옵트인이 실제로 문구 작성기를 부르는지 보는 것임.
 *
 * **별도 클래스인 이유**: 빈을 갈아 끼우면 Spring 컨텍스트가 새로 떠서 콜드 스타트가 약 2분 붙음(컨테이너 재사용 없음).
 * `StylingIntegrationTest`에 섞으면 그 클래스 전체가 느려짐.
 *
 * **Bedrock을 부르지 않음** — 스텁이 고정 문자열을 돌려줌. 통합 테스트가 외부를 부르면 결과가 비결정적이 됨.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StylingAiReasonIntegrationTest extends HttpIntegrationSupport {

	private static final String STUB_REASON = "스텁이 쓴 문구예요";

	@TestConfiguration
	static class StubWriterConfig {

		@Bean
		@Primary
		StylingReasonWriter stubWriter() {
			return (base, suggestion, matchedTag) -> STUB_REASON;
		}

	}

	private long ownedId;

	@BeforeEach
	void 로그인하고_보유_제품을_만든다() {
		clearCookies();
		loginAs("01012345678", "1234");
		post("/api/v1/owned", "{\"serial\":\"MX2024A031\"}");
		this.ownedId = get("/api/v1/owned").body().get("list").get(0).get("id").asLong();
	}

	/** 옵트인하면 작성기가 쓴 문구가 첫 항목에 실리고 `reasonSource`가 LLM이 됨. */
	@Test
	void 옵트인하면_작성기_문구가_나가고_reasonSource가_LLM이다() {
		var response = get("/api/v1/owned/" + this.ownedId + "/styling?aiReason=true");

		assertThat(response.status()).as(response.text()).isEqualTo(200);
		assertThat(response.body().get("reasonSource").asString()).isEqualTo("LLM");
		assertThat(response.body().get("results").get(0).get("reason").asString()).isEqualTo(STUB_REASON);
	}

	/**
	 * **기본 호출은 작성기를 아예 안 부름.** 스텁이 붙어 있는데도 규칙 문구가 나오는 것이 그 증거임 — 이 단언이 깨지면 옵트인이 무력해져 비용과
	 * 지연이 되살아남.
	 */
	@Test
	void 기본_호출은_작성기를_부르지_않는다() {
		var response = get("/api/v1/owned/" + this.ownedId + "/styling");

		assertThat(response.body().get("reasonSource").asString()).isEqualTo("RULE");
		assertThat(response.body().get("results").get(0).get("reason").asString()).isNotEqualTo(STUB_REASON);
	}

}
