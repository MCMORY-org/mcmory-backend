package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 요청 형식 검증 에러코드임. 도메인 규칙 위반이 아니라 **요청을 읽지 못한 경우**만 둠 — 값의 의미가 틀린 것은 각 도메인 코드가 담당함.
 */
public enum ValidationErrorCode implements BaseErrorCode {

	INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "VALID400_0", "요청 값의 형식을 확인해주세요"),
	UNREADABLE_BODY(HttpStatus.BAD_REQUEST, "VALID400_1", "요청 형식을 확인해주세요"),
	MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "VALID400_2", "필수 입력 항목을 모두 작성해주세요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	ValidationErrorCode(HttpStatus httpStatus, String code, String message) {
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
