package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 예측 요청 이력. 성공/실패 모두 남긴다.
 *
 * <p>존재 이유는 하나다 — <b>시연 중 문제가 생겼을 때 원인을 찾기 위해서.</b>
 * 어떤 payload 를 보냈고, 몇 ms 걸렸고, 뭐라고 실패했는지가 여기 남는다.
 *
 * <p>{@code user} 를 연관관계로 두지 않고 {@code userId} 로 둔 이유: 이 테이블은
 * 순수 로그라 조인이 필요 없고, 실패 기록을 남기는 경로에서 User 를 로드하다
 * 또 실패하는 상황을 피하고 싶어서다. (DB 에도 FK 제약을 걸지 않았다)
 */
@Entity
@Table(name = "prediction_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private JobStatus status;

    /** 파이썬에 보낸 요청 본문. 재현/디버깅용. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload")
    private Map<String, Object> requestPayload;

    /**
     * 응답 원문 문자열. JSON 이 아닐 수도 있으므로(에러 HTML, 빈 응답 등)
     * JSON 타입이 아니라 MEDIUMTEXT 다. 파싱 실패한 응답도 그대로 남길 수 있어야 한다.
     */
    @Column(name = "response_body", columnDefinition = "MEDIUMTEXT")
    private String responseBody;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 성공 처리 — 종료 시각과 소요 시간을 함께 기록한다. */
    public void markSucceeded(String responseBody) {
        this.status = JobStatus.SUCCEEDED;
        this.responseBody = responseBody;
        finish();
    }

    /** 실패 처리. 예외 메시지가 null 인 경우(NPE 등)도 흔해서 클래스명으로 대체한다. */
    public void markFailed(Throwable cause) {
        this.status = JobStatus.FAILED;
        this.errorMessage = cause == null
                ? "알 수 없는 오류"
                : (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName());
        finish();
    }

    private void finish() {
        this.finishedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.latencyMs = (int) java.time.Duration.between(this.startedAt, this.finishedAt).toMillis();
        }
    }
}
