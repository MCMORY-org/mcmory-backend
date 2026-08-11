package com.mcmory.backend.styling;

import java.util.List;

/**
 * 모델이 준 문구를 화면에 쓸 수 있는 형태로 만드는 것임. **순수 함수라 단위 테스트 대상임** — 클라이언트를 통째로 모킹하면 이 로직이 한 줄도 안
 * 돌아감.
 *
 * **이것이 런타임 게이트임**(EDD.md의 G1과 G3). 사전 검사가 아니라 매 응답에 걸리므로 시연 중에도 형식과 금지 표현은 보장됨 — 어기면 규칙
 * 문구로 떨어짐. 사실 충실성(G2)만 여기서 못 막고 프롬프트와 사전 검사에 의존함.
 *
 * 40자를 넘으면 **자르지 않고 버림**. 실측에서 nova-lite가 41에서 63자를 냈는데, 억지로 자르면 문장 중간이 끊기고(따옴표나 이모지
 * surrogate pair를 쪼갤 수도 있음) 미완성 문장이 화면에 나감.
 */
public final class StylingReasonNormalizer {

	/**
	 * 화면 한 줄에 들어가는 길이임. code point 기준으로 셈 — 이모지는 char 둘을 차지함.
	 *
	 * **근거**: 디자인 `CONTINUE-02`에서 근거 문구는 상품명 위 한 줄이고, 렌더된 `평소 '미니멀' 스타일을 즐기시네요!`(21자)가 문구
	 * 영역의 약 55퍼센트를 차지함. 한 줄 한계가 약 38자라 여유 2자를 뺐음.
	 *
	 * **이건 디자인 이미지에서 비례로 잰 추정임.** 프론트가 카드를 만들면 실제 폰트로 재측정할 것. 그리고 **레이아웃 보증은 CSS 말줄임이 해야
	 * 함** — 이 상한은 사고 방지 천장이지 레이아웃 보장이 아님.
	 *
	 * 2026-08-11 이전에는 40이었는데 **출처 없이 이전 세션이 고른 값**이었음.
	 */
	public static final int MAX_LENGTH = 36;

	/**
	 * 화면과 발표에 쓰면 안 되는 말임(ADR-004, NFR-004). 우리 앱은 소유도 정품도 판별할 수 없으므로 모델이 이 말을 쓰면 그 문장은
	 * 버림. 판매 권유도 함께 막음 — 우리는 판매자가 아니라 연동 서비스임.
	 */
	private static final List<String> FORBIDDEN_TERMS = List.of("정품", "인증", "가품", "진품", "구매하세요", "구입하세요", "지금 사", "할인");

	/**
	 * **존댓말 검사는 두지 않음.** 2026-08-11에 허용 종결어 목록(`요`·`다`·`죠`·`까`)으로 넣었다가 뺐음 — 목록에 `다`가 있어
	 * `잘 어울린다`(반말)가 통과하고 `잘 어울림`(명사형)만 걸렸음. 즉 이름과 실효가 어긋났고, 그런 검사는 "존댓말이 보장된다"는 잘못된 믿음을
	 * 만듦. 톤은 사전 30개 전수 검토에서 봄.
	 */
	private StylingReasonNormalizer() {
	}

	/** 거부 사유임. 모든 거부가 조용하므로 **이 값이 유일한 관측 지점임**(EDD.md의 G5 분해). */
	public enum Rejection {

		TOO_LONG, FORBIDDEN_TERM, EMPTY, HAS_ENGLISH

	}

	/**
	 * 정규화 결과임. 통과면 `text`가 있고 `rejection`이 null임.
	 */
	public record Result(String text, Rejection rejection) {

		public boolean accepted() {
			return this.text != null;
		}

		static Result of(String text) {
			return new Result(text, null);
		}

		static Result rejected(Rejection rejection) {
			return new Result(null, rejection);
		}

	}

	public static Result normalize(String raw) {
		if (raw == null) {
			return Result.rejected(Rejection.EMPTY);
		}
		// 모델이 개행과 마크다운 강조와 목록 기호를 섞어 내는 일이 있음. 화면은 한 줄이라 걷어냄
		String cleaned = raw.replaceAll("[\\r\\n]+", " ").replaceAll("[*_`#>]", "").replaceAll("\\s{2,}", " ").trim();

		// 모델이 인용부호로 감싸는 일이 잦음. 곡선 따옴표도 씀
		cleaned = stripWrappingQuotes(cleaned);

		if (cleaned.isEmpty()) {
			return Result.rejected(Rejection.EMPTY);
		}
		for (String term : FORBIDDEN_TERMS) {
			if (cleaned.contains(term)) {
				return Result.rejected(Rejection.FORBIDDEN_TERM);
			}
		}
		// 카테고리명이 WOMAN TOP 같은 영문이라 모델이 그대로 문장에 넣는 일이 있음
		if (cleaned.matches(".*[A-Za-z]{2,}.*")) {
			return Result.rejected(Rejection.HAS_ENGLISH);
		}
		if (cleaned.codePointCount(0, cleaned.length()) > MAX_LENGTH) {
			return Result.rejected(Rejection.TOO_LONG);
		}
		return Result.of(cleaned);
	}

	private static String stripWrappingQuotes(String value) {
		String result = value;
		while (result.length() >= 2 && isQuote(result.charAt(0)) && isQuote(result.charAt(result.length() - 1))) {
			result = result.substring(1, result.length() - 1).trim();
		}
		return result;
	}

	private static boolean isQuote(char character) {
		return character == '"' || character == '\'' || character == '“' || character == '”' || character == '‘'
				|| character == '’';
	}

}
