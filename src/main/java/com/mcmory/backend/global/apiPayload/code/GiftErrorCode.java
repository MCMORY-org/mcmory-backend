package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 선물과 초대 에러코드임(ADR-008, ADR-011).
 */
public enum GiftErrorCode implements BaseErrorCode {

	PRODUCT_NOT_FOUND(HttpStatus.BAD_REQUEST, "GIFT400_1", "상품을 찾을 수 없습니다"),
	INVALID_LETTER_LENGTH(HttpStatus.BAD_REQUEST, "GIFT400_2", "편지는 1자에서 200자까지 쓸 수 있습니다"),
	NOT_OPENED(HttpStatus.BAD_REQUEST, "GIFT400_3", "선물을 먼저 열어주세요"),
	NO_IMAGE(HttpStatus.BAD_REQUEST, "GIFT400_5", "사진을 선택해주세요"),
	TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "GIFT400_6", "사진은 5장까지 넣을 수 있습니다"),
	INVALID_IMAGE(HttpStatus.BAD_REQUEST, "GIFT400_7", "사진은 10MB 이하 이미지 파일만 올릴 수 있습니다"),
	INVALID_LETTER_COLOR(HttpStatus.BAD_REQUEST, "GIFT400_8", "편지지 색상 형식을 확인해주세요"),
	ALREADY_CHANGE_REQUESTED(HttpStatus.CONFLICT, "GIFT409_1", "이미 옵션 변경을 문의했습니다"),
	INVALID_CHANGE_REASON(HttpStatus.BAD_REQUEST, "GIFT400_4", "문의 사유를 선택해주세요"),
	INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "GIFT404_1", "초대 정보를 찾을 수 없습니다");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	GiftErrorCode(HttpStatus httpStatus, String code, String message) {
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
