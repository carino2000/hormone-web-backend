package com.sixletter.hormone_web_backend.config;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.demo.* 설정. 시연 시나리오의 기본값이다.
 * 실제 진행 상태는 demo_session 테이블이 들고 있고, 여기 값은 세션을 처음 만들 때만 쓴다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.demo")
public class DemoProperties {

    /** 시연용 단일 가상 사용자. 로그인이 없으므로 고정값이다. */
    private Long userId = 1L;

    /** Day 1 에 해당하는 달력 날짜. */
    private LocalDate startDate = LocalDate.of(2026, 8, 13);

    /** 시드가 있으면 seed_data.json 의 totalDays 가 이긴다. 이 값은 시드 없이 세션을 만들 때만 쓴다. */
    private int totalDays = 90;

    /** 이 일수를 모아야 예측이 시작된다 (Day 1~19 수집, Day 20 부터 예측). */
    private int coldStartDays = 20;
}
