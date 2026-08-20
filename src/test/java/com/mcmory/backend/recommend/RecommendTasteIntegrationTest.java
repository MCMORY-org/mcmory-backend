package com.mcmory.backend.recommend;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수신자 답변이 추천을 실제로 바꾸는지 지키는 테스트임(FIX-W002 T1).
 *
 * 시드 근거: 친구 101의 취향이 블랙과 미니멀임. 기본 조건(친구, 50에서 150만원)에서 취향이 없으면 1순위가 상품 6이고 있으면 상품 1임.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecommendTasteIntegrationTest extends HttpIntegrationSupport {

	/** 화면 HOME-01 슬라이더 기본값(만원). 시드 계산의 전제라 바꾸면 아래 기대값이 함께 바뀜. */
	private static final String DEFAULT_BUDGET = "\"minBudget\":50,\"maxBudget\":150";

	/** 취향 시드가 붙어 있는 유일한 친구임. 다른 테스트가 회원 1에게 친구를 더 만들지만 기대값은 상품 카탈로그에만 의존함. */
	private static final long FRIEND_WITH_TASTE = 101L;

	@BeforeEach
	void 로그인() {
		clearCookies();
		loginAs("01012345678", "1234");
	}

	@Test
	void 친구_취향이_있으면_1순위가_바뀌고_근거가_그_취향을_말한다() {
		var without = post("/api/v1/recommend", "{\"relation\":\"친구\"," + DEFAULT_BUDGET + "}");
		var with = post("/api/v1/recommend",
				"{\"relation\":\"친구\",\"friendId\":" + FRIEND_WITH_TASTE + "," + DEFAULT_BUDGET + "}");

		assertThat(without.status()).as(without.text()).isEqualTo(200);
		assertThat(with.status()).as(with.text()).isEqualTo(200);

		var firstWithout = without.body().get("results").get(0);
		var firstWith = with.body().get("results").get(0);

		// 취향을 안 보내면 예전 그대로여야 함 — 이 단언이 기존 스모크와 커버리지 테스트의 전제를 지킴
		assertThat(firstWithout.get("reason").asString()).contains("캐주얼");

		// 상품 id를 못박음. "서로 다르다"로만 두면 다른 블랙 상품으로 바뀌어도 통과해 이 회귀를 놓침
		assertThat(firstWithout.get("product").get("id").asLong()).as("취향 없는 1순위가 시드 계산과 다름").isEqualTo(19L);
		assertThat(firstWith.get("product").get("id").asLong())
			.as("취향이 1순위를 바꾸지 못함. taste_profile을 안 읽는 상태로 되돌아갔는지 볼 것")
			.isEqualTo(39L);
		assertThat(firstWith.get("product").get("color").asString()).isEqualTo("블랙");
		assertThat(firstWith.get("reasonType").asString()).isEqualTo("PERSONAL");
		assertThat(firstWith.get("reason").asString()).contains("블랙");

		// 스냅샷이 어느 친구의 취향으로 뽑았는지를 재현해야 함
		long recommendationId = with.body().get("recommendationId").asLong();
		var snapshot = get("/api/v1/recommend/" + recommendationId);
		assertThat(snapshot.body().get("context").get("friendId").asLong()).isEqualTo(FRIEND_WITH_TASTE);
	}

	/** 가드가 없으면 발송자의 취향 저장이 수신자 답변을 지워 추천이 관계와 예산만 보는 상태로 되돌아감. */
	@Test
	void 발송자가_취향을_저장해도_수신자_본인_답변을_덮지_않는다() {
		var saved = patch("/api/v1/friends", "{\"id\":" + FRIEND_WITH_TASTE + ",\"tasteSummary\":\"발송자가 적은 요약\"}");
		assertThat(saved.status()).as(saved.text()).isEqualTo(200);

		var response = post("/api/v1/recommend",
				"{\"relation\":\"친구\",\"friendId\":" + FRIEND_WITH_TASTE + "," + DEFAULT_BUDGET + "}");

		var first = response.body().get("results").get(0);
		assertThat(first.get("product").get("color").asString()).as("본인 답변이 덮였음: " + response.text()).isEqualTo("블랙");
	}

	/**
	 * 프론트가 `productId → /products/{id}.webp`로 이미지를 고르므로 id가 계약에서 사라지면 화면이 빈다. 이모지는 사진이 없던
	 * 시절의 자리표시자라 실물 사진 사이에 섞이면 폴백이 아니라 깨진 화면임(F29).
	 */
	@Test
	void 추천_상품은_id를_주고_emoji를_주지_않는다() {
		var response = post("/api/v1/recommend", "{\"relation\":\"친구\"," + DEFAULT_BUDGET + "}");

		var product = response.body().get("results").get(0).get("product");
		assertThat(product.get("id").asLong()).isPositive();
		assertThat(product.has("emoji")).as(response.text()).isFalse();
	}

	/**
	 * **`imageUrl`은 주소이거나 없어야 함.** 값이 있으면 화면이 그대로 `img` 주소로 쓰므로 이모지가 들어가면
	 * `<img src="👜">`가 되어 깨진 이미지가 되고 폴백(`/products/{id}.webp`)도 안 탐.
	 *
	 * 2026-08-12까지 시드에 실제로 이모지가 들어 있었는데 **이것을 잡는 단언이 어디에도 없었음.** 이 테스트가 그 그물임.
	 */
	@Test
	void 추천_상품의_imageUrl은_없거나_http로_시작한다() {
		var response = post("/api/v1/recommend", "{\"relation\":\"친구\"," + DEFAULT_BUDGET + "}");

		// 결과가 비면 아래 검사가 한 번도 안 돌아 조용히 통과함. 그물이 뚫린 채 초록인 것이 가장 나쁨
		assertThat(response.status()).as(response.text()).isEqualTo(200);
		var results = response.body().get("results");
		assertThat(results.size()).as(response.text()).isEqualTo(3);

		results.forEach((result) -> {
			var imageUrl = result.get("product").get("imageUrl");
			if (imageUrl != null && !imageUrl.isNull()) {
				assertThat(imageUrl.asString()).as(response.text()).startsWith("http");
			}
		});
	}

	@Test
	void 남의_친구_취향은_읽지_못하고_404가_난다() {
		clearCookies();
		loginAs("01099998888", "1234");

		var response = post("/api/v1/recommend",
				"{\"relation\":\"친구\",\"friendId\":" + FRIEND_WITH_TASTE + "," + DEFAULT_BUDGET + "}");

		// 없는 친구와 남의 친구를 같은 코드로 다룸 — 순번을 훑어 남의 친구 존재 여부를 알아내는 것을 막음
		assertThat(response.status()).as(response.text()).isEqualTo(404);
		assertThat(response.code()).isEqualTo("FRIEND404_1");
	}

}
