package com.sixletter.hormone_web_backend.support;

import com.sixletter.hormone_web_backend.entity.WearableDaily;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 웨어러블 44개 피처 ↔ Map 변환. <b>키는 DB 컬럼명 기준</b>이다.
 *
 * <p>이 클래스가 44개 피처 목록의 단일 진실이다. 세 군데가 이걸 공유한다:
 * <ul>
 *   <li>{@code ModelInputBuilder} — 모델 피처명으로 한 번 더 번역해서 파이썬에 보냄</li>
 *   <li>데모 시드 적재 — {@code demo_seed_wearable.payload} → {@code wearable_daily}</li>
 *   <li>타임라인 응답 — 프론트에 그날의 생체신호를 내려줌</li>
 * </ul>
 *
 * <p><b>결측은 {@code null} 그대로 둔다.</b> 0 으로 채우지 말 것 —
 * 학습 데이터에서 결측률이 최대 69% 라 결측이 예외가 아니라 기본 상태다.
 * 키 자체는 항상 44개가 다 존재하고, 값만 null 이 된다.
 *
 * <p><b>★ 안정시 심박 2개 컬럼은 여기서 이름을 바꾸지 않는다.</b> 여기는 DB 컬럼명 세계다.
 * 모델 피처명으로의 교차 매핑({@code resting_heart_rate}→{@code value} 등)은
 * {@code ModelInputBuilder} 가 담당한다.
 */
@lombok.extern.slf4j.Slf4j
public final class WearableFeatures {

    private WearableFeatures() {
    }

    /** 하나의 피처: DB 컬럼명 + 엔티티에서 읽는 법 + 엔티티에 쓰는 법. */
    private record Field(String column, Function<WearableDaily, Object> getter,
                         BiConsumer<WearableDaily, Object> setter) {
    }

