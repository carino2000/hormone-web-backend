package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.DemoSeedWearable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoSeedWearableRepository extends JpaRepository<DemoSeedWearable, Long> {

    /** "하루 넘기기" 가 다음 일차 데이터를 꺼내는 지점. */
    Optional<DemoSeedWearable> findByUserIdAndDayIndex(Long userId, Integer dayIndex);

    List<DemoSeedWearable> findByUserIdOrderByDayIndexAsc(Long userId);

    long countByUserId(Long userId);
}
