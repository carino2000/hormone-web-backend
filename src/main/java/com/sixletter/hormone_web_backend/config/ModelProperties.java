package com.sixletter.hormone_web_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.model.* 설정. 파이썬 예측 서버 주소가 소스에 하드코딩돼 있으면
 * 모델팀 주소가 나올 때마다 재빌드해야 해서 전부 설정으로 뺐다.
 *
 * <p><b>요청은 일차 정수 하나뿐이다</b> ({@code POST /predict {"day": 45}}).
 * 예전의 {@code inputMode} / {@code windowSize}(히스토리를 몇 일치 실어 보낼지)는
 * 백엔드가 더 이상 피처를 보내지 않으므로 사라졌다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.model")
public class ModelProperties {

    private String baseUrl = "http://127.0.0.1:5000";
    private String predictPath = "/predict";

    /**
     * <b>★ 일차 정렬 보정.</b> 파이썬에 보낼 일차 = 백엔드 Day + dayOffset.
     *
     * <p>백엔드 Day 1 은 시드 기준 {@code day_in_study 862} 인데, 이 참가자
     * ({@code id=22} / {@code 2024}) 의 구간은 {@code 852} 부터 시작한다.
     * 즉 <b>백엔드 Day 1 은 구간의 11번째 행</b>이다.
     *
     * <ul>
     *   <li>파이썬이 <b>우리와 같은 90일 창</b>(862~951)을 1일차부터 센다 → {@code 0}</li>
     *   <li>파이썬이 <b>2024 구간 처음</b>(852)부터 센다 → {@code 10}</li>
     * </ul>
     *
     * <p>모델팀에 확인할 문장: <b>"45를 보내면 day_in_study 몇 번까지를 쓰나요?"</b>
     * {@code 906} 이면 0, {@code 896} 이면 10 이다.
     * 확인 전까지 기본값 0 으로 둔다 — 틀려도 설정 한 줄로 고쳐진다.
     */
    private int dayOffset = 0;

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

    /** 백엔드 일차 → 파이썬에 보낼 일차. */
    public int toModelDay(int backendDay) {
        return backendDay + dayOffset;
    }
}
