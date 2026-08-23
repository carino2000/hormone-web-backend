package com.sixletter.hormone_web_backend.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 월경주기 단계. mcPHASES 실데이터의 4클래스로 고정한다.
 *
 * <p>이 4개가 전부다. {@code ovulation} 같은 다른 라벨을 추가하지 말 것.
 * DB 쪽에도 {@code ck_pred_phase} CHECK 제약이 걸려 있어서 다른 값은 저장 자체가 거부된다.
 *
 * <p>자바 상수는 관례대로 대문자를 쓰고, DB/모델과 주고받는 문자열은 {@link #getLabel()}
 * ("Menstrual" 등)을 쓴다. 변환은 {@link CyclePhaseConverter} 가 담당한다.
 *
 * <p>주기 순서: Menstrual(월경) → Follicular(난포기) → Fertility(가임기) → Luteal(황체기)
 */
public enum CyclePhase {

    MENSTRUAL("Menstrual", "월경기"),
    FOLLICULAR("Follicular", "난포기"),
    FERTILITY("Fertility", "가임기"),
    LUTEAL("Luteal", "황체기");

    private final String label;
    private final String labelKo;

    CyclePhase(String label, String labelKo) {
        this.label = label;
        this.labelKo = labelKo;
    }

    /** DB/모델과 주고받는 문자열. 예: "Fertility" */
    public String getLabel() {
        return label;
    }

    /** 화면 표시용 한국어. 예: "가임기" */
    public String getLabelKo() {
        return labelKo;
    }

    /**
     * 모델 응답 문자열을 enum 으로 변환한다.
     *
     * <p>예측 모델 계약이 아직 확정되지 않아 대소문자나 공백이 어떻게 올지 모른다.
     * ("fertility", "FERTILITY", " Luteal " 등) 그래서 관대하게 파싱한다.
     * 못 알아보는 값이면 예외를 던지지 않고 {@link Optional#empty()} 를 돌려준다 —
     * 슈퍼셋 가정(규칙 10)에 따라 모르는 값 때문에 예측 저장 전체가 실패하면 안 된다.
     */
    public static Optional<CyclePhase> fromLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim();
        return Arrays.stream(values())
                .filter(p -> p.label.equalsIgnoreCase(normalized) || p.name().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
