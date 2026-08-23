package com.sixletter.hormone_web_backend.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @Async("taskExecutor") 로 실행되는 백그라운드 작업용 스레드풀.
 * HormonePredictionService.requestPrediction() 이 이걸 사용함.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);   // TODO: 동시 예측 요청량에 맞게 조정
        executor.setMaxPoolSize(4);    // TODO: 동시 예측 요청량에 맞게 조정
        executor.setQueueCapacity(50); // TODO: 큐 초과 시 거부 정책도 필요하면 확인
        executor.setThreadNamePrefix("predict-async-");
        executor.initialize();
        return executor;
    }
}
