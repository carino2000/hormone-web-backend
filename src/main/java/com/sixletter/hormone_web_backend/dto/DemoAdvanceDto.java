package com.sixletter.hormone_web_backend.dto;

import java.time.LocalDate;

/**
 * "하루 넘기기" 응답 (202 Accepted).
 *
 * <p><b>예측 결과는 여기 없다.</b> 예측은 비동기라 아직 안 나왔다.
 * 결과는 WebSocket {@code /topic/prediction/{userId}} 로 온다.
 * 웹소켓이 막혔을 때의 폴백은 {@code GET /api/predictions/users/{id}/latest}.
 *
 * @param jobId 콜드스타트 구간이면 null (예측을 아예 안 돌린다)
 */
public record DemoAdvanceDto(
        Long userId,
        int day,
        LocalDate date,
        String status,
        int collectedDays,
        int requiredDays,
        Long jobId,
        String message
) {
}
