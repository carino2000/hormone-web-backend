package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.PeriodPrediction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodPredictionRepository extends JpaRepository<PeriodPrediction, Long> {

    // 화면 표시용 : 가장 최근 예측 1건
    Optional<PeriodPrediction> findFirstByUserIdOrderByPredictedAtDesc(Long userId);

    // 변동폭(신뢰도 대용) 계산용 : 최근 N일 전체 예측
    List<PeriodPrediction> findByUserIdAndPredictedAtGreaterThanEqualOrderByPredictedAtDesc(
            Long userId, LocalDateTime since);

    // 회고용 : 특정 날짜 시점에 냈던 예측
    List<PeriodPrediction> findByUserIdAndPredictedOnOrderByPredictedAtDesc(Long userId, LocalDate predictedOn);
}
