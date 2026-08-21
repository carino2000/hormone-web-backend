package com.sixletter.hormone_web_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 브로커 설정. HormonePredictionService 가 SimpMessagingTemplate 으로
 * 예측 결과를 "/topic/..." 구독자에게 push 할 때 이 브로커를 탄다.
 *
 * ↓↓↓ 확인 필요 ↓↓↓
 *   - 프론트가 접속할 엔드포인트 경로("/ws")가 실제 합의된 경로인지 확인
 *   - setAllowedOriginPatterns("*") 는 개발용. 운영 배포 전 실제 프론트 도메인으로 제한할 것
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws") // TODO: 실제 웹소켓 접속 경로 확인
                .setAllowedOriginPatterns("*"); // TODO: 운영 환경 CORS 정책으로 교체
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
