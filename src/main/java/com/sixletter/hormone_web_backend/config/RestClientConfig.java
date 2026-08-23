package com.sixletter.hormone_web_backend.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 파이썬 예측 서버 호출용 RestClient.
 *
 * <p><b>타임아웃이 반드시 있어야 한다.</b> 없으면 파이썬이 응답하지 않을 때
 * 비동기 스레드가 무한히 물려서 스레드풀(core 2 / max 4)이 금방 고갈되고,
 * 시연 중 이후 요청이 전부 멈춘다.
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final ModelProperties modelProperties;

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(modelProperties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(modelProperties.getReadTimeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
