package com.sixletter.hormone_web_backend.dto.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 백엔드 → 파이썬 예측 서버 요청. <b>계약 미확정 상태의 제안안이다.</b>
 * 모델팀 답변이 오면 이 파일만 고치면 된다 (TODO_ROADMAP.md 부록 B).
 *
 * @param userId      사용자 식별자
 * @param targetDate  예측 대상 날짜 (history 의 마지막 원소와 같아야 함)
 * @param staticInfo  정적 피처 3개. 엑셀 주황색 = 이미 DB 에 있는 값
 * @param history     오름차순. input-mode 가 SINGLE_DAY 면 길이 1
 */
public record ModelPredictRequest(
        Long userId,
        LocalDate targetDate,
        StaticInfo staticInfo,
        List<DayFeatures> history
) {

    /**
     * 정적 피처. <b>현재 백엔드가 이 3개를 안 보내고 있었다</b> — 엑셀 기준으로는
     * 모델 입력에 포함돼야 한다.
     *
     * @param ethnicity 실데이터 8종 (White / East Asian / Southeast Asian / ...).
     *                  TODO: 문자열 그대로인지 인코딩이 필요한지 모델팀 확인 (Q9)
     */
    public record StaticInfo(Integer birthYear, Integer ageOfFirstMenarche, String ethnicity) {
    }

    /**
     * 하루치 피처.
     *
     * @param features <b>모델 피처명</b> 기준 44개 (DB 컬럼명 아님).
     *                 결측은 null. 키는 항상 44개 다 존재한다
     */
    public record DayFeatures(LocalDate date, Map<String, Object> features) {
    }
}
