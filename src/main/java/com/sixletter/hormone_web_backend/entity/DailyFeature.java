package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
 * daily_features : X 데이터 - 웨어러블 피처 (Fitbit + Dexcom). append 방식, 결측 다수.
 * observed_on / sleep_spans_midnight 은 DB 생성 컬럼(STORED)이라 읽기 전용으로만 매핑.
 */
@Entity
@Table(
        name = "daily_features",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_observed", columnNames = {"user_id", "observed_at"}),
        indexes = {
                @Index(name = "idx_user_day", columnList = "user_id, observed_on"),
                @Index(name = "idx_user_study_day", columnList = "user_id, day_in_study"),
                @Index(name = "idx_user_synced", columnList = "user_id, device_synced_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_feat_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt;

    @Column(name = "observed_on", insertable = false, updatable = false,
            columnDefinition = "DATE GENERATED ALWAYS AS (DATE(observed_at)) STORED")
    private LocalDate observedOn;

    @Column(name = "day_in_study", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer dayInStudy;

    @Column(name = "study_interval", columnDefinition = "SMALLINT UNSIGNED")
    private Integer studyInterval;

    @Builder.Default
    @Column(name = "is_weekend", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean weekend = false;

    // ---------- 수면 세션 구간 ----------
    @Column(name = "sleep_start_at")
    private LocalDateTime sleepStartAt;

    @Column(name = "sleep_end_at")
    private LocalDateTime sleepEndAt;

    @Column(name = "sleep_spans_midnight", insertable = false, updatable = false,
            columnDefinition = "TINYINT(1) GENERATED ALWAYS AS (DATE(sleep_start_at) <> DATE(sleep_end_at)) STORED")
    private Boolean sleepSpansMidnight;

    // ---------- 활동 : active_minutes.csv ----------
    @Column(name = "sedentary", columnDefinition = "SMALLINT UNSIGNED")
    private Integer sedentary;

    @Column(name = "lightly", columnDefinition = "SMALLINT UNSIGNED")
    private Integer lightly;

    @Column(name = "moderately", columnDefinition = "SMALLINT UNSIGNED")
    private Integer moderately;

    @Column(name = "very", columnDefinition = "SMALLINT UNSIGNED")
    private Integer very;

    // ---------- 칼로리 / 고도 ----------
    @Column(name = "calories", columnDefinition = "SMALLINT UNSIGNED")
    private Integer calories;

    @Column(name = "altitude", columnDefinition = "SMALLINT UNSIGNED")
    private Integer altitude;

    // ---------- 운동 세션 : exercise.csv ----------
    @Column(name = "exercise_start_at")
    private LocalDateTime exerciseStartAt;

    @Column(name = "originalduration", columnDefinition = "INT UNSIGNED")
    private Integer originalDuration;

    @Column(name = "averageheartrate", columnDefinition = "SMALLINT UNSIGNED")
    private Integer averageHeartRate;

    @Column(name = "exercise_calories", columnDefinition = "SMALLINT UNSIGNED")
    private Integer exerciseCalories;

    @Column(name = "steps", columnDefinition = "INT UNSIGNED")
    private Integer steps;

    // ---------- 심박 : heart_rate.csv ----------
    @Column(name = "bpm", precision = 6, scale = 2)
    private BigDecimal bpm;

    @Column(name = "bpm_min", columnDefinition = "SMALLINT UNSIGNED")
    private Integer bpmMin;

    @Column(name = "bpm_max", columnDefinition = "SMALLINT UNSIGNED")
    private Integer bpmMax;

    // ---------- 안정시 심박 ----------
    @Column(name = "rhr_daily", precision = 5, scale = 2)
    private BigDecimal rhrDaily;

    @Column(name = "rhr_sleep", columnDefinition = "SMALLINT UNSIGNED")
    private Integer rhrSleep;

    // ---------- Active Zone Minutes ----------
    @Column(name = "fat_burn", columnDefinition = "SMALLINT UNSIGNED")
    private Integer fatBurn;

    @Column(name = "cardio", columnDefinition = "SMALLINT UNSIGNED")
    private Integer cardio;

    @Column(name = "peak", columnDefinition = "SMALLINT UNSIGNED")
    private Integer peak;

    // ---------- 심박존 체류시간 ----------
    @Column(name = "below_default_zone_1", columnDefinition = "SMALLINT UNSIGNED")
    private Integer belowDefaultZone1;

    @Column(name = "in_default_zone_1", columnDefinition = "SMALLINT UNSIGNED")
    private Integer inDefaultZone1;

    @Column(name = "in_default_zone_2", columnDefinition = "SMALLINT UNSIGNED")
    private Integer inDefaultZone2;

    @Column(name = "in_default_zone_3", columnDefinition = "SMALLINT UNSIGNED")
    private Integer inDefaultZone3;

    // ---------- HRV ----------
    @Column(name = "rmssd", precision = 7, scale = 3)
    private BigDecimal rmssd;

    @Column(name = "low_frequency", precision = 9, scale = 3)
    private BigDecimal lowFrequency;

    @Column(name = "high_frequency", precision = 9, scale = 3)
    private BigDecimal highFrequency;

    // ---------- 수면 : sleep.csv ----------
    @Column(name = "minutesasleep", precision = 7, scale = 2)
    private BigDecimal minutesAsleep;

    @Column(name = "minutesawake", precision = 6, scale = 2)
    private BigDecimal minutesAwake;

    @Column(name = "efficiency", precision = 5, scale = 2)
    private BigDecimal efficiency;

    @Column(name = "nap_minutes_total", columnDefinition = "SMALLINT UNSIGNED")
    private Integer napMinutesTotal;

    // ---------- 수면 점수 ----------
    @Column(name = "overall_score", columnDefinition = "TINYINT UNSIGNED")
    private Integer overallScore;

    @Column(name = "deep_sleep_in_minutes", columnDefinition = "SMALLINT UNSIGNED")
    private Integer deepSleepInMinutes;

    @Column(name = "restlessness", precision = 6, scale = 4)
    private BigDecimal restlessness;

    // ---------- 호흡 ----------
    @Column(name = "full_sleep_breathing_rate", precision = 4, scale = 1)
    private BigDecimal fullSleepBreathingRate;

    @Column(name = "deep_sleep_breathing_rate", precision = 4, scale = 1)
    private BigDecimal deepSleepBreathingRate;

    @Column(name = "light_sleep_breathing_rate", precision = 4, scale = 1)
    private BigDecimal lightSleepBreathingRate;

    @Column(name = "rem_sleep_breathing_rate", precision = 4, scale = 1)
    private BigDecimal remSleepBreathingRate;

    // ---------- 체온 ----------
    @Column(name = "nightly_temperature", precision = 7, scale = 4)
    private BigDecimal nightlyTemperature;

    @Column(name = "temperature_samples", columnDefinition = "SMALLINT UNSIGNED")
    private Integer temperatureSamples;

    // ---------- 체온 편차 ----------
    @Column(name = "temperature_diff_from_baseline", precision = 6, scale = 3)
    private BigDecimal temperatureDiffFromBaseline;

    // ---------- 산소 ----------
    @Column(name = "spo2_variation_std", precision = 6, scale = 3)
    private BigDecimal spo2VariationStd;

    // ---------- 체력 ----------
    @Column(name = "filtered_demographic_vo2_max", precision = 7, scale = 4)
    private BigDecimal filteredDemographicVo2Max;

    // ---------- 혈당 ----------
    @Column(name = "glucose_mean", precision = 5, scale = 3)
    private BigDecimal glucoseMean;

    @Column(name = "glucose_std", precision = 5, scale = 3)
    private BigDecimal glucoseStd;

    // ---------- 스트레스 ----------
    @Column(name = "stress_score", columnDefinition = "TINYINT UNSIGNED")
    private Integer stressScore;

    // ---------- 수집 상태 ----------
    @Column(name = "device_synced_at")
    private LocalDateTime deviceSyncedAt;

    @Column(name = "ingested_at")
    private LocalDateTime ingestedAt;

    @Column(name = "window_start_at")
    private LocalDateTime windowStartAt;

    @Column(name = "window_end_at")
    private LocalDateTime windowEndAt;

    // ---------- 파생 ----------
    @Builder.Default
    @Column(name = "is_valid_day", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean validDay = false;

    @Column(name = "completeness", precision = 4, scale = 3)
    private BigDecimal completeness;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
