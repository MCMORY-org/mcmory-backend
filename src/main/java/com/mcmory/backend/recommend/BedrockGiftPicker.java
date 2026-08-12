package com.mcmory.backend.recommend;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.mcmory.backend.product.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

/**
 * Bedrock으로 후보 안에서 선물 3건을 고르고 1위 근거를 쓰는 것임(FEAT-W004).
 *
 * **어떤 실패도 위로 던지지 않음.** 타임아웃, 자격증명 없음, 429, 형식 위반, 후보 밖 id 전부 `null`을 돌려주고 호출부가 규칙 결과를 씀.
 *
 * 함정은 `BedrockStylingReasonWriter`와 같음 — 추론 프로파일 id가 필수이고, 타임아웃은 재시도를 포함한 총량이어야 하며, 기동
 * 워밍업을 안 하면 **시연의 첫 클릭이 조용히 규칙으로 폴백됨**.
 */
@Component
@ConditionalOnProperty(name = "bedrock.enabled", havingValue = "true")
public class BedrockGiftPicker implements GiftPicker {

	private static final Logger log = LoggerFactory.getLogger(BedrockGiftPicker.class);

	private static final int RESULT_SIZE = 3;

	private final BedrockRuntimeClient client;

	private final String modelId;

	public BedrockGiftPicker(@Value("${bedrock.region}") String region, @Value("${bedrock.model-id}") String modelId,
			@Value("${bedrock.timeout-millis}") long timeoutMillis) {
		this.modelId = modelId;
		this.client = BedrockRuntimeClient.builder()
			.region(Region.of(region))
			.overrideConfiguration(ClientOverrideConfiguration.builder()
				// 재시도 포함 총량임. 넘으면 폴백이라 사용자 체감 상한도 이 값임
				.apiCallTimeout(Duration.ofMillis(timeoutMillis))
				.retryPolicy(RetryPolicy.none())
				.build())
			.build();

		warmUp();
	}

	/** 첫 호출의 초기화 비용을 기동 시점으로 옮김. 실패해도 무시함 — 여기서 죽으면 서버가 안 뜸. */
	private void warmUp() {
		try {
			invoke("안녕");
			log.info("Bedrock 선정기 워밍업 완료 (model={})", this.modelId);
		}
		catch (RuntimeException ex) {
			log.warn("Bedrock 선정기 워밍업 실패. 추천은 규칙으로 폴백함: {}", ex.getClass().getSimpleName());
		}
	}

	@Override
	public Picked pick(List<Product> candidates, String relation, List<String> colors, List<String> styles) {
		int expectedSize = Math.min(RESULT_SIZE, candidates.size());
		Set<Long> candidateIds = candidates.stream().map(Product::getId).collect(Collectors.toSet());

		try {
			String raw = invoke(prompt(candidates, relation, colors, styles, expectedSize));
			GiftPickNormalizer.Result result = GiftPickNormalizer.normalize(raw, candidateIds, expectedSize);
			if (!result.accepted()) {
				// 조용히 폴백하는 유일한 경로임. **사유를 안 남기면 폴백 비율을 분해할 수 없음** —
				// 후보 밖 id(UNKNOWN_ID)와 형식 위반(BAD_JSON)은 프롬프트를 고칠 지점이 서로 다름
				log.warn("Bedrock 선정을 버리고 규칙으로 폴백함 (사유={})", result.rejection());
				return null;
			}
			return new Picked(result.productIds(), result.reason());
		}
		catch (RuntimeException ex) {
			log.warn("Bedrock 선정 호출 실패. 규칙 결과로 폴백함: {}", ex.getClass().getSimpleName());
			return null;
		}
	}

	/**
	 * 프롬프트임. **후보 목록 밖으로 못 나가게 못박음** — 없는 상품을 지어내면 서버가 통째로 버려 폴백률만 오름.
	 *
	 * 근거 문구 규칙은 스타일링과 같은 것을 씀(길이·금칙어·영문 금지). 서버가 다시 검사하므로 지시는 폴백률을 낮추는 수단이지 보장이 아님.
	 */
	private String prompt(List<Product> candidates, String relation, List<String> colors, List<String> styles,
			int expectedSize) {
		StringBuilder rows = new StringBuilder();
		for (Product product : candidates) {
			rows.append(product.getId())
				.append(" | ")
				.append(product.getName())
				.append(" | ")
				.append(product.getCategory())
				.append(" | ")
				.append((product.getColor() == null) ? "-" : product.getColor())
				.append(" | ")
				.append((product.getPrice() == null) ? 0 : product.getPrice())
				.append("원\n");
		}

		return "선물을 고른다. 아래 후보 중에서 정확히 " + expectedSize + "개를 고른다.\n\n" + "받는 사람과의 관계: " + relation + "\n" + "좋아하는 색: "
				+ (colors.isEmpty() ? "모름" : String.join(", ", colors)) + "\n" + "좋아하는 스타일: "
				+ (styles.isEmpty() ? "모름" : String.join(", ", styles)) + "\n\n" + "후보:\n" + rows + "\n" + """
						이유 예시(길이와 말투를 이만큼 맞춰라). **디자인이 승인한 화면 문구다**:
						블랙 계열을 좋아하신다고 하셔서 골랐어요
						친구에게 어울리는 캐주얼 라인이에요
						평소 미니멀 스타일을 즐기시네요

						규칙:
						- 후보에 있는 id만 쓴다. 목록에 없는 상품을 지어내지 않는다
						- 정확히 위에서 말한 개수만큼, 중복 없이, 가장 잘 맞는 것을 먼저 쓴다
						- 첫 번째로 고른 상품의 추천 이유를 한 문장으로 쓴다
						- 이유는 한국어 존댓말 25자 이내이고, 위에 준 정보만 쓴다
						- **광고 문구가 아니라 고른 이유를 쓴다.** 가격이 적당하다거나 다양하다는 말은 이유가 아니다
						- 정품, 인증, 가품, 진품이라는 말과 구매 권유를 쓰지 않는다
						- 상품명을 포함해 영문 표기를 이유에 넣지 않는다
						- 설명 없이 JSON만 출력한다: {"picks":[1,2,3],"reason":"..."}
						""";
	}

	private String invoke(String prompt) {
		ConverseResponse response = this.client.converse(ConverseRequest.builder()
			.modelId(this.modelId)
			.messages(Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText(prompt)).build())
			// 시연 중 같은 조건이 같은 결과를 내는 편이 안전함. 분산이 크면 순위가 호출마다 흔들림
			.inferenceConfig(InferenceConfiguration.builder().maxTokens(200).temperature(0.2F).build())
			.build());

		return response.output().message().content().get(0).text();
	}

}
