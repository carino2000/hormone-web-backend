package com.sixletter.hormone_web_backend.dto.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 파이썬 예측 서버 → 백엔드 응답. <b>계약 미확정 상태의 제안안이다.</b>
 *
 * <p><b>★ 슈퍼셋 가정 (규칙 10):</b> 모든 필드가 optional 이다.
 * 호르몬마다 모델이 따로 돌아 응답이 쪼개져 와도 되고, pdg 가 통째로 빠져도 된다.
 * 없는 필드는 null 로 파싱되고, {@code PredictionResult.mergeFrom} 이
 * 기존 값을 지우지 않고 병합한다.
 *
 * <p>Jackson 은 모르는 필드를 만나면 기본적으로 예외를 던지므로, 이 DTO 를 쓰는 쪽에서
 * FAIL_ON_UNKNOWN_PROPERTIES 를 꺼야 한다. 모르는 필드는 버리지 않고
 * {@code prediction_result.raw_response} 에 원문으로 따로 보관한다.
 */
public record ModelPredictResponse(
        Long userId,
        LocalDate targetDate,
        String modelVersion,
        Hormones hormones,
        Phase phase,
        NextPeriod nextPeriod,
        List<ContributionDto> contributions,
        ErrorBody error
) {

    /** 값 + 신뢰도. value 가 null 이면 "이번엔 예측 못 함"이다 (0 아님). */
    public record HormoneValue(BigDecimal value, BigDecimal confidence) {
    }

    public record Hormones(HormoneValue lh, HormoneValue estrogen, HormoneValue pdg) {
    }

    /**
     * @param label Menstrual | Follicular | Fertility | Luteal.
     *              대소문자가 달라도 CyclePhase.fromLabel 이 알아본다
     */
    public record Phase(String label, BigDecimal confidence, Map<String, BigDecimal> probabilities) {
    }

    public record NextPeriod(LocalDate predictedDate, LocalDate rangeStart, LocalDate rangeEnd) {
    }

    public record ContributionDto(String feature, BigDecimal weight, String direction, String signal) {
    }

    /** 모델이 정상 응답(200) 안에 에러를 실어 보낼 수도 있어서 자리를 만들어 둔다. */
    public record ErrorBody(String code, String message) {
    }

    public boolean hasError() {
        return error != null && error.code() != null;
    }
}
