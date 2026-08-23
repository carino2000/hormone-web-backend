package com.sixletter.hormone_web_backend.dto;

import java.time.LocalDate;

/**
 * 데모 진행 상태.
 *
 * <p><b>★ {@code status} 문자열 하나로 프론트 화면 분기가 결정된다.</b>
 * 값/이름을 바꾸면 프론트가 통째로 깨지므로 프론트와 합의 없이 건드리지 말 것.
 *
 * @param status {@code collecting} 수집 중 | {@code active} 예측 중 |
 *               {@code insufficient_data} 결측 과다로 예측 불가 | {@code done} 마지막 날 도달
 */
public record DemoStateDto(
        Long userId,
        LocalDate startDate,
        int currentDay,
        LocalDate currentDate,
        int totalDays,
        int coldStartDays,
        String status
) {
}
