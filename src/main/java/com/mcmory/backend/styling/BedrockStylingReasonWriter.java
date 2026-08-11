package com.mcmory.backend.styling;

import java.time.Duration;

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
 * Bedrock으로 첫 근거 문구를 쓰는 것임(FR-023).
 *
 * **어떤 실패도 위로 던지지 않음.** 타임아웃, 자격증명 없음, 429, 형식 위반 전부 `null`을 돌려주고 호출부가 규칙 문구를 씀 — 시연 중 모델
 * 장애가 화면을 죽이지 않는 것이 요구임.
 *
 * 함정 셋을 코드로 막아둠.
 *
 * 1. **추론 프로파일 id가 필수임.** `amazon.nova-lite-v1:0`을 그대로 부르면 `ValidationException`이고 서울에서는
 * `apac.` 접두사를 써야 함(2026-08-09 실측) 2. **타임아웃은 `apiCallTimeout`임.** 소켓 타임아웃만 걸면 SDK 기본 재시도
 * 3회가 총 지연을 배로 늘림. 재시도도 껐음 3. **기동 시 워밍업 1회.** 첫 호출은 클라이언트 초기화와 자격증명 조회와 TLS 핸드셰이크가 얹혀 정상
 * 왕복(실측 434에서 597ms)보다 훨씬 느림. 안 하면 **시연의 첫 클릭이 조용히 규칙으로 폴백됨**
 */
@Component
@ConditionalOnProperty(name = "bedrock.enabled", havingValue = "true")
public class BedrockStylingReasonWriter implements StylingReasonWriter {

	private static final Logger log = LoggerFactory.getLogger(BedrockStylingReasonWriter.class);

	private final BedrockRuntimeClient client;

	private final String modelId;

	public BedrockStylingReasonWriter(@Value("${bedrock.region}") String region,
			@Value("${bedrock.model-id}") String modelId, @Value("${bedrock.timeout-millis}") long timeoutMillis) {
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
			log.info("Bedrock 워밍업 완료 (model={})", this.modelId);
		}
		catch (RuntimeException ex) {
			log.warn("Bedrock 워밍업 실패. 스타일링 문구는 규칙으로 폴백함: {}", ex.getClass().getSimpleName());
		}
	}

	@Override
	public String write(Product base, Product suggestion, String matchedTag) {
		try {
			String raw = invoke(prompt(base, suggestion, matchedTag));
			StylingReasonNormalizer.Result result = StylingReasonNormalizer.normalize(raw);
			if (!result.accepted()) {
				// 예외 없이 조용히 폴백하는 유일한 경로임. **사유를 안 남기면 폴백 비율(EDD의 G5)을 분해할 수 없음** —
				// 실제로 nova-lite가 길이 지시를 못 지켜 전부 버려지던 것을 로그가 없어 늦게 알았음
				log.warn("Bedrock 문구를 버리고 규칙으로 폴백함 (사유={}, 길이={})", result.rejection(),
						(raw == null) ? 0 : raw.trim().codePointCount(0, raw.trim().length()));
			}
			return result.text();
		}
		catch (RuntimeException ex) {
			// 타임아웃, 자격증명 없음, 429, 5xx 전부 여기로 옴. 사유만 남기고 본문은 남기지 않음
			log.warn("Bedrock 호출 실패. 규칙 문구로 폴백함: {}", ex.getClass().getSimpleName());
			return null;
		}
	}

	/**
	 * 프롬프트임. **입력으로 준 속성만 말하도록 못박음** — 없는 취향을 지어내면 그 문장이 그대로 화면에 나감. 길이도 지시하되 서버가 다시 검사함
	 * (실측에서 모델이 길이 지시를 12건 중 12건 어겼음).
	 */
	private String prompt(Product base, Product suggestion, String matchedTag) {
		// 텍스트 블록에 formatted()를 쓰면 SpotBugs가 VA_FORMAT_STRING_USES_NEWLINE으로 막으므로 결합으로 만듦
		return """
				쇼핑 앱의 추천 카드에 들어갈 짧은 문구를 쓴다.

				""" + "가진 제품: " + base.getName() + " (" + base.getCategory() + ")\n" + "추천 제품: " + suggestion.getName()
				+ " (" + suggestion.getCategory() + ")\n" + "공통 스타일: " + matchedTag + "\n" + """

						예시 형식(길이를 이만큼 맞춰라). **디자인이 승인한 화면 문구다**:
						평소 미니멀 스타일을 즐기시네요
						모델이 함께 매치한 제품이에요
						캐주얼한 라인이 잘 맞아요

						규칙:
						- 한국어 존댓말 한 문장, 25자 이내
						- **위에 준 정보만 쓴다.** 색상, 소재, 구매 이력, 다른 취향은 주지 않았으므로 언급하지 않는다
						- 정품, 인증, 가품, 진품이라는 말과 구매 권유를 쓰지 않는다
						- 카테고리의 영문 표기를 문장에 넣지 않는다
						- 따옴표와 줄바꿈과 마크다운 없이 문장만 출력한다
						""";
	}

	private String invoke(String prompt) {
		ConverseResponse response = this.client.converse(ConverseRequest.builder()
			.modelId(this.modelId)
			.messages(Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText(prompt)).build())
			// temperature를 낮게 잡음: 실시간 호출이라 사전 검사가 이후 출력을 보장하지 못하는데,
			// 분산이 작을수록 30회 표본의 대표성이 올라감(EDD.md). 문구가 단조로워지는 것은 감수함
			.inferenceConfig(InferenceConfiguration.builder().maxTokens(120).temperature(0.2F).build())
			.build());

		return response.output().message().content().get(0).text();
	}

}
