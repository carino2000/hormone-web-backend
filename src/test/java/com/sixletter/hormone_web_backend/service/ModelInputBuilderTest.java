package com.sixletter.hormone_web_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sixletter.hormone_web_backend.config.ModelProperties;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictRequest;
import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.entity.WearableDaily;
import com.sixletter.hormone_web_backend.support.WearableFeatures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 모델 입력 피처 매핑을 못 박는 테스트.
 *
 * <p>이 테스트의 존재 이유는 하나다 — <b>피처 이름이 조용히 틀리는 걸 막기 위해서.</b>
 * 이름이 하나만 어긋나도 모델은 에러 없이 그냥 이상한 값을 예측한다. 컴파일도 통과하고
 * 화면도 잘 뜬다. 사람이 눈치채기 매우 어렵다.
 */
@DisplayName("모델 입력 빌더")
class ModelInputBuilderTest {

    /**
     * 무색 44개 컬럼 = 모델 입력. merged_nan.xlsx 헤더 순서 그대로 하드코딩한다.
     * <b>여기는 DB 컬럼명이 아니라 모델 피처명이다</b> (안정시 심박 2개가 교차돼 있음).
     */
    private static final List<String> EXPECTED_MODEL_FEATURES = List.of(
            "sedentary", "lightly", "moderately", "very",
            "FAT_BURN", "CARDIO", "PEAK",
            "altitude", "calories",
            "temperature_samples", "nightly_temperature",
            "filtered_demographic_vo2_max", "spo2_variation_std",
            "originalduration", "averageheartrate", "exercise_calories", "steps",
            "glucose_mean", "glucose_std",
            "bpm", "bpm_min", "bpm_max",
            "value",                    // <- DB resting_heart_rate
            "resting_heart_rate",       // <- DB sleep_resting_heart_rate
            "rmssd", "low_frequency", "high_frequency",
            "full_sleep_breathing_rate", "deep_sleep_breathing_rate",
            "light_sleep_breathing_rate", "rem_sleep_breathing_rate",
            "minutesasleep", "efficiency", "minutesawake", "nap_minutes_total",
            "overall_score", "deep_sleep_in_minutes", "restlessness",
            "stress_score",
            "in_default_zone_3", "in_default_zone_2", "in_default_zone_1", "below_default_zone_1",
            "temperature_diff_from_baseline");

    /** 엑셀 초록색 = 모델에 보내지 않는 컬럼. */
    private static final List<String> MUST_NOT_SEND =
            List.of("id", "study_interval", "is_weekend", "day_in_study");

