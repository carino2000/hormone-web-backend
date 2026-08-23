package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.PredictionResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface PredictionResultRepository extends JpaRepository<PredictionResult, Long> {

    /** 멱등 저장(upsert)의 진입점. 있으면 mergeFrom 으로 병합, 없으면 새로 저장. */
    Optional<PredictionResult> findByUserIdAndTargetDate(Long userId, LocalDate targetDate);

    /** 호르몬 곡선용. 오름차순이어야 차트가 왼쪽부터 자란다. */
    List<PredictionResult> findByUserIdAndTargetDateBetweenOrderByTargetDateAsc(
            Long userId, LocalDate startDate, LocalDate endDate);

    /** Home 화면의 "오늘의 예측" 카드용. */
    Optional<PredictionResult> findTopByUserIdOrderByTargetDateDesc(Long userId);

    List<PredictionResult> findByUserIdOrderByTargetDateAsc(Long userId);

    /**
     * 데모 초기화용. 해당 사용자 것만 지운다.
     * 파생 삭제는 트랜잭션이 없으면 런타임에 실패하므로 여기서 직접 건다.
     */
    @Transactional
    @Modifying
    void deleteByUserId(Long userId);
}
