package com.mcmory.backend.global.apiPayload.exception;

import com.mcmory.backend.global.apiPayload.code.BaseErrorCode;

/**
 * 도메인 실패를 에러코드로 감싸 전달하는 런타임 예외임. 예외 핸들러가 이 타입을 CustomResponse 실패 응답으로 바꿈.
 *
 * 문구를 직접 들고 다니지 않는 이유는 같은 실패가 곳마다 다른 문장으로 나가는 것을 막기 위함임 — 문구는 에러코드 enum 한 곳에만 있다.
 */
public class CustomException extends RuntimeException {

	private final transient BaseErrorCode errorCode;

	public CustomException(BaseErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public BaseErrorCode getErrorCode() {
		return this.errorCode;
	}

}
