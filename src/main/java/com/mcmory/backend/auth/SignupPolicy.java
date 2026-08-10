package com.mcmory.backend.auth;

import java.time.LocalDate;
import java.time.Period;

/**
 * 가입 정책 중 DB가 필요 없는 순수 규칙만 모음. 입력과 출력이 순수한 규칙이라 스프링 없이 단위 테스트가 되도록 분리함.
 */
public final class SignupPolicy {

	/** 만 14세 미만은 가입 불가임. 화면의 만 14세 이상 확인 체크와 생년월일을 이중으로 검증함. */
	public static final int MIN_AGE = 14;

	private static final int MIN_PASSWORD_LENGTH = 8;

	private SignupPolicy() {
	}

	/**
	 * 8자 이상이고 영문과 숫자를 각각 하나 이상 포함함. 특수문자는 요구하지 않음 — 화면에 그 안내가 없고 요구하면서 안내하지 않으면 가입이 막힘.
	 */
	public static boolean isValidPassword(String password) {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			return false;
		}
		// isLetter는 한글도 영문으로 인정해 `가나다라1234`가 통과함 — ADR-008이 말하는 영문은 ASCII 알파벳임
		boolean hasAlphabet = password.chars().anyMatch((ch) -> (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'));
		return hasAlphabet && password.chars().anyMatch(Character::isDigit);
	}

	/** 생일 당일에 만 나이가 채워지므로 당일은 통과임. */
	public static boolean isUnderMinAge(LocalDate birthDate, LocalDate today) {
		return Period.between(birthDate, today).getYears() < MIN_AGE;
	}

	public static boolean isFuture(LocalDate birthDate, LocalDate today) {
		return birthDate.isAfter(today);
	}

}
