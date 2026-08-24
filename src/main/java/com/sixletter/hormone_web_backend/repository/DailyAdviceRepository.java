package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.DailyAdvice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface DailyAdviceRepository extends JpaRepository<DailyAdvice, Long> {

    /** 같은 날짜를 다시 열 때 재호출하지 않으려고 먼저 본다. */
    Optional<DailyAdvice> findByUserIdAndTargetDate(Long userId, LocalDate targetDate);

    /** 조언 탭 목록. 최신순. */
    List<DailyAdvice> findByUserIdOrderByTargetDateDesc(Long userId);

    /** 파생 삭제는 트랜잭션이 없으면 런타임에 실패하므로 여기서 직접 건다. */
    @Transactional
    @Modifying
    void deleteByUserId(Long userId);
}
