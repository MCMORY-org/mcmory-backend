package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 인증 에러코드임. 자격증명 불일치와 세션 부재를 구분함 — 프론트가 전자는 인라인 에러로, 후자는 로그인 화면 이동으로 다루기 때문임.
 */
public enum AuthErrorCode implements BaseErrorCode {

	LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH401_1", "로그인이 필요합니다"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH401_2", "전화번호 또는 비밀번호가 올바르지 않습니다"),
	REFRESH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_3", "다시 로그인해주세요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	AuthErrorCode(HttpStatus httpStatus, String code, String message) {
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
