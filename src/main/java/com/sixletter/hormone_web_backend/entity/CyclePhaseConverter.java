package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link CyclePhase} ↔ DB VARCHAR 변환.
 *
 * <p>{@code @Enumerated(EnumType.STRING)} 을 쓰면 자바 상수명("MENSTRUAL")이 그대로 저장되는데,
 * DB CHECK 제약과 모델 계약은 "Menstrual" 형태를 요구한다. 그래서 별도 컨버터를 쓴다.
 *
 * <p>{@code autoApply = true} 라 엔티티 필드에 별도 애노테이션 없이 자동 적용된다.
 */
@Converter(autoApply = true)
public class CyclePhaseConverter implements AttributeConverter<CyclePhase, String> {

    @Override
    public String convertToDatabaseColumn(CyclePhase attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public CyclePhase convertToEntityAttribute(String dbData) {
        // 알 수 없는 값이면 null. DB CHECK 제약이 있어 실제로는 발생하지 않지만,
        // 수동으로 넣은 행이 있어도 조회 전체가 터지지 않게 방어한다.
        return CyclePhase.fromLabel(dbData).orElse(null);
    }
}
