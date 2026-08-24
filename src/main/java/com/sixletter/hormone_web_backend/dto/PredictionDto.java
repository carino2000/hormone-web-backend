package com.sixletter.hormone_web_backend.dto;

import com.sixletter.hormone_web_backend.entity.Contribution;
import com.sixletter.hormone_web_backend.entity.PredictionResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 프론트가 그리는 예측 결과 1건.
 *
 * <p>필드 이름과 중첩 구조를 프론트 목업(simulationDays.json)의 {@code days[].prediction}
 * 과 똑같이 맞췄다. 그래야 프론트 어댑터가 거의 통과(pass-through)로 끝난다.
 *
 * <p><b>슈퍼셋 가정:</b> 모델이 안 준 값은 null 로 나간다. 프론트는 null 인 항목의
 * 카드/라인을 조용히 숨기면 된다. 에러가 아니다.
 */
public record PredictionDto(
        LocalDate date,
        Integer dayInStudy,
        String phase,
        String phaseLabelKo,
        BigDecimal confidence,
        BigDecimal lh,
        BigDecimal estrogen,
        BigDecimal pdg,
        List<Contribution> contributions,
        String modelVersion
) {

    public static PredictionDto from(PredictionResult r) {
        if (r == null) {
            return null;
        }
        return new PredictionDto(
                r.getTargetDate(),
                r.getDayInStudy(),
                r.getPhase() == null ? null : r.getPhase().getLabel(),
                r.getPhase() == null ? null : r.getPhase().getLabelKo(),
                r.getPhaseConfidence(),
                r.getLh(), r.getEstrogen(), r.getPdg(),
                r.getContributions(),
                r.getModelVersion());
    }
}
