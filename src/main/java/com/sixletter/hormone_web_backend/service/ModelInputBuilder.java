package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.config.ModelProperties;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictRequest;
import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.entity.WearableDaily;
import com.sixletter.hormone_web_backend.support.WearableFeatures;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DB 데이터 → 파이썬 모델 입력으로 변환.
 *
 * <p><b>모델 입력은 47개다:</b> 웨어러블 44개(엑셀 무색) + 정적 3개(엑셀 주황).
 * {@code id}, {@code study_interval}, {@code is_weekend}, {@code day_in_study}
 * (엑셀 초록)는 <b>보내지 않는다.</b>
 *
 * <p><b>★ 안정시 심박 교차 매핑 — 절대 고치지 말 것</b>
 * <pre>
 *   DB resting_heart_rate       (일간, 소수)  -> 모델 "value"
 *   DB sleep_resting_heart_rate (수면중, 정수) -> 모델 "resting_heart_rate"
 * </pre>
 * 근거: mcPHASES 원본에서 {@code resting_heart_rate.csv} 의 컬럼명이 {@code value} 이고,
 * {@code sleep_score.csv} 의 컬럼명이 {@code resting_heart_rate} 이다.
 * 헷갈려서 "고치는" 순간 조용히 틀린 예측이 나간다. {@code ModelInputBuilderTest} 가 이걸 고정한다.
 */
@Component
@RequiredArgsConstructor
public class ModelInputBuilder {

    /** DB 컬럼명 -> 모델 피처명. 여기 없는 컬럼은 이름이 같다. */
    private static final Map<String, String> COLUMN_TO_FEATURE = Map.of(
            "resting_heart_rate", "value",
            "sleep_resting_heart_rate", "resting_heart_rate"
    );

    private final ModelProperties modelProperties;

    /**
     * 예측 요청 본문을 만든다.
     *
     * @param history 오름차순 정렬된 웨어러블 이력. 비어 있으면 안 된다
     * @return input-mode 설정에 따라 하루치 / 누적 전체 / 최근 N일로 잘린 요청
     */
    public ModelPredictRequest build(User user, List<WearableDaily> history, LocalDate targetDate) {
        if (history == null || history.isEmpty()) {
            throw new IllegalArgumentException("모델 입력을 만들 웨어러블 데이터가 없습니다: userId=" + user.getId());
        }

        List<WearableDaily> sorted = history.stream()
                .sorted(Comparator.comparing(WearableDaily::getMeasuredOn))
                .toList();

        List<WearableDaily> window = switch (modelProperties.getInputMode()) {
            case SINGLE_DAY -> List.of(sorted.get(sorted.size() - 1));
            case WINDOW -> sorted.subList(Math.max(0, sorted.size() - modelProperties.getWindowSize()), sorted.size());
            case FULL_HISTORY -> sorted;
        };

        List<ModelPredictRequest.DayFeatures> days = window.stream()
                .map(d -> new ModelPredictRequest.DayFeatures(d.getMeasuredOn(), toModelFeatures(d)))
                .toList();

        return new ModelPredictRequest(user.getId(), targetDate, toStaticInfo(user), days);
    }

    /**
     * 정적 피처 3개 (엑셀 주황색 = 이미 DB 에 존재하는 값).
     * 모델이 학습에 쓴 건 생년월일이 아니라 {@code birth_year} 라 연도만 뽑는다.
     */
    public ModelPredictRequest.StaticInfo toStaticInfo(User user) {
        Integer birthYear = user.getBirthDate() == null ? null : user.getBirthDate().getYear();
        return new ModelPredictRequest.StaticInfo(birthYear, user.getAgeOfFirstMenarche(), user.getEthnicity());
    }

    /**
     * 웨어러블 하루치 → 모델 피처명 기준 44개 Map.
     *
     * <p>결측은 {@code null} 그대로 둔다. 0 이나 평균으로 채우지 않는다 —
     * 임퓨테이션 정책은 모델팀 소관이고, 우리가 임의로 채우면 조용히 틀린 예측이 된다.
     * 키는 항상 44개가 다 존재한다(값만 null).
     */
    public Map<String, Object> toModelFeatures(WearableDaily daily) {
        Map<String, Object> byColumn = WearableFeatures.toMap(daily);
        Map<String, Object> byFeature = new LinkedHashMap<>();
        byColumn.forEach((column, value) ->
                byFeature.put(COLUMN_TO_FEATURE.getOrDefault(column, column), value));
        return byFeature;
    }
}
