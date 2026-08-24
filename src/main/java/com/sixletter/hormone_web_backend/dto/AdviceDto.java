package com.sixletter.hormone_web_backend.dto;

import com.sixletter.hormone_web_backend.entity.DailyAdvice;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * "오늘의 조언" 한 건.
 *
 * <p>토큰 사용량을 같이 내려준다. 시연에서 "이게 얼마나 드는 기능인가"를 물으면
 * 화면에서 바로 답할 수 있어야 하고, 개발 중에도 비용이 눈에 보여야 한다.
 */
public record AdviceDto(
        Long id,
        LocalDate targetDate,
        Integer dayInStudy,
        String status,
        String content,
        String errorMessage,
        Integer sentDays,
        Integer sentFeatures,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        boolean truncated,
        LocalDateTime createdAt
) {
    public static AdviceDto from(DailyAdvice a) {
        if (a == null) {
            return null;
        }
        return new AdviceDto(
                a.getId(), a.getTargetDate(), a.getDayInStudy(),
                a.getStatus() == null ? null : a.getStatus().name(),
                a.getContent(), a.getErrorMessage(),
                a.getSentDays(), a.getSentFeatures(),
                a.getModel(), a.getInputTokens(), a.getOutputTokens(), a.getLatencyMs(),
                a.isTruncated(),
                a.getCreatedAt());
    }
}
