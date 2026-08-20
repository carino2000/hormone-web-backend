package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.DailyFeature;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyFeatureRepository extends JpaRepository<DailyFeature, Long> {

    List<DailyFeature> findByUserIdAndObservedOnBetweenOrderByObservedAtAsc(
            Long userId, LocalDate startOn, LocalDate endOn);

    // 하루 대표값 : observed_on 으로 묶고 observed_at 최신 1건
    Optional<DailyFeature> findFirstByUserIdAndObservedOnOrderByObservedAtDesc(Long userId, LocalDate observedOn);

    // 데이터 신선도 확인용 최신 동기화 시각
    Optional<DailyFeature> findFirstByUserIdOrderByDeviceSyncedAtDesc(Long userId);

    // 콜드스타트 진행률 : COUNT(DISTINCT observed_on)
    @Query("SELECT COUNT(DISTINCT d.observedOn) FROM DailyFeature d "
            + "WHERE d.user.id = :userId AND d.validDay = true")
    long countValidDays(@Param("userId") Long userId);
}
