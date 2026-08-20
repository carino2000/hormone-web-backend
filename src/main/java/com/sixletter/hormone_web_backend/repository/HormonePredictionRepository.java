package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.HormonePrediction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HormonePredictionRepository extends JpaRepository<HormonePrediction, Long> {

    // 특정 날짜의 예측 이력 전체 (모델 비교/회고용, model_version 별로 구분 필요)
    List<HormonePrediction> findByUserIdAndTargetOnOrderByPredictedAtDesc(Long userId, LocalDate targetOn);

    // 화면 표시용 : 특정 날짜의 최신 1건
    Optional<HormonePrediction> findFirstByUserIdAndTargetOnOrderByPredictedAtDesc(Long userId, LocalDate targetOn);
}
