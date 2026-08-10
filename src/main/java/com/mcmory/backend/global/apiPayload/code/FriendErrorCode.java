package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 친구 에러코드임. 이름과 전화번호 검증 문구는 회원가입과 같은 규칙(ADR-008)이지만 도메인이 달라 코드를 나눔.
 */
public enum FriendErrorCode implements BaseErrorCode {

	INVALID_NAME(HttpStatus.BAD_REQUEST, "FRIEND400_1", "이름은 1자에서 20자까지 입력해주세요"),
	INVALID_PHONE(HttpStatus.BAD_REQUEST, "FRIEND400_2", "전화번호 형식을 확인해주세요"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "FRIEND404_1", "친구 정보를 찾을 수 없습니다"),
	DUPLICATE_PHONE(HttpStatus.CONFLICT, "FRIEND409_1", "이미 등록한 친구의 전화번호입니다"),
	PHONE_NOT_EDITABLE(HttpStatus.CONFLICT, "FRIEND409_2", "전화번호가 다른 친구는 새로 등록해주세요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	FriendErrorCode(HttpStatus httpStatus, String code, String message) {
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
