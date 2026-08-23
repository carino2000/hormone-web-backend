package com.sixletter.hormone_web_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Day 1 ~ 현재까지의 전체 스냅샷. 프론트가 새로고침해도 화면을 통째로 복구할 수 있게
 * 한 번에 다 내려준다. 프론트 {@code simulationSource} 어댑터의 입력이기도 하다.
 *
 * <p>아직 도달하지 않은 미래 일차는 <b>내려주지 않는다</b> — 시연에서 예측을 미리 스포일링하면 안 된다.
 *
 * @param baseline 개인 평소값. 호르몬 원시값 대신 "평소 대비"로 번역할 때 쓴다
 */
public record DemoTimelineDto(
        Long userId,
        LocalDate startDate,
        int currentDay,
        int totalDays,
        int coldStartDays,
        String status,
        Map<String, Object> baseline,
        List<Day> days
) {

    /**
     * @param wearable   DB 컬럼명 기준 44개 피처. 결측은 null
     * @param prediction 콜드스타트 구간이면 null
     * @param truth      실측 정답 라벨. 예측과 겹쳐 그려서 "맞히는 모델"임을 보여준다(P-01).
     *                   콜드스타트 구간은 예측이 없으므로 비교 대상도 없어 null
     */
    public record Day(int day, LocalDate date, Map<String, Object> wearable,
                      PredictionDto prediction, Truth truth) {
    }

    /**
     * 실측 호르몬 값. mcPHASES 원본의 정답 라벨이다.
     *
     * <p><b>이건 모델 입력이 아니라 채점표다.</b> 절대 예측 요청에 실어 보내지 말 것.
     * 용도는 두 가지뿐 — 화면에서 예측과 대조, 그리고 Mock Predictor 가 그럴듯한 값을 만들 때 참조.
     */
    public record Truth(String phase, BigDecimal lh, BigDecimal estrogen, BigDecimal pdg) {
    }
}
