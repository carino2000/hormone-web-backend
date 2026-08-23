package com.sixletter.hormone_web_backend.exception;

/** 조회 대상이 없을 때. GlobalExceptionHandler 가 404 로 바꾼다. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
