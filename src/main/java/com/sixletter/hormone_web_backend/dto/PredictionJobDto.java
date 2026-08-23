package com.sixletter.hormone_web_backend.dto;

import com.sixletter.hormone_web_backend.entity.PredictionJob;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 예측 요청 이력 한 건. 프론트 "기록" 탭이 쓴다.
 *
 * <p><b>왜 payload 원문을 안 내려주는가.</b> {@code request_payload} 는 FULL_HISTORY 모드에서
 * 90일 × 44피처라 한 건이 수백 KB 다. 목록 응답에 실으면 브라우저가 죽는다.
 * 대신 <b>디버깅에 실제로 필요한 요약</b>만 담는다 — 몇 일치를 보냈고 피처가 몇 개였는지.
 * 개수가 44 가 아니면 그 자체가 버그 신호다 (실제로 NON_NULL 직렬화 때문에 40개가
 * 나간 적이 있다). 원문이 필요하면 DB 의 {@code prediction_job} 을 직접 보면 된다.
 *
 * @param status     PENDING / SUCCEEDED / FAILED
 * @param latencyMs  모델 왕복 시간. Mock 은 한 자릿수, 파이썬은 여기가 실제 지연이다
 * @param historyDays 요청에 실어 보낸 일수 (MODEL_INPUT_MODE 확인용)
 * @param featureCount 마지막 날 피처 개수. <b>44 가 아니면 이상하다</b>
 * @param responsePreview 응답 원문 앞부분. 실패했을 때 뭐가 왔는지 보는 용도
 */
public record PredictionJobDto(
        Long id,
        LocalDate targetDate,
        String status,
        Integer latencyMs,
        String errorMessage,
        Integer historyDays,
        Integer featureCount,
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
                historyDays(job),
                featureCount(job),
                preview(job.getResponseBody()),
                job.getStartedAt(),
                job.getFinishedAt());
    }

    @SuppressWarnings("unchecked")
    private static Integer historyDays(PredictionJob job) {
        Object history = job.getRequestPayload() == null ? null : job.getRequestPayload().get("history");
        return history instanceof java.util.List<?> list ? list.size() : null;
    }

    @SuppressWarnings("unchecked")
    private static Integer featureCount(PredictionJob job) {
        Object history = job.getRequestPayload() == null ? null : job.getRequestPayload().get("history");
        if (!(history instanceof java.util.List<?> list) || list.isEmpty()) {
            return null;
        }
        Object last = list.get(list.size() - 1);
        if (!(last instanceof java.util.Map<?, ?> day)) {
            return null;
        }
        Object features = day.get("features");
        return features instanceof java.util.Map<?, ?> map ? map.size() : null;
    }

    private static String preview(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= PREVIEW_LIMIT ? body : body.substring(0, PREVIEW_LIMIT) + "…";
    }
}
