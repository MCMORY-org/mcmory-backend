package com.mcmory.backend.auth;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 스프링 컨텍스트 없이 도는 순수 규칙 검증임. 통합에 올리면 컨테이너 기동 비용만 내고 얻는 것이 없음. */
class SignupPolicyTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 8, 8);

	@Test
	void 비밀번호_7자는_거부() {
		assertThat(SignupPolicy.isValidPassword("abc1234")).isFalse();
	}

	@Test
	void 비밀번호_영문만_8자는_거부() {
		assertThat(SignupPolicy.isValidPassword("abcdefgh")).isFalse();
	}

	@Test
	void 비밀번호_숫자만_8자는_거부() {
		assertThat(SignupPolicy.isValidPassword("12345678")).isFalse();
	}

	@Test
	void 비밀번호_영문과_숫자_8자는_통과() {
		assertThat(SignupPolicy.isValidPassword("abcd1234")).isTrue();
	}

	@Test
	void 비밀번호가_없으면_거부() {
		assertThat(SignupPolicy.isValidPassword(null)).isFalse();
	}

	@Test
	void 만14세_생일_당일은_통과() {
		assertThat(SignupPolicy.isUnderMinAge(TODAY.minusYears(14), TODAY)).isFalse();
	}

	@Test
	void 만14세_생일_전날은_거부() {
		assertThat(SignupPolicy.isUnderMinAge(TODAY.minusYears(14).plusDays(1), TODAY)).isTrue();
	}

	@Test
	void 미래_생년월일은_거부() {
		assertThat(SignupPolicy.isFuture(TODAY.plusDays(1), TODAY)).isTrue();
	}

}
