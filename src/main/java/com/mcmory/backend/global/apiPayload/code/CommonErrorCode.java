package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러코드임. 도메인에 속하지 않는 실패만 둠 — 최종 안전망(COMMON500), 매핑되지 않은 경로(COMMON404), 허용되지 않은
 * 메서드(COMMON405), 요청 출처 거부(COMMON403).
 */
public enum CommonErrorCode implements BaseErrorCode {

	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류가 발생했습니다"),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청하신 경로를 찾을 수 없습니다"),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON405", "지금은 처리할 수 없는 요청입니다. 화면을 새로고침한 뒤 다시 시도해주세요"),
	FORBIDDEN_ORIGIN(HttpStatus.FORBIDDEN, "COMMON403", "현재 접속한 환경에서는 요청을 처리할 수 없습니다. 공식 앱이나 웹에서 다시 시도해주세요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	CommonErrorCode(HttpStatus httpStatus, String code, String message) {
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
