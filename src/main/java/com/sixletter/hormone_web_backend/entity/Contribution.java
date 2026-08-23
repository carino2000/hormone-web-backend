package com.sixletter.hormone_web_backend.entity;

import java.math.BigDecimal;

/**
 * 예측 근거 한 줄 (SHAP 개념의 기여도).
 * {@code prediction_result.contributions} JSON 배열의 원소로 저장된다.
 *
 * @param feature   모델 피처명. 예: "rmssd" (44개 피처명 중 하나)
 * @param weight    기여도 0~1
 * @param direction "up" | "down" — 해당 신호가 올라가서 기여했는지 내려가서 기여했는지
 * @param signal    화면 표시용 문구. 예: "HRV(rmssd) 감소". 모델이 안 주면 null
 */
public record Contribution(
        String feature,
        BigDecimal weight,
        String direction,
        String signal
) {
}
