package com.mcmory.backend.styling;

import com.mcmory.backend.product.Product;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LLM을 끈 상태의 구현임. 항상 `null`을 줘서 호출부가 규칙 문구를 쓰게 함.
 *
 * `bedrock.enabled=false`면 Bedrock 빈이 아예 안 만들어지고 이것이 남음. **로컬과 테스트의 기본값이 그쪽이라 통합 테스트는 외부
 * 호출이 0건임** — 결과가 결정론적이어야 회귀를 잡을 수 있음.
 */
@Component
@ConditionalOnProperty(name = "bedrock.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledStylingReasonWriter implements StylingReasonWriter {

	@Override
	public String write(Product base, Product suggestion, String matchedTag) {
		return null;
	}

}
