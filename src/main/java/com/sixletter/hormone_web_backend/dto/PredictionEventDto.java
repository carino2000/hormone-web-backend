package com.sixletter.hormone_web_backend.dto;

import java.time.LocalDate;

/**
 * WebSocket {@code /topic/prediction/{userId}} 로 나가는 메시지.
 *
 * <p><b>성공/실패/수집중이 전부 같은 봉투를 쓴다.</b> 예전에는 성공이 String,
 * 실패가 Map 이라 프론트가 타입 분기를 할 수 없었다. 프론트는 {@code type} 하나만 보면 된다.
 *
 * @param type    COLLECTING | PREDICTION_PENDING | PREDICTION_READY | PREDICTION_FAILED
 * @param payload PREDICTION_READY 일 때만 채워진다
 * @param error   PREDICTION_FAILED 일 때만 채워진다
 */
public record PredictionEventDto(
        String type,
        Long userId,
        Integer day,
        LocalDate date,
        PredictionDto payload,
        ApiError error
) {

    public static final String COLLECTING = "COLLECTING";
    public static final String PENDING = "PREDICTION_PENDING";
    public static final String READY = "PREDICTION_READY";
    public static final String FAILED = "PREDICTION_FAILED";

    public static PredictionEventDto collecting(Long userId, int day, LocalDate date) {
        return new PredictionEventDto(COLLECTING, userId, day, date, null, null);
    }

    public static PredictionEventDto pending(Long userId, int day, LocalDate date) {
        return new PredictionEventDto(PENDING, userId, day, date, null, null);
    }

    public static PredictionEventDto ready(Long userId, int day, LocalDate date, PredictionDto payload) {
        return new PredictionEventDto(READY, userId, day, date, payload, null);
    }

    public static PredictionEventDto failed(Long userId, int day, LocalDate date, String code, String message) {
        return new PredictionEventDto(FAILED, userId, day, date, null, ApiError.of(code, message));
    }
}
