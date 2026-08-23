package com.sixletter.hormone_web_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시연 진행 커서. 프론트의 "하루 넘기기 / Day N 정보 보내기" 버튼이 이 값을 올린다.
 *
 * <p>PK 가 user_id 다 (사용자당 데모 세션 1개). 그래서 별도 id 컬럼이 없다.
 *
 * <p>모델의 상대 시간축(Day 1..N)과 사용자에게 보여줄 절대 날짜를 잇는 지점이기도 하다.
 * {@code startDate} 가 Day 1 에 해당하며, Day N 의 날짜는 {@code startDate + (N-1)일} 이다.
 */
@Entity
@Table(name = "demo_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoSession {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** Day 1 에 해당하는 달력 날짜. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 지금까지 보낸 일차. 0 = 아직 아무것도 안 보냄. */
    @Column(name = "current_day", nullable = false)
    private Integer currentDay;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    /** 이 일수만큼 모아야 예측이 시작된다. 현재 20. */
    @Column(name = "cold_start_days", nullable = false)
    private Integer coldStartDays;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** Day N 에 해당하는 달력 날짜. N 은 1부터 시작한다. */
    public LocalDate dateOfDay(int day) {
        return startDate.plusDays(day - 1L);
    }

    /** 현재 일차의 날짜. 아직 시작 전(0)이면 null. */
    public LocalDate currentDate() {
        return currentDay == null || currentDay < 1 ? null : dateOfDay(currentDay);
    }

    /** 아직 넘길 날이 남았는지. */
    public boolean hasNext() {
        return currentDay < totalDays;
    }

    /**
     * 해당 일차가 콜드스타트 구간인지(= 아직 예측을 보여주지 않는 구간).
     * coldStartDays 가 20 이면 Day 1~19 가 콜드스타트, Day 20 부터 예측이 나온다.
     */
    public boolean isColdStart(int day) {
        return day < coldStartDays;
    }
}
