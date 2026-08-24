package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Claude API 로 받은 "오늘의 조언". 사용자당 하루 1건.
 *
 * <p><b>왜 저장하나:</b> 같은 날짜를 다시 열 때 재호출하지 않기 위해서다.
 * 한 번에 최근 30일 웨어러블이 들어가서 1만 토큰쯤 되는데, 탭을 옮길 때마다
 * 다시 부르면 비용이 그대로 곱해진다.
 *
 * <p><b>실패도 남긴다.</b> 시연 중 조언이 안 뜰 때 원인을 화면에서 바로 볼 수 있어야 한다.
 *
 * <p><b>프롬프트 원문은 저장하지 않는다.</b> 한 건이 30KB 가 넘고 시드에서 언제든
 * 재구성할 수 있다. 대신 무엇을 보냈는지 요약({@code sentDays}/{@code sentFeatures})만 남긴다.
 *
 * <p>{@code user} 를 연관관계로 두지 않고 {@code userId} 로 둔 이유는 {@code PredictionJob}
 * 과 같다 — 실패 기록을 남기는 경로에서 User 를 로드하다 또 실패하는 상황을 피하려는 것.
 */
@Entity
@Table(
        name = "daily_advice",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_advice_user_date", columnNames = {"user_id", "target_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyAdvice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "day_in_study")
    private Integer dayInStudy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AdviceStatus status;

    /** 조언 본문. 마크다운으로 온다. */
    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 최근 며칠치를 보냈나. 프롬프트 원문 대신 남기는 요약. */
    @Column(name = "sent_days")
    private Integer sentDays;

    /** 하루당 피처 개수. */
    @Column(name = "sent_features")
    private Integer sentFeatures;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    /**
     * max_tokens 에 걸려 문장 중간에서 잘렸는지.
     * 잘린 조언을 아무 표시 없이 화면에 띄우면 말이 끊긴 채로 보인다.
     */
    @Column(name = "truncated", nullable = false)
    private boolean truncated;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** 호출 시작 시각. 지연 계산에만 쓰고 저장하지 않는다. */
    @jakarta.persistence.Transient
    private LocalDateTime startedAt;

    public void markSucceeded(String content, String model, Integer inputTokens, Integer outputTokens,
                              boolean truncated) {
        this.status = AdviceStatus.SUCCEEDED;
        this.content = content;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.truncated = truncated;
        finish();
    }

    /** 예외 메시지가 null 인 경우(NPE 등)도 흔해서 클래스명으로 대체한다. */
    public void markFailed(Throwable cause) {
        this.status = AdviceStatus.FAILED;
        this.errorMessage = cause == null
                ? "알 수 없는 오류"
                : (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName());
        finish();
    }

    private void finish() {
        if (this.startedAt != null) {
            this.latencyMs = (int) Duration.between(this.startedAt, LocalDateTime.now()).toMillis();
        }
    }
}
