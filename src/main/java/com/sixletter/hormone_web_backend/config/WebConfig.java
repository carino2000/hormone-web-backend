package com.sixletter.hormone_web_backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * REST 용 CORS. 지금까지 CORS 설정이 WebSocket 쪽에만 있어서
 * 프론트가 백엔드(:8085)를 호출하면 브라우저가 전부 막았다.
 *
 * <p><b>★ 포트를 못 박지 않고 {@code http://localhost:*} 패턴을 쓴다.</b>
 * Vite 는 지정 포트가 이미 쓰이면 <b>말없이 다음 포트로 옮겨간다.</b> 이 환경에서는
 * 5173 을 Windows 서비스(IP Helper)가 점유하고 있어서 5174 로 가고, 그것도 쓰이면
 * 5175 로 간다. 그때마다 여기에 포트를 추가하지 않으면 화면이 통째로 안 뜨는데,
 * 증상이 <b>CORS 403</b> 이라 "백엔드가 죽었나?" 로 오해하기 쉽다. 실제로 두 번 겪었다.
 *
 * <p>{@code allowedOrigins} 가 아니라 {@code allowedOriginPatterns} 를 쓰는 이유:
 * 전자는 와일드카드를 못 받는다.
 *
 * <p>TODO: 운영 배포 시 {@code app.cors.allowed-origins} 를 실제 도메인으로 제한할 것.
 * 시연용 PoC 라 로컬 전체를 열어 둔다(README §1 — 배포·보안은 범위 밖).
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
