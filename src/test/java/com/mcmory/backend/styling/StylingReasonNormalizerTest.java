package com.mcmory.backend.styling;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델 출력 정규화임. **클라이언트를 모킹하는 통합 테스트로는 이 로직이 한 줄도 안 돌아가서** 여기서 따로 봄.
 *
 * 실측(2026-08-09)에서 모델이 40자 제한을 12건 중 12건 어겼음. 그래서 초과분을 자르지 않고 버리는 것이 이 클래스의 요점임.
 */
class StylingReasonNormalizerTest {

	@Test
	void 정상_문구는_그대로_통과한다() {
		assertThat(StylingReasonNormalizer.normalize("평소 미니멀 스타일을 즐기시네요")).isEqualTo("평소 미니멀 스타일을 즐기시네요");
	}

	@Test
	void 개행과_마크다운과_중복_공백을_걷어낸다() {
		assertThat(StylingReasonNormalizer.normalize("**미니멀**\n  스타일이  잘 맞아요")).isEqualTo("미니멀 스타일이 잘 맞아요");
	}

	@Test
	void 감싼_인용부호를_벗긴다() {
		assertThat(StylingReasonNormalizer.normalize("\"블랙 계열과 잘 어울려요\"")).isEqualTo("블랙 계열과 잘 어울려요");
	}

	/** 자르지 않고 버림 — 억지로 자르면 문장 중간이 끊겨 미완성 문장이 화면에 나감. */
	@Test
	void 마흔자를_넘으면_버린다() {
		String tooLong = "이 제품은 고객님이 평소에 즐겨 착용하시는 미니멀한 스타일과 아주 잘 어울리는 아이템이라고 생각합니다";
		assertThat(tooLong.length()).isGreaterThan(StylingReasonNormalizer.MAX_LENGTH);

		assertThat(StylingReasonNormalizer.normalize(tooLong)).isNull();
	}

	@Test
	void 경계인_마흔자는_통과한다() {
		String exact = "가".repeat(StylingReasonNormalizer.MAX_LENGTH);

		assertThat(StylingReasonNormalizer.normalize(exact)).isEqualTo(exact);
	}

	/** 이모지는 char 둘을 차지하므로 code point로 세지 않으면 통과할 문구가 잘못 버려짐. */
	@Test
	void 이모지는_한_글자로_센다() {
		String withEmoji = "가".repeat(StylingReasonNormalizer.MAX_LENGTH - 1) + "👍";
		assertThat(withEmoji.length()).isGreaterThan(StylingReasonNormalizer.MAX_LENGTH);

		assertThat(StylingReasonNormalizer.normalize(withEmoji)).isEqualTo(withEmoji);
	}

	@Test
	void 빈_값과_공백과_null은_버린다() {
		assertThat(StylingReasonNormalizer.normalize(null)).isNull();
		assertThat(StylingReasonNormalizer.normalize("")).isNull();
		assertThat(StylingReasonNormalizer.normalize("   \n  ")).isNull();
	}

}
