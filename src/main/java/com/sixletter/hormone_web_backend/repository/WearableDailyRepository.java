package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.WearableDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WearableDailyRepository extends JpaRepository<WearableDaily, Long> {

    List<WearableDaily> findByUserIdOrderByMeasuredOnDesc(Long userId);

    Optional<WearableDaily> findByUserIdAndMeasuredOn(Long userId, LocalDate measuredOn);

    List<WearableDaily> findByUserIdAndMeasuredOnBetweenOrderByMeasuredOnDesc(
            Long userId, LocalDate startDate, LocalDate endDate);
}
