package com.sixletter.hormone_web_backend.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 HTTP 호출용 RestClient 빈.
 *
 * <p><b>타임아웃이 반드시 있어야 한다.</b> 없으면 파이썬이 응답하지 않을 때
 * 비동기 스레드가 무한히 물려서 스레드풀(core 2 / max 4)이 금방 고갈되고,
 * 시연 중 이후 요청이 전부 멈춘다.
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final ModelProperties modelProperties;
    private final AnthropicProperties anthropicProperties;

    /** 파이썬 예측 서버용. 기본 빈이라 @Primary 를 준다. */
    @Bean
    @Primary
    public RestClient restClient() {
        return build(modelProperties.getConnectTimeoutMs(), modelProperties.getReadTimeoutMs());
    }

    /**
     * Claude API 용. 예측 서버와 타임아웃이 다르다 —
     * 조언 생성은 1만 토큰을 읽고 1천 토큰을 쓰므로 수십 초가 걸릴 수 있다.
     */
    @Bean("anthropicRestClient")
    public RestClient anthropicRestClient() {
        return build(anthropicProperties.getConnectTimeoutMs(), anthropicProperties.getReadTimeoutMs());
    }

    private RestClient build(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectMs));
        factory.setReadTimeout(Duration.ofMillis(readMs));
        return RestClient.builder().requestFactory(factory).build();
    }
}
