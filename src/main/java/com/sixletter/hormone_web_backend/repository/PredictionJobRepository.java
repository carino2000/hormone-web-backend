package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.JobStatus;
import com.sixletter.hormone_web_backend.entity.PredictionJob;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface PredictionJobRepository extends JpaRepository<PredictionJob, Long> {

    /** 시연 중 "방금 그 요청 어떻게 됐지?" 를 확인하는 용도. */
    Optional<PredictionJob> findTopByUserIdAndTargetDateOrderByIdDesc(Long userId, LocalDate targetDate);

    List<PredictionJob> findByUserIdOrderByIdDesc(Long userId);

    List<PredictionJob> findByStatusOrderByIdDesc(JobStatus status);

    /** 파생 삭제는 트랜잭션이 없으면 런타임에 실패하므로 여기서 직접 건다. */
    @Transactional
    @Modifying
    void deleteByUserId(Long userId);
}
