package com.sixletter.hormone_web_backend.exception;

import com.sixletter.hormone_web_backend.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * 모든 에러 응답을 {@link ApiError} 한 가지 형태로 통일한다.
 * 프론트가 code 로 분기할 수 있어야 하고, 스택트레이스는 절대 응답에 넣지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException e) {
        log.debug("리소스 없음: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", e.getMessage()));
    }

    /** 같은 (user, 날짜) 를 두 번 저장하려 할 때. 시연 중 버튼 연타로 흔히 발생한다. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 위반 (중복 저장 시도로 추정): {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CONFLICT", "이미 존재하는 데이터입니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", detail));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", e.getMessage()));
    }

    /** 파이썬 예측 서버 호출 실패. 우리 잘못이 아니라 업스트림 문제라 502. */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> handleModelUnavailable(RestClientException e) {
        log.error("예측 서버 호출 실패", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of("MODEL_UNAVAILABLE", "예측 서버에 연결할 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
