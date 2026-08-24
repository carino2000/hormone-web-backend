package com.sixletter.hormone_web_backend.dto;

import com.sixletter.hormone_web_backend.entity.PredictionJob;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 예측 요청 이력 한 건. 프론트 "기록" 탭이 쓴다.
 *
 * <p>요청 본문이 일차 정수 하나뿐이라 payload 원문을 그대로 실어도 되지만,
 * 화면이 쓰기 좋게 <b>보낸 일차</b>만 뽑아 준다.
 *
 * <p>(예전 계약에서는 요청이 90일 × 44피처라 수백 KB 였고, 그래서 "몇 일치를 보냈고
 * 피처가 몇 개였는지"를 요약해 줬다. 지금은 보낸 값이 정수 하나다.)
 *
 * @param status          PENDING / SUCCEEDED / FAILED
 * @param latencyMs       파이썬 예측 서버 왕복 시간
 * @param sentDay         파이썬에 실제로 보낸 일차. <b>백엔드 Day 와 다를 수 있다</b>
 *                        (app.model.day-offset 보정 — ModelProperties 참고)
 * @param responsePreview 응답 원문 앞부분. 실패했을 때 뭐가 왔는지 보는 용도
 */
public record PredictionJobDto(
        Long id,
        LocalDate targetDate,
        String status,
        Integer latencyMs,
        String errorMessage,
        Integer sentDay,
        String responsePreview,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {

    /** 응답 원문을 통째로 내려보내지 않는다. 성공 응답도 수 KB 라 목록에서는 낭비다. */
    private static final int PREVIEW_LIMIT = 500;

    public static PredictionJobDto from(PredictionJob job) {
        return new PredictionJobDto(
                job.getId(),
                job.getTargetDate(),
                job.getStatus() == null ? null : job.getStatus().name(),
                job.getLatencyMs(),
                job.getErrorMessage(),
                sentDay(job),
                preview(job.getResponseBody()),
                job.getStartedAt(),
                job.getFinishedAt());
    }

    /** 요청 payload 에서 실제로 보낸 일차를 꺼낸다. {@code {"day": 45}} */
    private static Integer sentDay(PredictionJob job) {
        Object day = job.getRequestPayload() == null ? null : job.getRequestPayload().get("day");
        return day instanceof Number n ? n.intValue() : null;
    }

    private static String preview(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= PREVIEW_LIMIT ? body : body.substring(0, PREVIEW_LIMIT) + "…";
    }
}
