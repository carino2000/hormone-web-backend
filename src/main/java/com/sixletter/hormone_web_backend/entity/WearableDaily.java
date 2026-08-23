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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 웨어러블 일일 데이터. 사용자당 하루 1건 (user_id, measured_on 유니크).
 *
 * <p>주의: 안정시 심박 2개 컬럼은 모델 피처명과 이름이 엇갈려 있다.
 * restingHeartRate(DB: resting_heart_rate) -> 모델: value
 * sleepRestingHeartRate(DB: sleep_resting_heart_rate) -> 모델: resting_heart_rate
 * 모델로 넘기기 전 반드시 변환할 것.
 */
@Entity
@Table(
        name = "wearable_daily",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_date", columnNames = {"user_id", "measured_on"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WearableDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wearable_user"))
    private User user;

    @Column(name = "measured_on", nullable = false)
    private LocalDate measuredOn;

    // ---------- 활동 (분) ----------
    @Column(name = "sedentary")
    private Integer sedentary;

    @Column(name = "lightly")
    private Integer lightly;

    @Column(name = "moderately")
    private Integer moderately;

    @Column(name = "very")
    private Integer very;

    // ---------- Active Zone Minutes (분) ----------
    @Column(name = "FAT_BURN")
    private Integer fatBurn;

    @Column(name = "CARDIO")
    private Integer cardio;

    @Column(name = "PEAK")
    private Integer peak;

    // ---------- 고도 / 칼로리 ----------
    @Column(name = "altitude")
    private Integer altitude;

    @Column(name = "calories")
    private Integer calories;

    // ---------- 체온 ----------
    @Column(name = "temperature_samples")
    private Integer temperatureSamples;

    @Column(name = "nightly_temperature", precision = 7, scale = 4)
    private BigDecimal nightlyTemperature;

    // ---------- 체력 / 산소 ----------
    @Column(name = "filtered_demographic_vo2_max", precision = 7, scale = 4)
    private BigDecimal filteredDemographicVo2Max;

    @Column(name = "spo2_variation_std", precision = 6, scale = 3)
    private BigDecimal spo2VariationStd;

    // ---------- 운동 세션 ----------
    @Column(name = "originalduration")
    private Long originalduration;

    @Column(name = "averageheartrate")
    private Integer averageheartrate;

    @Column(name = "exercise_calories")
    private Integer exerciseCalories;

    @Column(name = "steps")
    private Long steps;

    // ---------- 혈당 (CGM) ----------
    @Column(name = "glucose_mean", precision = 5, scale = 3)
    private BigDecimal glucoseMean;

    @Column(name = "glucose_std", precision = 5, scale = 3)
    private BigDecimal glucoseStd;

    // ---------- 심박 ----------
    @Column(name = "bpm", precision = 6, scale = 2)
    private BigDecimal bpm;

    @Column(name = "bpm_min")
    private Integer bpmMin;

    @Column(name = "bpm_max")
    private Integer bpmMax;

    // ---------- 안정시 심박 (모델 피처명과 이름이 엇갈려 있음. 클래스 주석 참고) ----------
    @Column(name = "resting_heart_rate", precision = 5, scale = 2)
    private BigDecimal restingHeartRate;

    @Column(name = "sleep_resting_heart_rate")
    private Integer sleepRestingHeartRate;

    // ---------- HRV ----------
    @Column(name = "rmssd", precision = 7, scale = 3)
    private BigDecimal rmssd;

    @Column(name = "low_frequency", precision = 9, scale = 3)
    private BigDecimal lowFrequency;

    @Column(name = "high_frequency", precision = 9, scale = 3)
    private BigDecimal highFrequency;

    // ---------- 호흡 (회/분) ----------
    @Column(name = "full_sleep_breathing_rate", precision = 4, scale = 1)
    private BigDecimal fullSleepBreathingRate;

    @Column(name = "deep_sleep_breathing_rate", precision = 4, scale = 1)
    private BigDecimal deepSleepBreathingRate;

    @Column(name = "light_sleep_breathing_rate", precision = 4, scale = 1)
    private BigDecimal lightSleepBreathingRate;

    @Column(name = "rem_sleep_breathing_rate", precision = 4, scale = 1)
    private BigDecimal remSleepBreathingRate;

    // ---------- 수면 ----------
    @Column(name = "minutesasleep", precision = 7, scale = 2)
    private BigDecimal minutesasleep;

    @Column(name = "efficiency", precision = 5, scale = 2)
    private BigDecimal efficiency;

    @Column(name = "minutesawake", precision = 6, scale = 2)
    private BigDecimal minutesawake;

    @Column(name = "nap_minutes_total")
    private Integer napMinutesTotal;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "deep_sleep_in_minutes")
    private Integer deepSleepInMinutes;

    @Column(name = "restlessness", precision = 6, scale = 4)
    private BigDecimal restlessness;

    // ---------- 스트레스 ----------
    @Column(name = "stress_score")
    private Integer stressScore;

    // ---------- 심박존 체류시간 (분) ----------
    @Column(name = "in_default_zone_3")
    private Integer inDefaultZone3;

    @Column(name = "in_default_zone_2")
    private Integer inDefaultZone2;

    @Column(name = "in_default_zone_1")
    private Integer inDefaultZone1;

    @Column(name = "below_default_zone_1")
    private Integer belowDefaultZone1;

    // ---------- 체온 편차 ----------
    @Column(name = "temperature_diff_from_baseline", precision = 6, scale = 3)
    private BigDecimal temperatureDiffFromBaseline;

    // ---------- 관리 ----------
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
