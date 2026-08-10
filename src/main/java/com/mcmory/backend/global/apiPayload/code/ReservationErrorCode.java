package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 매장 예약 에러코드임(ADR-012). 슬롯 선점 충돌은 조회가 아니라 DB 유니크 제약이 최종 방어선이라 409가 정상 경로임.
 */
public enum ReservationErrorCode implements BaseErrorCode {

	INVALID_SLOT(HttpStatus.BAD_REQUEST, "RESV400_1", "선택할 수 없는 시간입니다. 다른 시간을 골라주세요"),
	INVALID_REFERENCE(HttpStatus.BAD_REQUEST, "RESV400_2", "선택한 제품이나 매장 정보를 확인해주세요"),
	CANCEL_TOO_LATE(HttpStatus.BAD_REQUEST, "RESV400_3", "방문 1시간 이내에는 앱에서 취소할 수 없습니다. 매장으로 연락해주세요"),
	PAST_SLOT(HttpStatus.BAD_REQUEST, "RESV400_4", "이미 지났거나 곧 시작하는 시간입니다. 다른 시간을 골라주세요"),
	NOTE_TOO_LONG(HttpStatus.BAD_REQUEST, "RESV400_5", "요청 사항은 500자까지 쓸 수 있습니다"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "RESV404_1", "예약 정보를 찾을 수 없습니다"),
	SLOT_TAKEN(HttpStatus.CONFLICT, "RESV409_1", "방금 다른 분이 예약했습니다. 다른 시간을 골라주세요");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	ReservationErrorCode(HttpStatus httpStatus, String code, String message) {
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
