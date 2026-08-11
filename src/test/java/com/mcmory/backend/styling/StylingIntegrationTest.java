package com.mcmory.backend.styling;

import java.util.ArrayList;
import java.util.List;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-023 AI 스타일링 계약임(화면 CONTINUE-02). 지금까지 이 경로가 없어 제품 사진을 누르면 데드엔드였음.
 *
 * **테스트는 LLM을 부르지 않음** — `bedrock.enabled` 기본값이 false라 비활성 구현이 붙고 결과가 결정론적임. 외부 호출이 섞이면
 * 회귀를 못 잡음.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StylingIntegrationTest extends HttpIntegrationSupport {

	private static final String SENDER_PHONE = "01012345678";

	private static final String OTHER_PHONE = "01099998888";

	private static final String PASSWORD = "1234";

	/** 시드 상품 1(Tracy 비세토스 크로스바디, 가방)의 데모 시리얼임. 시드에 보유 제품 행이 없어 테스트가 직접 만듦. */
	private static final String DEMO_SERIAL = "MX2024A031";

	private long ownedId;

	@BeforeEach
	void 로그인하고_보유_제품을_만든다() {
		clearCookies();
		loginAs(SENDER_PHONE, PASSWORD);
		this.ownedId = 보유_제품을_만든다();
	}

	/**
	 * 시리얼 등록은 같은 상품을 두 번 등록하면 `OWNED409_1`임. 클래스 안 테스트가 여럿이라 이미 있으면 목록에서 찾아 씀.
	 */
	private long 보유_제품을_만든다() {
		post("/api/v1/owned", "{\"serial\":\"" + DEMO_SERIAL + "\"}");

		var list = get("/api/v1/owned").body().get("list");
		assertThat(list).as("보유 제품 등록에 실패함").isNotEmpty();
		return list.get(0).get("id").asLong();
	}

	@Test
	void 보유_제품에_추천_3건과_근거가_온다() {
		var response = get("/api/v1/owned/" + this.ownedId + "/styling");
		assertThat(response.status()).as(response.text()).isEqualTo(200);

		var result = response.body();
		assertThat(result.get("product").get("productId").asLong()).isPositive();
		assertThat(result.get("results")).hasSize(3);

		// 화면이 근거·카테고리·가격·보러가기를 그림
		var first = result.get("results").get(0);
		assertThat(first.get("productId").asLong()).isPositive();
		assertThat(first.get("name").asString()).isNotBlank();
		assertThat(first.get("category").asString()).isNotBlank();
		assertThat(first.get("reason").asString()).isNotBlank();
		assertThat(first.get("officialUrl").asString()).startsWith("https://");
	}

	/** ADR-009: 첫 항목만 개인화 근거임. 나머지는 고정 문구임. */
	@Test
	void 첫_항목만_PERSONAL이다() {
		var results = get("/api/v1/owned/" + this.ownedId + "/styling").body().get("results");

		List<String> types = new ArrayList<>();
		results.forEach((item) -> types.add(item.get("reasonType").asString()));

		assertThat(types).containsExactly("PERSONAL", "GENERAL", "GENERAL");
	}

	/** 화면이 코디를 그리므로 의류가 나와야 함. 안 그러면 가방 하나에 지갑과 카드케이스가 붙어 코디가 아니라 상품 목록이 됨. */
	@Test
	void 코디용_의류가_추천에_포함된다() {
		var results = get("/api/v1/owned/" + this.ownedId + "/styling").body().get("results");

		List<String> categories = new ArrayList<>();
		results.forEach((item) -> categories.add(item.get("category").asString()));

		assertThat(categories).as("코디 화면인데 의류가 없음: " + categories)
			.anySatisfy((category) -> assertThat(category).startsWith("WOMAN"));
	}

	/** 보유 제품과 같은 제품군은 제안하지 않음(기능명세서의 `제품군 겹치지 않게`). 결과끼리도 겹치지 않음. */
	@Test
	void 제품군이_겹치지_않는다() {
		var body = get("/api/v1/owned/" + this.ownedId + "/styling").body();
		String baseCategory = body.get("product").get("category").asString();

		List<String> categories = new ArrayList<>();
		body.get("results").forEach((item) -> categories.add(item.get("category").asString()));

		assertThat(categories).doesNotContain(baseCategory);
		assertThat(categories).doesNotHaveDuplicates();
	}

	/**
	 * 기본 호출은 모델을 아예 부르지 않으므로 언제나 규칙 문구임. 화면이 AI라고 단정하는 문구는 `reasonSource`가 LLM일 때만 써야 함.
	 */
	@Test
	void 기본_호출은_reasonSource가_RULE이다() {
		var response = get("/api/v1/owned/" + this.ownedId + "/styling");

		assertThat(response.body().get("reasonSource").asString()).isEqualTo("RULE");
		assertThat(response.body().get("results").get(0).get("reason").asString()).isNotBlank();
	}

	/**
	 * 옵트인해도 이 테스트 환경은 `bedrock.enabled`가 기본값 false라 규칙 문구가 나감. **오류가 아니라 정상 동작임** — 통합
	 * 테스트가 외부를 부르지 않아야 결과가 결정론적임.
	 */
	@Test
	void 옵트인해도_Bedrock이_꺼져_있으면_RULE이다() {
		var response = get("/api/v1/owned/" + this.ownedId + "/styling?aiReason=true");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.body().get("reasonSource").asString()).isEqualTo("RULE");
	}

	/** 잘못된 불리언은 스프링 타입 변환에서 걸림 — 우리 검증 400과 구분되는 기존 계약임. */
	@Test
	void 잘못된_aiReason_값은_400이다() {
		var response = get("/api/v1/owned/" + this.ownedId + "/styling?aiReason=foo");

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("VALID400_0");
	}

	@Test
	void 남의_보유_제품과_없는_id는_같은_404다() {
		clearCookies();
		loginAs(OTHER_PHONE, PASSWORD);

		var others = get("/api/v1/owned/" + this.ownedId + "/styling");
		assertThat(others.status()).isEqualTo(404);
		assertThat(others.code()).isEqualTo("OWNED404_2");

		var missing = get("/api/v1/owned/999999/styling");
		assertThat(missing.status()).isEqualTo(404);
		assertThat(missing.code()).isEqualTo("OWNED404_2");
	}

	@Test
	void 비로그인은_401이다() {
		clearCookies();

		var response = get("/api/v1/owned/" + this.ownedId + "/styling");
		assertThat(response.status()).isEqualTo(401);
		assertThat(response.code()).isEqualTo("AUTH401_1");
	}

}
