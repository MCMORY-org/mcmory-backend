package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 보유 제품 에러코드임. **실패 문구에 인증이나 정품 표현을 쓰지 않음**(ADR-004, NFR-004) — 조회 출처가 없어 정품 여부를 말할 근거가
 * 없음.
 */
public enum OwnedProductErrorCode implements BaseErrorCode {

	SERIAL_REQUIRED(HttpStatus.BAD_REQUEST, "OWNED400_1", "일련번호를 입력해주세요"),
	SERIAL_NOT_MATCHED(HttpStatus.NOT_FOUND, "OWNED404_1", "등록된 제품 정보를 찾지 못했습니다. 제품 태그와 보증서의 일련번호를 다시 확인해주세요"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "OWNED404_2", "제품 정보를 찾을 수 없습니다"),
	DUPLICATE(HttpStatus.CONFLICT, "OWNED409_1", "이미 등록된 제품입니다");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	OwnedProductErrorCode(HttpStatus httpStatus, String code, String message) {
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
