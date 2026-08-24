package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 예측 결과. 사용자당 하루 1건 (user_id, target_date 유니크).
 *
 * <p><b>★ 슈퍼셋 가정 (TODO_ROADMAP.md 규칙 10)</b>
 * <ul>
 *   <li>예측 필드는 <b>전부 nullable</b> 이다. 모델이 뭘 돌려주든 담을 수 있어야 하고,
 *       안 오는 필드는 null 로 두면 된다. 화면은 null 인 카드를 조용히 숨긴다.</li>
 *   <li>호르몬마다 모델이 따로 돌아 결과가 쪼개져 와도 같은 행에 <b>필드 단위로 병합</b>한다
 *       ({@link #mergeFrom}). 덮어쓰기가 아니다.</li>
 *   <li>모르는 필드는 버리지 말고 {@link #rawResponse} 에 통째로 남긴다.</li>
 * </ul>
 *
 * <p><b>결측 처리:</b> 값이 없으면 {@code null} 이다. 절대 {@code 0} 으로 채우지 말 것.
 * 특히 {@code pdg} 는 학습 데이터에서 64.7% 결측이라 null 이 정상 상태다.
 */
@Entity
@Table(
        name = "prediction_result",
        uniqueConstraints = @UniqueConstraint(name = "uk_pred_user_date", columnNames = {"user_id", "target_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pred_user"))
    private User user;

    /** 예측 대상 날짜(사용자에게 보여줄 절대 날짜). */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /** 연구 n일째(상대 시간축). 모델 입력에는 안 쓰지만 디버깅/정렬에 유용해서 보관만 한다. */
    @Column(name = "day_in_study")
    private Integer dayInStudy;

    // ---------- 호르몬 3종 (노란색 = 예측 대상) ----------
    @Column(name = "lh", precision = 8, scale = 3)
    private BigDecimal lh;

    @Column(name = "estrogen", precision = 8, scale = 3)
    private BigDecimal estrogen;

    /** 결측 정상. 학습 데이터 기준 64.7% 결측이고, 2022 관측구간에는 아예 없다. */
    @Column(name = "pdg", precision = 8, scale = 3)
    private BigDecimal pdg;

    // ---------- 주기 단계 ----------
    /** {@link CyclePhaseConverter} 가 "Fertility" 형태로 변환해 저장한다. */
    @Column(name = "phase", length = 16)
    private CyclePhase phase;

    @Column(name = "phase_confidence", precision = 4, scale = 3)
    private BigDecimal phaseConfidence;

    /** {"Menstrual":0.02, "Follicular":0.11, ...} — 모델이 확률분포를 주면 저장. */

    // ---------- 다음 월경 예정일 ----------
    // 이 값은 매일 재계산되므로 "흔들린다". 점이 아니라 범위로 다룰 것.


    // ---------- 부가 정보 ----------
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contributions")
    private List<Contribution> contributions;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    /**
     * 파이썬 응답 원문. 계약이 바뀌어도 데이터를 잃지 않기 위한 안전망이다.
     * 우리가 아직 매핑하지 않은 필드도 여기 남는다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response")
    private Map<String, Object> rawResponse;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /**
     * 다른 예측 결과를 이 행에 <b>필드 단위로 병합</b>한다.
     *
     * <p>호르몬마다 모델이 따로 돌면 응답이 쪼개져서 온다 (예: lh 만 담긴 응답이 먼저 오고
     * estrogen 만 담긴 응답이 나중에 옴). 그때 나중 응답이 앞 응답을 <b>지워버리면 안 된다.</b>
     * 그래서 {@code other} 의 값이 null 이 아닐 때만 덮어쓴다.
     *
     * <p>주의: 이 규칙 때문에 "모델이 의도적으로 null 을 보냈다"와 "이번 응답엔 그 필드가 없다"를
     * 구분하지 못한다. PoC 범위에서는 후자가 압도적으로 흔하므로 이렇게 둔다.
     * 구분이 필요해지면 응답 DTO 에 명시적 present 플래그를 두고 이 메서드를 고칠 것.
     */
    public void mergeFrom(PredictionResult other) {
        if (other == null) {
            return;
        }
        if (other.dayInStudy != null) this.dayInStudy = other.dayInStudy;

        if (other.lh != null) this.lh = other.lh;
        if (other.estrogen != null) this.estrogen = other.estrogen;
        if (other.pdg != null) this.pdg = other.pdg;

        if (other.phase != null) this.phase = other.phase;
        // 모델이 확신도를 하나만 준다. 호르몬별 확신도 컬럼은 아무도 안 채워서 지웠다.
        if (other.phaseConfidence != null) this.phaseConfidence = other.phaseConfidence;

        if (other.contributions != null && !other.contributions.isEmpty()) {
            this.contributions = other.contributions;
        }
        if (other.modelVersion != null) this.modelVersion = other.modelVersion;
        if (other.rawResponse != null) this.rawResponse = other.rawResponse;
    }

    /** 호르몬 3종이 하나도 없고 phase 도 없으면 "사실상 빈 예측"이다. */
    public boolean isEmpty() {
        return lh == null && estrogen == null && pdg == null && phase == null;
    }
}
