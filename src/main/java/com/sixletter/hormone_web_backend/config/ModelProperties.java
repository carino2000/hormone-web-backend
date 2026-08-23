package com.sixletter.hormone_web_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.model.* 설정. 파이썬 예측 서버 주소가 소스에 하드코딩돼 있으면
 * 모델팀 주소가 나올 때마다 재빌드해야 해서 전부 설정으로 뺐다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.model")
public class ModelProperties {

    /** 모델 입력을 어떤 형태로 보낼지. 모델팀 확정 전 기본값은 누적 전체. */
    public enum InputMode {
        /** 그날 하루치 1건만 */
        SINGLE_DAY,
        /** 콜드스타트 이후 누적 전체 */
        FULL_HISTORY,
        /** 최근 windowSize 일치만 */
        WINDOW
    }

    private String baseUrl = "http://127.0.0.1:5000";
    private String predictPath = "/predict";

    /** false 면 MockPredictionClient 가 뜬다 (파이썬 없이 E2E 동작). */
    private boolean enabled = false;

    private InputMode inputMode = InputMode.FULL_HISTORY;
    private int windowSize = 20;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 30000;

    /** 비어 있으면 인증 헤더를 붙이지 않는다. 모델팀 확인 후 방식 확정 필요. */
    private String apiKey = "";

    public String predictUrl() {
        return baseUrl + predictPath;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
