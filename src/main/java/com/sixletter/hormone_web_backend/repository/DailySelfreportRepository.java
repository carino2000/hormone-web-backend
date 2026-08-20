package com.sixletter.hormone_web_backend.repository;

import com.sixletter.hormone_web_backend.entity.DailySelfreport;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySelfreportRepository extends JpaRepository<DailySelfreport, Long> {

    // 특정 날짜에 기록된 자가입력 전체 (하루 여러 번 기록 가능)
    List<DailySelfreport> findByUserIdAndReportedOnOrderByReportedAtDesc(Long userId, LocalDate reportedOn);

    List<DailySelfreport> findByUserIdAndReportedOnBetweenOrderByReportedAtAsc(
            Long userId, LocalDate startOn, LocalDate endOn);
}
