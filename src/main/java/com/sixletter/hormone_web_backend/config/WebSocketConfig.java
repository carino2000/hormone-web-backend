package com.sixletter.hormone_web_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 브로커. 예측 결과가 {@code /topic/prediction/{userId}} 로 push 된다.
 *
 * <p>SockJS 는 켜지 않는다 — 프론트가 네이티브 WebSocket(@stomp/stompjs)으로 붙는다.
 * 켜려면 프론트에 sockjs-client 도 같이 추가해야 하므로 양쪽을 함께 바꿀 것.
 *
 * <p>TODO: 운영 배포 전 setAllowedOriginPatterns 를 실제 도메인으로 제한할 것.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.ws.endpoint:/ws}")
    private String endpoint;

    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
