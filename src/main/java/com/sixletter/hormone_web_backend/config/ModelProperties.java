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

    /**
     * 예측 경로. <b>모델팀 실제 서버는 {@code GET /api/predict?days=N} 이다</b>
     * (hormone-web-model/app.py). 예전 계약서의 {@code POST /predict} 가 아니다.
     */
    private String predictPath = "/api/predict";

    /** 헬스체크. {@code {"status":"ok","loaded_predictors":[...]}} 를 돌려준다. */
    private String healthPath = "/api/health";

    /**
     * 일차를 실어 보낼 쿼리 파라미터 이름.
     *
     * <p><b>이름은 {@code days} 로 유지되고 의미만 바뀌었다.</b> 모델팀이 "1일치만 반환"을
     * 수용했지만 파라미터 이름은 기존 {@code days} 를 그대로 쓴다.
     * 예전엔 "1~N일 전체", 지금은 <b>"N일차 하나"</b> 다.
     *
     * <p>⚠️ 이름이 그대로라서 <b>의미가 바뀐 걸 URL 만 보고는 알 수 없다.</b>
     * 모델팀 배포 후 응답이 배열이면 예전 의미로 도는 것이니 확인할 것
     * (그 경우 {@link PythonPredictionClient#parse} 가 값을 못 찾아 전부 null 이 된다).
     *
     * <p>이름이 {@code day} 로 바뀌면 {@code MODEL_DAY_PARAM=day} 로 띄운다. 재빌드 불필요.
     */
    private String dayParam = "days";

    /**
     * <b>★ 일차 정렬 보정.</b> 파이썬에 보낼 일차 = 백엔드 Day + dayOffset.
     *
     * <p><b>지금은 0 이 정답이다. 시드를 모델에 맞춰 잘랐기 때문이다.</b>
     * 모델은 {@code predictors_base.STUDY_START_DAY_IN_STUDY = 869} 를 day_index 1 로 삼는데,
     * 예전 시드는 {@code day_in_study 862} 부터라 7일이 어긋나 있었다(그때는 -7 이 필요했다).
     * 시드에서 앞 7일을 잘라내 <b>웹 Day N == 모델 day_index N</b> 이 되도록 맞췄다.
     * 그 결과 시드는 90일에서 <b>83일</b>(869~951)이 됐다.
     *
     * <p><b>검증:</b> 모델 응답의 {@code actual} 을 시드 {@code truth} 와 전수 대조해
     * 4개 모델 314건 전부 일치하는 것을 확인했다(LH 서지 35.9 → 양쪽 다 21일차).
     *
     * <p>그래도 이 값을 남겨두는 이유: 모델팀이 기준일을 바꾸면 다시 필요해진다.
     * 그때는 재빌드 없이 설정 한 줄로 흡수한다. 기준일이 바뀌면 값이 그럴듯하게 나오면서
     * 날짜만 틀리므로 <b>조용히 어긋난다</b> — 모델팀에 변경 시 통보를 요청해 뒀다.
     */
    private int dayOffset = 0;

    /**
     * <b>모델이 {@code modelVersion} 을 안 줄 때 대신 쓸 이름.</b> 화면 헤더 배지에 그대로 뜬다.
     *
     * <p>모델팀 응답 계약에 버전 필드가 없어서 배지가 영구 공백이 되는 걸 막으려고 둔다.
     * <b>모델이 값을 주면 그쪽이 이긴다</b> — 여기 값은 어디까지나 폴백이다.
     *
     * <p>⚠️ 이건 우리가 붙이는 이름이라 <b>모델이 실제로 바뀌어도 자동으로 안 바뀐다.</b>
     * 모델이 교체되면 이 값도 같이 손으로 고칠 것. 재빌드 없이 환경변수로 덮을 수 있다.
     */
    private String fallbackVersion = "python-v1";

    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 30000;

    /** 비어 있으면 인증 헤더를 붙이지 않는다. 모델팀 확인 후 방식 확정 필요. */
    private String apiKey = "";

    public String predictUrl() {
        return baseUrl + predictPath;
    }

    public String healthUrl() {
        return baseUrl + healthPath;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 백엔드 일차 → 파이썬에 보낼 일차. */
    public int toModelDay(int backendDay) {
        return backendDay + dayOffset;
    }
}
