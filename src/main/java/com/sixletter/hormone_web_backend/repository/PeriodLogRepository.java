package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.PeriodLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodLogRepository extends JpaRepository<PeriodLog, Long> {

    List<PeriodLog> findByUserIdOrderByStartedOnDesc(Long userId);

    // 예측 정확도 평가 정답값 : 가장 최근 시작일
    Optional<PeriodLog> findFirstByUserIdOrderByStartedOnDesc(Long userId);

    // 진행 중인 월경 (ended_on 이 아직 없는 기록)
    Optional<PeriodLog> findFirstByUserIdAndEndedOnIsNullOrderByStartedOnDesc(Long userId);
}
