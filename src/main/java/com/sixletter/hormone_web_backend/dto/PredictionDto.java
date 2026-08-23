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
        HormoneConfidence hormoneConfidence,
        NextPeriod nextPeriod,
        List<Contribution> contributions,
        String modelVersion
) {

    /** 호르몬별 신뢰도. pdg 는 학습 데이터 결측 64.7% 라 대개 낮게 나온다. */
    public record HormoneConfidence(BigDecimal lh, BigDecimal estrogen, BigDecimal pdg) {
    }

    /**
     * 다음 월경 예정일. <b>점이 아니라 범위로 다룬다</b> —
     * 누적 데이터로 매일 재계산되므로 반드시 흔들린다.
     */
    public record NextPeriod(LocalDate date, LocalDate rangeStart, LocalDate rangeEnd) {
    }

    public static PredictionDto from(PredictionResult r) {
        if (r == null) {
            return null;
        }
        NextPeriod np = (r.getNextPeriodDate() == null
                && r.getNextPeriodRangeStart() == null
                && r.getNextPeriodRangeEnd() == null)
                ? null
                : new NextPeriod(r.getNextPeriodDate(), r.getNextPeriodRangeStart(), r.getNextPeriodRangeEnd());

        return new PredictionDto(
                r.getTargetDate(),
                r.getDayInStudy(),
                r.getPhase() == null ? null : r.getPhase().getLabel(),
                r.getPhase() == null ? null : r.getPhase().getLabelKo(),
                r.getPhaseConfidence(),
                r.getLh(), r.getEstrogen(), r.getPdg(),
                new HormoneConfidence(r.getLhConfidence(), r.getEstrogenConfidence(), r.getPdgConfidence()),
                np,
                r.getContributions(),
                r.getModelVersion());
    }
}
