package com.mcmory.backend.global.apiPayload.exception;

import java.time.format.DateTimeParseException;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import com.mcmory.backend.global.apiPayload.code.BaseErrorCode;
import com.mcmory.backend.global.apiPayload.code.CommonErrorCode;
import com.mcmory.backend.global.apiPayload.code.GiftErrorCode;
import com.mcmory.backend.global.apiPayload.code.ValidationErrorCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 실패를 CustomResponse 봉투로 바꿈. **여기서 잡지 않는 예외는 스프링 기본 오류 페이지로 나가 봉투 계약이 깨진다** — 실측으로 두 번
 * 새어나갔다(요청 본문 파싱 실패가 400 기본형, 필드 누락 NPE가 500 기본형). 그래서 계층별로 하나씩 막고 마지막에 안전망을 둔다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<CustomResponse<Void>> handleCustom(CustomException ex) {
		return respond(ex.getErrorCode());
	}

	/**
	 * 요청 본문을 읽지 못함. **Jackson 3(`tools.jackson`)가 던진다** — 클래스패스에 Jackson 2도 transitive로
	 * 남아 있어 그쪽 타입을 검사하면 컴파일은 되고 분기는 영영 안 탄다.
	 *
	 * 원인 메시지를 응답에 담지 않는 이유는 내부 클래스명과 필드 구조가 노출되기 때문임. 로그에는 남긴다.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<CustomResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
		log.warn("요청 본문을 읽지 못함", ex);
		return respond(ValidationErrorCode.UNREADABLE_BODY);
	}

	/** 경로나 쿼리 값이 선언 타입으로 변환되지 않음(예 `/api/v1/recommend/abc`). 500이 아니라 400으로 나가야 함. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<CustomResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return respond(ValidationErrorCode.INVALID_PARAMETER);
	}

	/**
	 * 날짜 문자열이 형식에 맞지 않음(예 `date=2026-13-99`). 서비스 계층이 `LocalDate.parse`를 그대로 쓰는 곳이 있어 여기서
	 * 잡지 않으면 클라이언트 입력 오류가 COMMON500으로 나감.
	 */
	@ExceptionHandler(DateTimeParseException.class)
	public ResponseEntity<CustomResponse<Void>> handleDateParse(DateTimeParseException ex) {
		return respond(ValidationErrorCode.INVALID_PARAMETER);
	}

	/**
	 * 업로드가 서블릿 상한을 넘음. 서비스의 크기 검사보다 **먼저** 걸리므로 여기서 잡지 않으면 계약상 400인 것이 COMMON500으로 나감. 계약
	 * 코드는 API명세서 28번의 `GIFT400_7`임.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<CustomResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
		return respond(GiftErrorCode.INVALID_IMAGE);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<CustomResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
		return respond(ValidationErrorCode.MISSING_PARAMETER);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<CustomResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		return respond(CommonErrorCode.METHOD_NOT_ALLOWED);
	}

	/**
	 * 매핑되지 않은 경로임. 안전망보다 먼저 잡아 500이 아니라 404로 보냄 — 주소창으로 아무 경로나 열었을 때 "서버 내부 오류"가 보이면 안 됨.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<CustomResponse<Void>> handleNoResource(NoResourceFoundException ex) {
		return respond(CommonErrorCode.RESOURCE_NOT_FOUND);
	}

	/**
	 * 최종 안전망임. 어떤 예외든 봉투로 감싸 프론트가 항상 같은 형태를 읽게 함.
	 *
	 * 예외 메시지를 응답에 담지 않는다 — 내부 구조와 SQL이 샌다. 대신 로그에 스택까지 남기므로 **500이 관측되면 서버 로그부터 볼 것.**
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<CustomResponse<Void>> handleUnexpected(Exception ex) {
		log.error("처리되지 않은 예외를 COMMON500으로 감쌈", ex);
		return respond(CommonErrorCode.INTERNAL_SERVER_ERROR);
	}

	private ResponseEntity<CustomResponse<Void>> respond(BaseErrorCode errorCode) {
		return ResponseEntity.status(errorCode.getHttpStatus()).body(CustomResponse.fail(errorCode));
	}

}
