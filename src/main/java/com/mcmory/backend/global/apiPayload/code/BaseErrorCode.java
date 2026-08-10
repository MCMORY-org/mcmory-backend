package com.mcmory.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

/** 도메인 에러코드가 구현하는 계약임. CustomException과 예외 핸들러가 이 인터페이스로만 에러코드를 다룸. */
public interface BaseErrorCode {

	HttpStatus getHttpStatus();

	String getCode();

	String getMessage();

}
