package com.mcmory.backend.styling;

import com.mcmory.backend.styling.StylingReasonNormalizer.Rejection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델 출력 정규화임. **클라이언트를 모킹하는 통합 테스트로는 이 로직이 한 줄도 안 돌아가서** 여기서 따로 봄.
 *
 * 이것이 EDD의 런타임 게이트(G1 형식과 G3 금지 표현)임 — 사전 검사가 아니라 매 응답에 걸리므로 **시연 중에도 보장됨.** 아래 적대적 픽스처는
 * 실측 출력을 최소 수정해 만든 것임(실제 호출에서 나쁜 사례가 다 나오지는 않기 때문).
 */
class StylingReasonNormalizerTest {

	@Test
	void 정상_문구는_그대로_통과한다() {
		var result = StylingReasonNormalizer.normalize("평소 미니멀 스타일을 즐기시네요");

		assertThat(result.accepted()).isTrue();
		assertThat(result.text()).isEqualTo("평소 미니멀 스타일을 즐기시네요");
	}

	@Test
	void 개행과_마크다운과_중복_공백을_걷어낸다() {
		var result = StylingReasonNormalizer.normalize("**미니멀**\n  스타일이  잘 맞아요");

		assertThat(result.text()).isEqualTo("미니멀 스타일이 잘 맞아요");
	}

	@Test
	void 곧은_따옴표와_곡선_따옴표를_모두_벗긴다() {
		assertThat(StylingReasonNormalizer.normalize("\"함께 매치하기 좋아요\"").text()).isEqualTo("함께 매치하기 좋아요");
		assertThat(StylingReasonNormalizer.normalize("“함께 매치하기 좋아요”").text()).isEqualTo("함께 매치하기 좋아요");
	}

	/** 자르지 않고 버림 — 억지로 자르면 문장 중간이 끊겨 미완성 문장이 화면에 나감. */
	@Test
	void 상한을_넘으면_버린다() {
		String tooLong = "이 제품은 고객님이 평소에 즐겨 착용하시는 미니멀한 스타일과 아주 잘 어울리는 아이템이라고 생각합니다";
		assertThat(tooLong.length()).isGreaterThan(StylingReasonNormalizer.MAX_LENGTH);

		var result = StylingReasonNormalizer.normalize(tooLong);

		assertThat(result.accepted()).isFalse();
		assertThat(result.rejection()).isEqualTo(Rejection.TOO_LONG);
	}

	@Test
	void 경계_길이는_통과한다() {
		String exact = "가".repeat(StylingReasonNormalizer.MAX_LENGTH - 1) + "요";

		assertThat(StylingReasonNormalizer.normalize(exact).text()).isEqualTo(exact);
	}

	/** 이모지는 char 둘을 차지하므로 code point로 세지 않으면 통과할 문구가 잘못 버려짐. */
	@Test
	void 이모지는_한_글자로_센다() {
		String withEmoji = "가".repeat(StylingReasonNormalizer.MAX_LENGTH - 2) + "👍요";
		assertThat(withEmoji.length()).isGreaterThan(StylingReasonNormalizer.MAX_LENGTH);

		assertThat(StylingReasonNormalizer.normalize(withEmoji).accepted()).isTrue();
	}

	/** ADR-004와 NFR-004: 우리 앱은 소유도 정품도 판별할 수 없으므로 이 말이 화면에 나가면 허위임. */
	@Test
	void 금지_표현이_있으면_버린다() {
		for (String bad : new String[] { "정품 보증된 제품과 잘 맞아요", "인증된 소재라 잘 어울려요", "지금 사면 좋아요", "할인 중이라 좋아요" }) {
			var result = StylingReasonNormalizer.normalize(bad);

			assertThat(result.accepted()).as(bad).isFalse();
			assertThat(result.rejection()).as(bad).isEqualTo(Rejection.FORBIDDEN_TERM);
		}
	}

	/** 카테고리명이 WOMAN TOP 같은 영문이라 모델이 그대로 문장에 넣는 일이 있음. */
	@Test
	void 영어가_섞이면_버린다() {
		var result = StylingReasonNormalizer.normalize("WOMAN TOP과 잘 어울려요");

		assertThat(result.rejection()).isEqualTo(Rejection.HAS_ENGLISH);
	}

	/**
	 * 존댓말 검사는 두지 않음 — 허용 종결어 목록으로 넣었다가 뺐음. `다`를 허용하면 반말 평서형이 통과하고 명사형만 걸려 이름과 실효가 어긋났음.
	 * 톤은 사전 전수 검토에서 봄.
	 */
	@Test
	void 존댓말이_아니어도_형식만_맞으면_통과한다() {
		assertThat(StylingReasonNormalizer.normalize("미니멀 스타일과 잘 어울림").accepted()).isTrue();
	}

	@Test
	void 빈_값과_공백과_null은_버린다() {
		for (String bad : new String[] { null, "", "   \n  " }) {
			var result = StylingReasonNormalizer.normalize(bad);

			assertThat(result.accepted()).isFalse();
			assertThat(result.rejection()).isEqualTo(Rejection.EMPTY);
		}
	}

}
