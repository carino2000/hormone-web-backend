package com.sixletter.hormone_web_backend.dto.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.util.List;

/**
 * 파이썬 예측 서버 → 백엔드 응답. <b>평평한 구조다.</b>
 *
 * <pre>
 * {
 *   "lh": 6.2,
 *   "estrogen": 88.6,
 *   "pdg": 3.8,
 *   "phase": "Fertility",
 *   "confidence": 0.87,
 *   "contributions": [{"feature": "rmssd", "weight": 0.42, "direction": "down"}],
 *   "modelVersion": "v0.3"
 * }
 * </pre>
 *
 * <p><b>★ 슈퍼셋 가정 — 필수 필드가 하나도 없다.</b> 전부 optional 이라
 * {@code phase} 만 와도 돌아간다. 없는 필드는 null 로 파싱되고,
 * {@code PredictionResult.mergeFrom} 이 기존 값을 지우지 않고 병합한다.
 * 모르는 필드는 버리지 않고 {@code prediction_result.raw_response} 에 원문으로 보관한다.
 *
 * <p><b>다음 월경 예정일({@code nextPeriod})은 받지 않는다.</b> 모델이 오늘자 phase 만
 * 알 수 있다고 해서 합의 하에 기능을 뺐다. 되살리려면 여기 필드를 추가하고
 * {@code PredictionResult} 와 프론트 화면을 함께 되돌려야 한다.
 *
 * <p><b>필드명에 관대하다.</b> 계약이 아직 안 굳어서 snake_case / camelCase 가 섞여 올 수
 * 있다. {@code @JsonAlias} 로 둘 다 받는다. 모르는 필드는
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false} 로 무시한다({@code PythonPredictionClient}).
 *
 * @param confidence 그날 <b>예측 하나</b>에 대한 확신도(0~1). 모델 성능 지표가 아니다.
 *                   모델 성능(MAE·정확도)은 백엔드가 예측 vs 실측을 비교해 따로 계산한다
 * @param error      모델이 200 안에 에러를 실어 보낼 수도 있어서 자리를 만들어 둔다
 */
public record ModelPredictResponse(
        BigDecimal lh,
        BigDecimal estrogen,
        BigDecimal pdg,

        /** Menstrual | Follicular | Fertility | Luteal. 대소문자가 달라도 CyclePhase.fromLabel 이 알아본다 */
        String phase,

        BigDecimal confidence,

        /** 배열 또는 맵 둘 다 온다. PythonPredictionClient 가 정규화해서 채운다 */
        List<ContributionDto> contributions,

        @JsonAlias({"model_version", "version"})
        String modelVersion,

        ErrorBody error
) {

    /**
     * 예측 근거 한 줄.
     *
     * @param feature   모델 피처명 (예 rmssd)
     * @param weight    기여도. 부호가 방향을 겸할 수 있다
     * @param direction "up" | "down". 없으면 weight 부호에서 유도한다
     * @param signal    사람이 읽는 이름. 없으면 프론트가 feature 를 그대로 쓴다
     */
    public record ContributionDto(String feature, BigDecimal weight, String direction, String signal) {
    }

    public record ErrorBody(String code, String message) {
    }

    public boolean hasError() {
        return error != null && error.code() != null;
    }

    /** 아무 값도 없는 응답인지. 전부 null 이면 저장할 게 없다 */
    public boolean isEmpty() {
        return lh == null && estrogen == null && pdg == null && phase == null;
    }
}
