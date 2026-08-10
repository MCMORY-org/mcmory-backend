package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 동의 에러코드임(ADR-002). 필수 동의 철회를 막는 이유는 탈퇴가 MVP 밖이라 받아두면 동의 없이 쓰는 회원이 생기기 때문임.
 */
public enum ConsentErrorCode implements BaseErrorCode {

	PRIVACY_REQUIRED(HttpStatus.BAD_REQUEST, "CONSENT400_1", "개인정보 수집과 이용에 동의해주세요"),
	UNKNOWN_TYPE(HttpStatus.BAD_REQUEST, "CONSENT400_2", "동의 항목을 확인한 뒤 다시 시도해주세요"),
	REQUIRED_NOT_WITHDRAWABLE(HttpStatus.BAD_REQUEST, "CONSENT400_3", "필수 동의는 철회할 수 없습니다");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	ConsentErrorCode(HttpStatus httpStatus, String code, String message) {
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
