package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 시연 시드 원본. merged_nan 실참가자(id=22, 2024구간, day_in_study 902~931)의 30일치.
 *
 * <p><b>wearable_daily 와 분리해 둔 이유:</b> "하루 넘기기"를 누르면 여기서 하루치를 꺼내
 * {@code wearable_daily} 로 옮긴다. 그래야 "데이터가 하루씩 쌓이는" 과정이 화면뿐 아니라
 * DB 에서도 실제로 재현된다. 처음부터 wearable_daily 에 30일을 다 넣어두면
 * 콜드스타트 구간이 거짓말이 된다.
 *
 * <p><b>⚠️ {@link #truth} 를 모델 입력으로 절대 보내지 말 것.</b> 정답 라벨이다.
 * 용도는 하나뿐:
 * (2) 시연 후 "예측값 vs 실측값" 비교.
 */
@Entity
@Table(
        name = "demo_seed_wearable",
        uniqueConstraints = @UniqueConstraint(name = "uk_seed_user_day", columnNames = {"user_id", "day_index"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoSeedWearable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 1..30 */
    @Column(name = "day_index", nullable = false)
    private Integer dayIndex;

    /**
     * 웨어러블 44개 피처. 키는 모델 피처명이 아니라 <b>DB 컬럼명</b> 기준이다
     * (예: {@code resting_heart_rate}, {@code sleep_resting_heart_rate}).
     * 원본 CSV 는 안정시 심박 2개 컬럼의 이름이 엇갈려 있다 — 시드 생성 스크립트가 번역해 넣는다.
     *
     * <p>결측은 {@code null} 로 들어 있다. 0 으로 채우지 말 것.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private Map<String, Object> payload;

    /** 실측 정답 라벨 {@code {phase, lh, estrogen, pdg}}. 모델 입력 금지. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "truth")
    private Map<String, Object> truth;
}
