package com.sixletter.hormone_web_backend.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sixletter.hormone_web_backend.config.ModelProperties;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictRequest;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 파이썬 예측 서버 호출.
 *
 * <p>응답 파싱은 <b>관대하게</b> 한다 (FAIL_ON_UNKNOWN_PROPERTIES off).
 * 계약이 미확정이라 모르는 필드가 섞여 올 수 있는데, 그것 때문에 예측 전체가
 * 실패하면 안 된다. 모르는 필드는 버려지지 않고 호출부가 raw 문자열을
 * {@code prediction_result.raw_response} 에 따로 보관한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.model.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PythonPredictionClient implements PredictionClient {

    private final RestClient restClient;
    private final ModelProperties properties;

    /**
     * 모델 통신 전용 매퍼. 전역 Jackson 설정을 쓰지 않는 이유가 두 가지 있다.
     *
     * <p><b>1) 결측 피처가 사라지면 안 된다.</b> application.yaml 의
     * {@code jackson.default-property-inclusion: NON_NULL} 은 프론트 응답을 가볍게
     * 하려고 켠 것인데, 그게 모델 요청에도 적용되면 <b>그날 결측인 피처의 키가 통째로
     * 빠진 채</b> 나간다. 그러면 날마다 피처 개수가 달라져서 고정 길이 벡터를 기대하는
     * 모델이 깨진다. 결측률이 최대 69% 라 이건 예외가 아니라 매일 일어나는 일이다.
     * → 여기서는 null 을 그대로 실어 보낸다.
     *
     * <p><b>2) 모르는 응답 필드에 관대해야 한다.</b> 계약이 미확정이라 예상 못 한
     * 필드가 섞여 올 수 있는데, 그것 때문에 예측 전체가 실패하면 안 된다.
     */
    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.ALWAYS))
            .build();

    private final ThreadLocal<String> lastBody = new ThreadLocal<>();

    @Override
    public ModelPredictResponse predict(ModelPredictRequest request) {
        String url = properties.predictUrl();
        log.debug("파이썬 예측 요청: url={} userId={} targetDate={} historySize={}",
                url, request.userId(), request.targetDate(), request.history().size());

        // ★ 객체를 그대로 넘기면 Spring 이 전역 ObjectMapper(NON_NULL)로 직렬화해서
        //   결측 피처의 키가 사라진다. 반드시 전용 매퍼로 문자열을 만들어 보낼 것.
        String payload = mapper.writeValueAsString(toWireFormat(request));

        String body = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    if (properties.hasApiKey()) {
                        // TODO: 인증 헤더 이름을 모델팀과 확정할 것 (지금은 X-API-Key 로 가정)
                        h.set("X-API-Key", properties.getApiKey());
                    }
                })
                .body(payload)
                .retrieve()
                .body(String.class);

        lastBody.set(body);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("예측 서버가 빈 응답을 반환했습니다");
        }
        return mapper.readValue(body, ModelPredictResponse.class);
    }

    /**
     * 요청을 파이썬이 읽기 쉬운 snake_case 로 바꾼다.
     * 자바 record 이름(camelCase)을 그대로 보내면 파이썬 쪽에서 다시 매핑해야 한다.
     */
    private WireRequest toWireFormat(ModelPredictRequest r) {
        var s = r.staticInfo();
        return new WireRequest(
                r.userId(),
                r.targetDate() == null ? null : r.targetDate().toString(),
                new WireStatic(s.birthYear(), s.ageOfFirstMenarche(), s.ethnicity()),
                r.history().stream()
                        .map(d -> new WireDay(d.date().toString(), d.features()))
                        .toList());
    }

    private record WireRequest(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("target_date") String targetDate,
            @JsonProperty("static") WireStatic staticInfo,
            @JsonProperty("history") java.util.List<WireDay> history) {
    }

    private record WireStatic(
            @JsonProperty("birth_year") Integer birthYear,
            @JsonProperty("age_of_first_menarche") Integer ageOfFirstMenarche,
            @JsonProperty("ethnicity") String ethnicity) {
    }

    private record WireDay(
            @JsonProperty("date") String date,
            @JsonProperty("features") java.util.Map<String, Object> features) {
    }

    @Override
    public String name() {
        return "python";
    }

    @Override
    public String lastRawBody() {
        return lastBody.get();
    }
}
