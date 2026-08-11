package com.mcmory.backend.styling;

import com.mcmory.backend.product.Product;

/**
 * 첫 근거 문구를 쓰는 것임. 구현 둘 — 비활성(`DisabledStylingReasonWriter`)과 Bedrock임.
 *
 * **실패를 위로 던지지 않음.** 못 쓰면 `null`을 주고 호출부가 규칙 문구로 폴백함 — 시연 중 LLM 장애가 화면을 죽이지 않는 것이 요구임.
 */
public interface StylingReasonWriter {

	/**
	 * 문구를 쓰거나 못 쓰면 `null`을 반환함.
	 * @param base 보유 제품임
	 * @param suggestion 제안할 상품임
	 * @param matchedTag 둘이 공유하는 스타일 태그임
	 */
	String write(Product base, Product suggestion, String matchedTag);

}
