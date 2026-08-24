package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.PredictionJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface PredictionJobRepository extends JpaRepository<PredictionJob, Long> {

    List<PredictionJob> findByUserIdOrderByIdDesc(Long userId);

    /** 파생 삭제는 트랜잭션이 없으면 런타임에 실패하므로 여기서 직접 건다. */
    @Transactional
    @Modifying
    void deleteByUserId(Long userId);
}
