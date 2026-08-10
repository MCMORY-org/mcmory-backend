package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 회원 에러코드임. 가입 입력 검증(ADR-008)과 전화번호 중복임.
 */
public enum MemberErrorCode implements BaseErrorCode {

	INVALID_NAME(HttpStatus.BAD_REQUEST, "MEMBER400_1", "이름은 1자에서 20자까지 입력해주세요"),
	INVALID_PHONE(HttpStatus.BAD_REQUEST, "MEMBER400_2", "전화번호 형식을 확인해주세요"),
	INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "MEMBER400_3", "비밀번호는 영문과 숫자를 포함해 8자 이상 입력해주세요"),
	INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "MEMBER400_4", "생년월일을 확인해주세요"),
	UNDER_MIN_AGE(HttpStatus.BAD_REQUEST, "MEMBER400_5", "만 14세 이상만 가입할 수 있습니다"),
	INVALID_GENDER(HttpStatus.BAD_REQUEST, "MEMBER400_6", "성별을 선택해주세요"),
	DUPLICATE_PHONE(HttpStatus.CONFLICT, "MEMBER409_1", "이미 가입된 전화번호입니다");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	MemberErrorCode(HttpStatus httpStatus, String code, String message) {
		this.httpStatus = httpStatus;
		this.code = code;
		this.message = message;
	}

	@Override
	public HttpStatus getHttpStatus() {
		return this.httpStatus;
	}

	@Override
	public String getCode() {
		return this.code;
	}

	@Override
	public String getMessage() {
		return this.message;
	}

}
