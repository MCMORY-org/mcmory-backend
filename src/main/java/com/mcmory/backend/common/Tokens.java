package com.mcmory.backend.common;

import java.security.SecureRandom;

/**
 * 링크에 실리는 토큰 발급임. 초대(`gift.invite_token`)와 설문(`friend.survey_token`)이 함께 씀.
 *
 * 24자 36진(약 124비트)이라 열거가 불가능함. **길이를 바꾸면 두 컬럼(VARCHAR(24))도 함께 바꿔야 함.**
 */
public final class Tokens {

	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

	private static final int LENGTH = 24;

	private static final SecureRandom RANDOM = new SecureRandom();

	private Tokens() {
	}

	public static String issue() {
		StringBuilder token = new StringBuilder(LENGTH);
		for (int index = 0; index < LENGTH; index++) {
			token.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return token.toString();
	}

}
