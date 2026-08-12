package com.mcmory.backend.recommend;

import java.util.ArrayList;
import java.util.List;

import com.mcmory.backend.support.HttpIntegrationSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코디용 의류가 선물 경로로 새지 않는지 지키는 테스트임(FEAT-W002).
 *
 * FR-023 스타일링을 위해 시드에 의류 8건(id 11에서 18)을 넣었는데, 선물 추천과 발송은 카탈로그를 카테고리로 거르지 않고 있었음. 막지 않으면
 * "친구에게 슬랙스를 선물하세요"가 나감.
 *
 * **기존 추천 테스트의 초록을 안전 신호로 읽으면 안 됨** — 안정 정렬의 id 타이브레이크 때문에 의류가 항상 뒤로 밀려 1순위는 그대로임. 혼입은
 * 2순위와 3순위, 그리고 예산 대역 밖 폴백에서만 드러남.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiftEligibilityIntegrationTest extends HttpIntegrationSupport {

	/**
	 * 시드의 코디용 의류 id 대역임(WOMAN OUTER, WOMAN TOP, WOMAN BOTTOM). 추천 응답에 카테고리가 없어 id로 판정함 —
	 * 카테고리를 응답에 더하는 것은 계약 변경이라 이 테스트를 위해 하지 않음. **시드에 의류를 더 넣으면 이 대역을 넓힐 것.**
	 */
	private static final long CLOTHING_ID_FROM = 11L;

	private static final long CLOTHING_ID_TO = 18L;

	/** 선물로 보내려 시도할 의류 하나임. */
	private static final long CLOTHING_PRODUCT_ID = 11L;

	@BeforeEach
	void 로그인() {
		clearCookies();
		loginAs("01012345678", "1234");
	}

	@Test
	void 기본_예산_추천에는_의류가_섞이지_않는다() {
		assertNoClothing(post("/api/v1/recommend", "{\"relation\":\"친구\",\"minBudget\":50,\"maxBudget\":150}"));
	}

	@Test
	void 넓은_예산_추천에도_의류가_섞이지_않는다() {
		assertNoClothing(post("/api/v1/recommend", "{\"relation\":\"연인\",\"minBudget\":0,\"maxBudget\":500}"));
	}

	/**
	 * 예산에 걸리는 상품이 0건이면 전체 카탈로그 폴백으로 떨어짐. 예산 필터만 막고 이 갈래를 놓치면 여기서 의류가 그대로 나감.
	 */
	@Test
	void 예산에_맞는_상품이_없어_전체_폴백으로_떨어져도_의류가_섞이지_않는다() {
		// 시드 최고가가 183만원(의류)이고 선물 대상 최고가는 115만원임. 300만원 이상은 0건이라 폴백이 발동함
		assertNoClothing(post("/api/v1/recommend", "{\"relation\":\"부모님\",\"minBudget\":300,\"maxBudget\":900}"));
	}

	/**
	 * 추천만 막으면 뚫림 — recommendationId는 선택 필드라 없으면 상품 존재 여부만 보고 통과함. 의류 id를 직접 실으면 발송과 열람과
	 * 보유 등록까지 그대로 감.
	 */
	@Test
	void 의류를_직접_실어_선물하면_400이다() {
		var response = post("/api/v1/gift",
				"{\"productId\":" + CLOTHING_PRODUCT_ID + ",\"letterBody\":\"코트 받아\",\"friendName\":\"친구 2\"}");

		// 없는 상품과 같은 코드임 — 카탈로그의 어느 id가 선물 가능한지 훑게 하지 않음
		assertThat(response.status()).as(response.text()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("GIFT400_1");
	}

	private void assertNoClothing(Response response) {
		assertThat(response.status()).as(response.text()).isEqualTo(200);

		List<Long> ids = new ArrayList<>();
		response.body().get("results").forEach((item) -> ids.add(item.get("product").get("id").asLong()));

		List<Long> clothingIds = new ArrayList<>();
		for (long id = CLOTHING_ID_FROM; id <= CLOTHING_ID_TO; id++) {
			clothingIds.add(id);
		}

		assertThat(ids).as("추천 결과가 비어 있으면 이 단언이 무의미해짐").isNotEmpty();
		assertThat(ids).as("선물 추천에 의류가 섞임: " + response.text()).doesNotContainAnyElementsOf(clothingIds);
	}

}
