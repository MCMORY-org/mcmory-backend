package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/**
 * 추천 에러코드임. 남의 추천과 없는 추천을 같은 404로 다룸 — 존재 여부를 알려주면 남의 ID를 탐색할 수 있음.
 */
public enum RecommendErrorCode implements BaseErrorCode {

	BUDGET_INVERTED(HttpStatus.BAD_REQUEST, "REC400_1", "최소 예산을 최대 예산 이하로 입력해주세요"),
	ALREADY_ATTACHED(HttpStatus.BAD_REQUEST, "REC400_2", "이미 다른 친구에게 저장한 추천이라 바꿀 수 없습니다"),
	INVALID_REFERENCE(HttpStatus.BAD_REQUEST, "REC400_3", "추천 정보를 찾을 수 없습니다"),
	INVALID_CONDITION(HttpStatus.BAD_REQUEST, "REC400_4", "추천 조건을 다시 선택해주세요"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "REC404_1", "추천 정보를 찾을 수 없습니다");

	private final HttpStatus httpStatus;

	private final String code;

	private final String message;

	RecommendErrorCode(HttpStatus httpStatus, String code, String message) {
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
