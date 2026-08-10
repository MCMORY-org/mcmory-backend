package com.mcmory.backend.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.mcmory.backend.global.apiPayload.code.BaseErrorCode;

/**
 * MCMORY API 표준 응답 봉투임(API명세서 "응답 envelope" 절). 멋사 14기 형식이라 프론트가 배운 그대로 파싱함 — 성공 여부를 상태
 * 코드와 봉투 양쪽에서 볼 수 있고, 실패는 문구가 아니라 `code`로 분기한다.
 *
 * JSON 필드 순서를 isSuccess, code, message, result로 고정함. `isSuccess`에 `@JsonProperty`를 명시하는
 * 이유는 자바빈 is-접두어 스트리핑 관례 때문임 — 없으면 필드명이 `success`로 나갈 수 있음.
 *
 * @param <T> 결과 데이터 타입
 * @param isSuccess 성공 여부
 * @param code 성공은 HTTP 숫자만("200"), 실패는 도메인 접두사 형식("MEMBER404_1")
 * @param message 성공은 영문 reasonPhrase, 실패는 사용자에게 보일 한국어
 * @param result 결과 데이터. 실패면 null
 */
@JsonPropertyOrder({ "isSuccess", "code", "message", "result" })
public record CustomResponse<T>(@JsonProperty("isSuccess") boolean isSuccess, @JsonProperty("code") String code,
		@JsonProperty("message") String message, @JsonProperty("result") T result) {

	private static final String SUCCESS_MESSAGE_OK = "OK";

	public static <T> CustomResponse<T> ok(T result) {
		return new CustomResponse<>(true, "200", SUCCESS_MESSAGE_OK, result);
	}

	public static CustomResponse<Void> fail(BaseErrorCode errorCode) {
		return new CustomResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
	}

}