    private ModelProperties properties;
    private ModelInputBuilder builder;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new ModelProperties();
        builder = new ModelInputBuilder(properties);
        user = User.builder()
                .id(22L)
                .birthDate(LocalDate.of(2002, 5, 3))
                .ageOfFirstMenarche(10)
                .ethnicity("Southeast Asian")
                .build();
    }

    private WearableDaily day(LocalDate date) {
        return WearableDaily.builder().user(user).measuredOn(date).build();
    }

    @Test
    @DisplayName("모델 피처는 정확히 44개이며 이름과 순서가 원본과 일치한다")
    void featureNamesMatchExactly() {
        Map<String, Object> features = builder.toModelFeatures(day(LocalDate.of(2026, 8, 31)));

        assertThat(features).hasSize(44);
        assertThat(features.keySet()).containsExactlyElementsOf(EXPECTED_MODEL_FEATURES);
    }

    @Test
    @DisplayName("★ 안정시 심박 교차 매핑 — 헷갈려서 고치면 조용히 틀린 예측이 나간다")
    void restingHeartRateCrossMapping() {
        // 근거: mcPHASES 원본에서
        //   resting_heart_rate.csv 의 컬럼명이 "value"              (일간, 소수)
        //   sleep_score.csv       의 컬럼명이 "resting_heart_rate"  (수면중, 정수)
        // 실측 예시는 id=22 / 2024구간 / Day 19 (LH 서지일)
        WearableDaily d = day(LocalDate.of(2026, 8, 31));
        d.setRestingHeartRate(new BigDecimal("85.40"));  // 일간
        d.setSleepRestingHeartRate(80);                  // 수면중

        Map<String, Object> f = builder.toModelFeatures(d);

        assertThat(f.get("value")).isEqualTo(new BigDecimal("85.40"));
        assertThat(f.get("resting_heart_rate")).isEqualTo(80);
    }

    @Test
    @DisplayName("엑셀 초록색 컬럼(id/study_interval/is_weekend/day_in_study)은 보내지 않는다")
    void greenColumnsAreNeverSent() {
        Map<String, Object> f = builder.toModelFeatures(day(LocalDate.of(2026, 8, 31)));
        assertThat(f.keySet()).doesNotContainAnyElementsOf(MUST_NOT_SEND);
    }

    @Test
    @DisplayName("정적 피처 3개(엑셀 주황색)가 포함된다 — 예전엔 누락돼 있었다")
    void staticFeaturesAreIncluded() {
        ModelPredictRequest req = builder.build(
                user, List.of(day(LocalDate.of(2026, 8, 31))), LocalDate.of(2026, 8, 31));

        assertThat(req.staticInfo().birthYear()).isEqualTo(2002);   // birth_date 에서 연도만
        assertThat(req.staticInfo().ageOfFirstMenarche()).isEqualTo(10);
        assertThat(req.staticInfo().ethnicity()).isEqualTo("Southeast Asian");
    }

    @Test
    @DisplayName("결측은 null 로 유지되고 키는 사라지지 않는다")
    void missingValuesStayNull() {
        // 2024 관측구간은 혈당/sedentary 가 아예 없다 — 결측이 정상 상태
        Map<String, Object> f = builder.toModelFeatures(day(LocalDate.of(2026, 8, 31)));

        assertThat(f).containsKey("glucose_mean");
        assertThat(f.get("glucose_mean")).isNull();   // 0 이 아니라 null
        assertThat(f).containsKey("sedentary");
        assertThat(f.get("sedentary")).isNull();
    }

    @Test
    @DisplayName("input-mode 설정만으로 하루치 / 누적 / 윈도우가 전환된다")
    void inputModeSwitching() {
        List<WearableDaily> history = List.of(
                day(LocalDate.of(2026, 8, 13)), day(LocalDate.of(2026, 8, 14)),
                day(LocalDate.of(2026, 8, 15)), day(LocalDate.of(2026, 8, 16)),
                day(LocalDate.of(2026, 8, 17)));
        LocalDate target = LocalDate.of(2026, 8, 17);

        properties.setInputMode(ModelProperties.InputMode.SINGLE_DAY);
        var single = builder.build(user, history, target);
        assertThat(single.history()).hasSize(1);
        assertThat(single.history().get(0).date()).isEqualTo(target);

        properties.setInputMode(ModelProperties.InputMode.FULL_HISTORY);
        assertThat(builder.build(user, history, target).history()).hasSize(5);

        properties.setInputMode(ModelProperties.InputMode.WINDOW);
        properties.setWindowSize(3);
        var window = builder.build(user, history, target);
        assertThat(window.history()).hasSize(3);
        assertThat(window.history().get(0).date()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    @DisplayName("입력 순서가 뒤섞여 있어도 날짜 오름차순으로 정렬해서 보낸다")
    void historyIsSortedAscending() {
        List<WearableDaily> shuffled = List.of(
                day(LocalDate.of(2026, 8, 15)),
                day(LocalDate.of(2026, 8, 13)),
                day(LocalDate.of(2026, 8, 14)));

        var req = builder.build(user, shuffled, LocalDate.of(2026, 8, 15));

        assertThat(req.history().stream().map(ModelPredictRequest.DayFeatures::date))
                .containsExactly(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 15));
    }

    @Test
    @DisplayName("★ JSON 직렬화 후에도 피처 키가 44개 그대로다 (결측이 키째 사라지면 안 됨)")
    void nullFeaturesSurviveSerialization() throws Exception {
        // 실제로 났던 버그: application.yaml 의 jackson.default-property-inclusion: NON_NULL 이
        // 모델 요청에도 적용돼서, 그날 결측인 피처의 키가 통째로 빠진 채 나갔다.
        // (44개 중 40개만 전송됨 → 날마다 피처 개수가 달라져 고정 길이 벡터 모델이 깨진다)
        // PythonPredictionClient 는 전용 매퍼로 null 을 살려 보낸다.
        var mapper = tools.jackson.databind.json.JsonMapper.builder()
                .changeDefaultPropertyInclusion(incl ->
                        incl.withValueInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS))
                .build();

        WearableDaily d = day(LocalDate.of(2026, 8, 31));
        d.setRmssd(new BigDecimal("39.369"));   // 이것만 값이 있고 나머지 43개는 결측

        String json = mapper.writeValueAsString(builder.toModelFeatures(d));
        var parsed = mapper.readValue(json, java.util.Map.class);

        assertThat(parsed).hasSize(44);
        assertThat(parsed).containsKey("glucose_mean");
        assertThat(parsed.get("glucose_mean")).isNull();
    }

    @Test
    @DisplayName("★ 시드 payload → 엔티티 → 모델 피처 전 구간에서 안정시 심박이 안 뒤바뀐다")
    void seedToModelKeepsRestingHeartRateStraight() {
        // 실제로 났던 버그: 시드가 merged_nan 헤더(= 모델 피처명)를 그대로 써서
        //   payload = {"value": 72.3, "resting_heart_rate": 75}
        // 인데 applyToEntity 는 DB 컬럼명을 기대했다. 그래서
        //   - "value"(일간 72.3) 는 모르는 키라 조용히 버려지고
        //   - "resting_heart_rate"(수면중 75) 가 일간 컬럼에 들어갔다
        // 에러가 안 나서 44개 중 2개가 틀린 채로 모델에 나갔다.
        //
        // 시드는 반드시 **DB 컬럼명**으로 저장돼야 한다.
        Map<String, Object> seedPayload = new java.util.HashMap<>();
        seedPayload.put("resting_heart_rate", 72.3);        // 일간 (소수)
        seedPayload.put("sleep_resting_heart_rate", 75);    // 수면중 (정수)

        WearableDaily entity = day(LocalDate.of(2026, 8, 13));
        WearableFeatures.applyToEntity(seedPayload, entity);

        // 엔티티에 제자리로 들어갔는가
        assertThat(entity.getRestingHeartRate()).isEqualByComparingTo("72.3");
        assertThat(entity.getSleepRestingHeartRate()).isEqualTo(75);

        // 모델 피처명으로 나갈 때 교차 매핑이 적용되는가
        Map<String, Object> features = builder.toModelFeatures(entity);
        assertThat(features.get("value")).isEqualTo(new BigDecimal("72.3"));
        assertThat(features.get("resting_heart_rate")).isEqualTo(75);
    }

    @Test
    @DisplayName("모델 피처명으로 된 payload 를 넣으면 일간 값이 유실된다 (그래서 시드는 DB 컬럼명이어야 한다)")
    void modelFeatureNamesInSeedWouldLoseData() {
        // 이 테스트는 "고쳐진 동작"이 아니라 **왜 시드를 DB 컬럼명으로 만들어야 하는지**를 고정한다.
        // 누군가 extract_seed.py 의 MERGED_TO_DB_COLUMN 변환을 지우면 이 형태가 되고,
        // 그때 무슨 일이 벌어지는지가 여기 적혀 있다.
        Map<String, Object> wrongPayload = new java.util.HashMap<>();
        wrongPayload.put("value", 72.3);                 // 모델 피처명 — DB 에는 없는 컬럼
        wrongPayload.put("resting_heart_rate", 75);      // 모델 세계에선 수면중, DB 세계에선 일간

        WearableDaily entity = day(LocalDate.of(2026, 8, 13));
        WearableFeatures.applyToEntity(wrongPayload, entity);

        // 일간 값 72.3 은 사라지고, 수면중 값 75 가 일간 자리에 들어간다
        assertThat(entity.getRestingHeartRate()).isEqualByComparingTo("75");
        assertThat(entity.getSleepRestingHeartRate()).isNull();
    }

    @Test
    @DisplayName("WearableFeatures 의 DB 컬럼 목록도 44개다")
    void wearableFeaturesColumnCount() {
        assertThat(WearableFeatures.featureCount()).isEqualTo(44);
        assertThat(WearableFeatures.columnNames())
                .contains("resting_heart_rate", "sleep_resting_heart_rate")
                .doesNotContain("value");   // DB 세계에는 "value" 컬럼이 없다
    }
}