    // ---- 값 변환 헬퍼. JSON 에서 올라온 Number 를 엔티티 타입으로 맞춘다 ----
    private static Integer toInt(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static BigDecimal toDec(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        // Double 을 그대로 new BigDecimal(double) 하면 0.1 -> 0.1000000000000000055...
        // 가 되므로 반드시 문자열을 거친다.
        return new BigDecimal(v.toString());
    }

    private static final List<Field> FIELDS = Arrays.asList(
            // 활동 (분)
            new Field("sedentary", WearableDaily::getSedentary, (e, v) -> e.setSedentary(toInt(v))),
            new Field("lightly", WearableDaily::getLightly, (e, v) -> e.setLightly(toInt(v))),
            new Field("moderately", WearableDaily::getModerately, (e, v) -> e.setModerately(toInt(v))),
            new Field("very", WearableDaily::getVery, (e, v) -> e.setVery(toInt(v))),
            // Active Zone Minutes
            new Field("FAT_BURN", WearableDaily::getFatBurn, (e, v) -> e.setFatBurn(toInt(v))),
            new Field("CARDIO", WearableDaily::getCardio, (e, v) -> e.setCardio(toInt(v))),
            new Field("PEAK", WearableDaily::getPeak, (e, v) -> e.setPeak(toInt(v))),
            // 고도 / 칼로리
            new Field("altitude", WearableDaily::getAltitude, (e, v) -> e.setAltitude(toInt(v))),
            new Field("calories", WearableDaily::getCalories, (e, v) -> e.setCalories(toInt(v))),
            // 체온 (Fitbit 피부온도. 체온 아님 — 실측 31~35°C)
            new Field("temperature_samples", WearableDaily::getTemperatureSamples, (e, v) -> e.setTemperatureSamples(toInt(v))),
            new Field("nightly_temperature", WearableDaily::getNightlyTemperature, (e, v) -> e.setNightlyTemperature(toDec(v))),
            // 체력 / 산소
            new Field("filtered_demographic_vo2_max", WearableDaily::getFilteredDemographicVo2Max, (e, v) -> e.setFilteredDemographicVo2Max(toDec(v))),
            new Field("spo2_variation_std", WearableDaily::getSpo2VariationStd, (e, v) -> e.setSpo2VariationStd(toDec(v))),
            // 운동 세션
            new Field("originalduration", WearableDaily::getOriginalduration, (e, v) -> e.setOriginalduration(toLong(v))),
            new Field("averageheartrate", WearableDaily::getAverageheartrate, (e, v) -> e.setAverageheartrate(toInt(v))),
            new Field("exercise_calories", WearableDaily::getExerciseCalories, (e, v) -> e.setExerciseCalories(toInt(v))),
            new Field("steps", WearableDaily::getSteps, (e, v) -> e.setSteps(toLong(v))),
            // 혈당 (CGM). 단위 mmol/L — mg/dL 아님
            new Field("glucose_mean", WearableDaily::getGlucoseMean, (e, v) -> e.setGlucoseMean(toDec(v))),
            new Field("glucose_std", WearableDaily::getGlucoseStd, (e, v) -> e.setGlucoseStd(toDec(v))),
            // 심박
            new Field("bpm", WearableDaily::getBpm, (e, v) -> e.setBpm(toDec(v))),
            new Field("bpm_min", WearableDaily::getBpmMin, (e, v) -> e.setBpmMin(toInt(v))),
            new Field("bpm_max", WearableDaily::getBpmMax, (e, v) -> e.setBpmMax(toInt(v))),
            // 안정시 심박 — DB 컬럼명 그대로. 모델 피처명 변환은 ModelInputBuilder 가 한다
            new Field("resting_heart_rate", WearableDaily::getRestingHeartRate, (e, v) -> e.setRestingHeartRate(toDec(v))),
            new Field("sleep_resting_heart_rate", WearableDaily::getSleepRestingHeartRate, (e, v) -> e.setSleepRestingHeartRate(toInt(v))),
            // HRV
            new Field("rmssd", WearableDaily::getRmssd, (e, v) -> e.setRmssd(toDec(v))),
            new Field("low_frequency", WearableDaily::getLowFrequency, (e, v) -> e.setLowFrequency(toDec(v))),
            new Field("high_frequency", WearableDaily::getHighFrequency, (e, v) -> e.setHighFrequency(toDec(v))),
            // 호흡 (회/분)
            new Field("full_sleep_breathing_rate", WearableDaily::getFullSleepBreathingRate, (e, v) -> e.setFullSleepBreathingRate(toDec(v))),
            new Field("deep_sleep_breathing_rate", WearableDaily::getDeepSleepBreathingRate, (e, v) -> e.setDeepSleepBreathingRate(toDec(v))),
            new Field("light_sleep_breathing_rate", WearableDaily::getLightSleepBreathingRate, (e, v) -> e.setLightSleepBreathingRate(toDec(v))),
            new Field("rem_sleep_breathing_rate", WearableDaily::getRemSleepBreathingRate, (e, v) -> e.setRemSleepBreathingRate(toDec(v))),
            // 수면
            new Field("minutesasleep", WearableDaily::getMinutesasleep, (e, v) -> e.setMinutesasleep(toDec(v))),
            new Field("efficiency", WearableDaily::getEfficiency, (e, v) -> e.setEfficiency(toDec(v))),
            new Field("minutesawake", WearableDaily::getMinutesawake, (e, v) -> e.setMinutesawake(toDec(v))),
            new Field("nap_minutes_total", WearableDaily::getNapMinutesTotal, (e, v) -> e.setNapMinutesTotal(toInt(v))),
            new Field("overall_score", WearableDaily::getOverallScore, (e, v) -> e.setOverallScore(toInt(v))),
            new Field("deep_sleep_in_minutes", WearableDaily::getDeepSleepInMinutes, (e, v) -> e.setDeepSleepInMinutes(toInt(v))),
            new Field("restlessness", WearableDaily::getRestlessness, (e, v) -> e.setRestlessness(toDec(v))),
            // 스트레스
            new Field("stress_score", WearableDaily::getStressScore, (e, v) -> e.setStressScore(toInt(v))),
            // 심박존 체류시간 (분)
            new Field("in_default_zone_3", WearableDaily::getInDefaultZone3, (e, v) -> e.setInDefaultZone3(toInt(v))),
            new Field("in_default_zone_2", WearableDaily::getInDefaultZone2, (e, v) -> e.setInDefaultZone2(toInt(v))),
            new Field("in_default_zone_1", WearableDaily::getInDefaultZone1, (e, v) -> e.setInDefaultZone1(toInt(v))),
            new Field("below_default_zone_1", WearableDaily::getBelowDefaultZone1, (e, v) -> e.setBelowDefaultZone1(toInt(v))),
            // 체온 편차
            new Field("temperature_diff_from_baseline", WearableDaily::getTemperatureDiffFromBaseline, (e, v) -> e.setTemperatureDiffFromBaseline(toDec(v)))
    );

    /** 44개 피처의 DB 컬럼명 목록 (원본 CSV 순서). */
    public static List<String> columnNames() {
        return FIELDS.stream().map(Field::column).toList();
    }

    public static int featureCount() {
        return FIELDS.size();
    }

    /**
     * 엔티티 → Map. 값이 없어도 키는 남긴다(값만 null).
     * 모델이 "이 피처가 아예 없다"와 "측정이 안 됐다"를 구분할 수 있어야 하는데,
     * 후자가 정상 상태이므로 키를 항상 채워 보낸다.
     */
    public static Map<String, Object> toMap(WearableDaily entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field f : FIELDS) {
            map.put(f.column(), entity == null ? null : f.getter().apply(entity));
        }
        return map;
    }

    /**
     * Map → 엔티티. Map 에 없는 키는 건드리지 않고, 있으면 null 이라도 반영한다
     * (명시적 결측을 존중).
     *
     * <p><b>★ 모르는 키를 조용히 버리지 않는다.</b> 실제로 이것 때문에 버그가 났다 —
     * 시드가 모델 피처명({@code value})을 쓰고 여기는 DB 컬럼명을 기대해서,
     * 일간 안정시 심박이 통째로 버려지고 수면중 값이 일간 컬럼에 들어갔다.
     * 에러가 안 나서 44개 중 2개가 틀린 채로 모델에 나갔다.
     * 이름 체계가 두 개(DB 컬럼명 / 모델 피처명)라 앞으로도 헷갈리기 쉬우므로 경고를 남긴다.
     */
    public static void applyToEntity(Map<String, Object> source, WearableDaily target) {
        if (source == null || target == null) {
            return;
        }
        Set<String> known = new HashSet<>(columnNames());
        List<String> unknown = source.keySet().stream().filter(k -> !known.contains(k)).toList();
        if (!unknown.isEmpty()) {
            log.warn("알 수 없는 웨어러블 키 {}개를 무시합니다: {}. "
                            + "DB 컬럼명이 맞는지 확인하세요 (모델 피처명과 다릅니다 — "
                            + "일간 안정시심박은 DB 'resting_heart_rate', 모델 'value').",
                    unknown.size(), unknown);
        }

        for (Field f : FIELDS) {
            if (source.containsKey(f.column())) {
                f.setter().accept(target, source.get(f.column()));
            }
        }
    }
}
