package com.mcmory.backend.recommend;

import java.util.List;

import com.mcmory.backend.product.Product;

/**
 * 규칙이 만든 후보 안에서 선물 3건을 고르고 1위 근거를 쓰는 것임. 구현 둘 — 비활성(`DisabledGiftPicker`)과 Bedrock임.
 *
 * **실패를 위로 던지지 않음.** 못 고르면 `null`을 주고 호출부가 규칙 상위 3건으로 폴백함 — 시연 중 LLM 장애가 화면을 죽이지 않는 것이
 * 요구임(`StylingReasonWriter`와 같은 계약임).
 *
 * **후보 밖으로 나갈 수 없음.** 상품 선택을 모델에 통째로 맡기면 카탈로그에 없는 상품을 지어내고 그대로 화면에 나감. 후보 목록 대조가 그 차단
 * 지점이고 `GiftPickNormalizer`가 그 일을 함.
 */
public interface GiftPicker {

	/**
	 * 고른 결과임. `productIds`의 **순서가 곧 순위임** — 정렬하면 모델이 고른 1순위가 사라짐.
	 */
	record Picked(List<Long> productIds, String reason) {
	}

	/**
	 * 고르거나 못 고르면 `null`을 반환함.
	 * @param candidates 규칙이 점수 순으로 좁힌 후보임. **이 밖의 상품을 고르면 호출부가 통째로 버림**
	 * @param relation 관계 태그임
	 * @param colors 수신자가 고른 색상임. 없으면 빈 목록임
	 * @param styles 수신자가 고른 스타일임. 없으면 빈 목록임
	 */
	Picked pick(List<Product> candidates, String relation, List<String> colors, List<String> styles);

}
