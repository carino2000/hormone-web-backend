package com.sixletter.hormone_web_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 외부(파이썬 예측 서버 등) 호출용 RestClient 빈 등록.
 * 서비스 쪽에서는 이 빈을 주입받아 바로 쓰면 됨.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
