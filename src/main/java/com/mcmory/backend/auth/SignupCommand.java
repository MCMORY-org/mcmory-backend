package com.mcmory.backend.auth;

import java.time.LocalDate;
import java.util.Set;

import com.mcmory.backend.global.apiPayload.code.ConsentErrorCode;
import com.mcmory.backend.global.apiPayload.code.MemberErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import com.mcmory.backend.common.Phones;

/**
 * 가입 입력과 그 검증임. 검증 순서는 동의 다음에 필드 순서(이름, 전화번호, 비밀번호, 생년월일, 성별)이며 화면의 입력 순서와 같음 — 사용자가
 * 위에서부터 고칠 수 있게 함.
 *
 * 동의를 리스트가 아니라 이름 붙은 boolean으로 받는 이유는 프론트가 문자열을 잘못 보내도 서버가 조용히 통과시키지 않게 하기 위함임.
 *
 * passwordConfirm은 받지 않음. 04 화면의 불일치 표기는 오타 방지용 인라인 에러이고 서버가 받아도 같은 문자열 비교 이상을 못 함.
 */
public record SignupCommand(String name, String phone, String password, LocalDate birthDate, String gender,
		boolean privacyAgreed, boolean smsOptIn) {

	private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "NONE");

	public void validate() {
		// 필수 동의는 개인정보 수집과 이용 1종뿐임. 만 14세는 아래 생년월일 검증과 중복이고, 이용약관은 본문이
		// 없는 상태에서 항목만 늘리는 것이라 뺐음. 문안이 생기면 ConsentType에 값을 더해 되살림
		if (!this.privacyAgreed) {
			throw new CustomException(ConsentErrorCode.PRIVACY_REQUIRED);
		}

		String trimmedName = (this.name == null) ? "" : this.name.trim();
		if (trimmedName.isEmpty() || trimmedName.length() > 20) {
			throw new CustomException(MemberErrorCode.INVALID_NAME);
		}
		if (!Phones.isValid(Phones.digits(this.phone))) {
			throw new CustomException(MemberErrorCode.INVALID_PHONE);
		}
		if (!SignupPolicy.isValidPassword(this.password)) {
			throw new CustomException(MemberErrorCode.INVALID_PASSWORD);
		}

		LocalDate today = LocalDate.now();
		if (this.birthDate == null || SignupPolicy.isFuture(this.birthDate, today)) {
			throw new CustomException(MemberErrorCode.INVALID_BIRTH_DATE);
		}
		if (SignupPolicy.isUnderMinAge(this.birthDate, today)) {
			throw new CustomException(MemberErrorCode.UNDER_MIN_AGE);
		}
		// null 검사를 먼저 하는 이유는 Set.of가 만든 집합이 contains(null)에서 NPE를 던지기 때문임 —
		// 필드를 빼먹은 요청이 400이 아니라 500이 되어 클라이언트가 원인을 못 봄(실측)
		if (this.gender == null || !GENDERS.contains(this.gender)) {
			throw new CustomException(MemberErrorCode.INVALID_GENDER);
		}
	}

}
