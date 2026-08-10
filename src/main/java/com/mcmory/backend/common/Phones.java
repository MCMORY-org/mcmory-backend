package com.mcmory.backend.common;

/**
 * ADR-008: 전화번호는 숫자만 저장함. 입력의 하이픈과 공백을 걷어내는 지점을 한 곳으로 모음 — 로그인과 친구 등록이 같은 규칙을 써야 함.
 */
public final class Phones {

	private Phones() {
	}

	/**
	 * 하이픈과 공백만 걷어냄. 모든 비숫자를 지우면 `abc01012345678xyz` 같은 값이 정상 번호로 통과함 — 남은 문자는 isValid가
	 * 걸러야 하므로 여기서 지우지 않음.
	 */
	public static String digits(String raw) {
		return raw == null ? "" : raw.replaceAll("[-\\s]", "");
	}

	/** 010으로 시작하는 10자리에서 11자리. 디자인 v1.0의 입력 필드 기준임. */
	public static boolean isValid(String digits) {
		return digits.matches("^010\\d{7,8}$");
	}

}
