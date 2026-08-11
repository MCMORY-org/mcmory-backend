package com.mcmory.backend.styling;

/**
 * 모델이 준 문구를 화면에 쓸 수 있는 형태로 만드는 것임. **순수 함수라 단위 테스트 대상임** — 클라이언트를 통째로 모킹하면 이 로직이 한 줄도 안
 * 돌아감.
 *
 * 40자를 넘으면 **자르지 않고 버림**. 실측에서 모델이 45에서 71자를 냈는데, 억지로 자르면 문장 중간이 끊기고(따옴표나 이모지 surrogate
 * pair를 쪼갤 수도 있음) 미완성 문장이 화면에 나감. 검증된 규칙 문구가 시연 품질이 나음.
 */
public final class StylingReasonNormalizer {

	/** 화면 한 줄에 들어가는 길이임. code point 기준으로 셈 — 이모지는 char 둘을 차지함. */
	public static final int MAX_LENGTH = 40;

	private StylingReasonNormalizer() {
	}

	/**
	 * 쓸 수 있으면 정리한 문구를, 아니면 `null`을 반환함.
	 */
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		// 모델이 개행과 마크다운 강조와 목록 기호를 섞어 내는 일이 있음. 화면은 한 줄이라 걷어냄
		String cleaned = raw.replaceAll("[\\r\\n]+", " ").replaceAll("[*_`#>]", "").replaceAll("\\s{2,}", " ").trim();

		// 모델이 인용부호로 감싸는 일이 잦음
		if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
			cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
		}

		if (cleaned.isEmpty()) {
			return null;
		}
		if (cleaned.codePointCount(0, cleaned.length()) > MAX_LENGTH) {
			return null;
		}
		return cleaned;
	}

}
