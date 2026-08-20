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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * hormone_predictions : Y 데이터(1) - 호르몬 추정치. append 방식, UPDATE 금지.
 * 같은 target_at 을 재추론해도 새 행으로 쌓이므로 predicted_at 최신순 조회가 기본.
 * model_version, input_through_at 은 모델 확정 전까지 값/의미가 바뀔 수 있음.
 */
@Entity
@Table(
        name = "hormone_predictions",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_target_run", columnNames = {"user_id", "target_at", "predicted_at"}),
        indexes = @Index(name = "idx_user_target_latest", columnList = "user_id, target_on, predicted_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HormonePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prediction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_horm_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "target_at", nullable = false)
    private LocalDateTime targetAt;

    @Column(name = "target_on", insertable = false, updatable = false,
            columnDefinition = "DATE GENERATED ALWAYS AS (DATE(target_at)) STORED")
    private LocalDate targetOn;

    @Column(name = "lh", precision = 6, scale = 2)
    private BigDecimal lh;

    @Column(name = "estrogen", precision = 6, scale = 2)
    private BigDecimal estrogen;

    @Column(name = "pdg", precision = 5, scale = 2)
    private BigDecimal pdg;

    @Column(name = "lh_baseline_ratio", precision = 6, scale = 3)
    private BigDecimal lhBaselineRatio;

    @Column(name = "estrogen_baseline_ratio", precision = 6, scale = 3)
    private BigDecimal estrogenBaselineRatio;

    @Column(name = "pdg_baseline_ratio", precision = 6, scale = 3)
    private BigDecimal pdgBaselineRatio;

    /**
     * ENUM('Menstrual','Follicular','Fertility','Luteal').
     * 값 목록이 아직 안정적이지 않을 수 있어 Java enum 대신 String 으로 매핑.
     */
    @Column(name = "phase", length = 16)
    private String phase;

    @Column(name = "phase_confidence", precision = 4, scale = 3)
    private BigDecimal phaseConfidence;

    @Column(name = "model_version", nullable = false, length = 32)
    private String modelVersion;

    @Column(name = "predicted_at", nullable = false)
    private LocalDateTime predictedAt;

    @Column(name = "input_through_at")
    private LocalDateTime inputThroughAt;
}
