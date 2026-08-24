package com.sixletter.hormone_web_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * anthropic.* 설정. "오늘의 조언" 기능이 쓴다.
 *
 * <p><b>API 키는 절대 로그에 남기지 않는다.</b> 이 클래스에 toString 을 만들지 말 것
 * (Lombok {@code @Data} 를 쓰면 자동 생성돼서 새어나간다 — 그래서 Getter/Setter 만 쓴다).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "anthropic")
public class AnthropicProperties {

    /** 비어 있으면 조언 기능이 꺼진다 (호출 자체를 하지 않고 안내 문구를 돌려준다). */
    private String apiKey = "";

    private String model = "claude-sonnet-5";
    private String baseUrl = "https://api.anthropic.com";
    private String messagesPath = "/v1/messages";

    /** Anthropic Messages API 버전 헤더. */
    private String apiVersion = "2023-06-01";

    /**
     * 조언에 실어 보낼 웨어러블 일수. 일차가 늘어도 이 값으로 고정되므로 비용이 일정하다.
     * 30일이면 호출당 약 9천~1만 토큰이다.
     */
    private int historyDays = 30;

    /**
     * 조언 길이 상한.
     *
     * <p>1200 이었을 때 Sonnet 이 정확히 1200 을 채우고 <b>문장 중간에서 잘렸다</b>.
     * 한국어는 글자당 토큰이 커서(대략 1.5) 프롬프트가 요구하는 400자도 600토큰쯤 된다.
     * 모델이 지시보다 길게 쓰는 경우까지 감안해 여유를 둔다.
     * 잘리면 {@code AnthropicClient} 가 경고 로그를 남긴다.
     */
    private int maxTokens = 2000;

    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String messagesUrl() {
        return baseUrl + messagesPath;
    }
}
