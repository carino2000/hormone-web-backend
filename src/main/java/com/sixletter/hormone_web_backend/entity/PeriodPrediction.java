package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * period_predictions : Y 데이터(2) - 월경 예정일 예측. 매일 재계산되어 append, UPDATE 금지.
 * predicted_date / range_* 는 "날"의 예측이므로 DATE 유지, days_until 은 음수 허용(UNSIGNED 아님).
 */
@Entity
@Table(
        name = "period_predictions",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_run", columnNames = {"user_id", "predicted_at"}),
        indexes = {
                @Index(name = "idx_user_latest", columnList = "user_id, predicted_at"),
                @Index(name = "idx_user_day", columnList = "user_id, predicted_on")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prediction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pp_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "predicted_at", nullable = false)
    private LocalDateTime predictedAt;

    @Column(name = "predicted_on", insertable = false, updatable = false,
            columnDefinition = "DATE GENERATED ALWAYS AS (DATE(predicted_at)) STORED")
    private LocalDate predictedOn;

    @Column(name = "predicted_date", nullable = false)
    private LocalDate predictedDate;

    @Column(name = "range_start")
    private LocalDate rangeStart;

    @Column(name = "range_end")
    private LocalDate rangeEnd;

    @Column(name = "days_until")
    private Integer daysUntil;

    @Column(name = "input_days_used", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer inputDaysUsed;

    @Column(name = "input_through_at")
    private LocalDateTime inputThroughAt;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "model_version", nullable = false, length = 32)
    private String modelVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
