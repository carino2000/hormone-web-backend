package com.sixletter.hormone_web_backend.dto;

import java.time.LocalDateTime;

/**
 * 모든 에러 응답의 공통 형태. 프론트는 code 로 분기하고 message 를 보여준다.
 * 스택트레이스는 절대 담지 않는다.
 */
public record ApiError(String code, String message, LocalDateTime timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, LocalDateTime.now());
    }
}
