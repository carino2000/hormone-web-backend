package com.sixletter.hormone_web_backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sixletter.hormone_web_backend.config.ModelProperties;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictRequest;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 파이썬 예측 서버 호출.
 *
 * <pre>
 *   POST {base-url}{predict-path}    {"day": 45}
 *   ->  {"lh":6.2,"estrogen":88.6,"pdg":3.8,"phase":"Fertility",
 *        "confidence":0.87,"contributions":[...],"modelVersion":"v0.3"}
 * </pre>
 *
 * <p><b>파싱을 관대하게 한다.</b> 계약이 아직 안 굳어서 모델팀이 어떤 모양으로 줄지
 * 확정되지 않았다. 사소한 형태 차이 때문에 예측 전체가 실패하면 안 되므로,
 * 아래 세 가지를 전부 받아낸다.
 *
 * <ol>
 *   <li><b>모르는 필드</b> — {@code FAIL_ON_UNKNOWN_PROPERTIES} off. 그냥 무시한다
 *       (원문은 호출부가 {@code raw_response} 에 따로 보관하므로 잃지 않는다)</li>
 *   <li><b>{@code contributions} 가 배열이든 맵이든</b> — 아래 normalize 참고</li>
 *   <li><b>중첩된 옛 모양</b> — {@code {"hormones":{"lh":{"value":6.2}}}} 처럼 와도
 *       평평하게 끌어낸다. 모델팀이 예전 계약서를 보고 만들었을 수 있다</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonPredictionClient implements PredictionClient {

    private final RestClient restClient;
    private final ModelProperties properties;

    /**
     * 모델 통신 전용 매퍼.
     *
     * <p>전역 Jackson 설정({@code jackson.default-property-inclusion: NON_NULL})을 쓰지
     * 않는 이유는 <b>응답 파싱을 우리가 통제해야 하기 때문</b>이다. 모르는 필드에서
     * 터지지 않도록 {@code FAIL_ON_UNKNOWN_PROPERTIES} 를 끈다.
     *
     * <p>(요청이 피처 44개였던 시절에는 NON_NULL 이 결측 피처의 키를 통째로 지워서
     * 44개가 40개로 나가는 사고가 났었다. 지금은 요청이 {@code {"day":N}} 하나라
     * 그 위험은 없지만, 매퍼를 분리해 두는 원칙은 유지한다.)
     */
    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ThreadLocal<String> lastBody = new ThreadLocal<>();

    @Override
    public ModelPredictResponse predict(ModelPredictRequest request) {
        String url = properties.predictUrl();
        log.debug("파이썬 예측 요청: url={} day={}", url, request.day());

        String payload = mapper.writeValueAsString(new WireRequest(request.day()));

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
        return parse(body);
    }

    /** 파이썬이 읽기 쉽게 snake_case 로 보낸다. 필드가 하나뿐이라 사실상 형식만 맞추는 것. */
    private record WireRequest(@JsonProperty("day") Integer day) {
    }

    // ------------------------------------------------------------------
    // 관대한 파싱
    // ------------------------------------------------------------------

    /**
     * 응답 JSON → DTO.
     *
     * <p>Jackson 자동 바인딩만 쓰지 않고 트리로 훑는 이유: {@code contributions} 의
     * 모양이 두 가지고, 옛 계약(중첩)으로 올 가능성도 있어서다.
     * 어느 쪽이든 같은 DTO 로 떨어뜨린다.
     */
    ModelPredictResponse parse(String body) {
        JsonNode root = mapper.readTree(body);
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("예측 서버 응답이 JSON 객체가 아닙니다: " + preview(body));
        }

        ModelPredictResponse.ErrorBody error = null;
        JsonNode err = root.get("error");
        if (err != null && err.isObject() && err.hasNonNull("code")) {
            error = new ModelPredictResponse.ErrorBody(
                    text(err, "code"), text(err, "message"));
        }

        ModelPredictResponse parsed = new ModelPredictResponse(
                hormone(root, "lh"),
                hormone(root, "estrogen"),
                hormone(root, "pdg"),
                phase(root),
                confidence(root),
                contributions(root.get("contributions")),
                firstText(root, "modelVersion", "model_version", "version"),
                error);

        if (parsed.isEmpty() && error == null) {
            log.warn("예측 응답에 lh/estrogen/pdg/phase 가 하나도 없습니다. 응답 원문: {}", preview(body));
        }
        return parsed;
    }

    /**
     * 호르몬 값 하나. 세 가지 모양을 받는다.
     * <pre>
     *   "lh": 6.2                          ← 지금 계약
     *   "lh": {"value": 6.2}               ← 옛 계약 잔재
     *   "hormones": {"lh": 6.2 또는 {...}} ← 옛 계약 잔재
     * </pre>
     */
    private BigDecimal hormone(JsonNode root, String key) {
        JsonNode n = root.get(key);
        if (n == null || n.isNull()) {
            JsonNode hormones = root.get("hormones");
            n = hormones == null ? null : hormones.get(key);
        }
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isObject()) {
            n = n.get("value");
        }
        return n == null || n.isNull() || !n.isNumber() ? null : n.decimalValue();
    }

    /**
     * 확신도. 루트에 있는 게 지금 계약이지만, 옛 계약은 {@code phase.confidence} 안에 있었다.
     * 둘 다 본다 — 모델팀이 예전 계약서를 보고 만들었을 수 있다.
     */
    private BigDecimal confidence(JsonNode root) {
        BigDecimal atRoot = decimal(root, "confidence", "phase_confidence", "phaseConfidence");
        if (atRoot != null) {
            return atRoot;
        }
        JsonNode phase = root.get("phase");
        return phase != null && phase.isObject() ? decimal(phase, "confidence") : null;
    }

    /** {@code "phase": "Fertility"} 또는 {@code "phase": {"label": "Fertility"}} */
    private String phase(JsonNode root) {
        JsonNode n = root.get("phase");
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isObject()) {
            JsonNode label = n.get("label");
            return label == null || label.isNull() ? null : label.asString();
        }
        return n.asString();
    }

    /**
     * 기여도. <b>배열과 맵을 둘 다 받는다.</b>
     *
     * <pre>
     *   [{"feature":"rmssd","weight":0.42,"direction":"down"}]   ← 배열
     *   {"rmssd": 0.42, "sleep_resting_heart_rate": -0.18}       ← 맵
     * </pre>
     *
     * 맵으로 오면 <b>부호에서 방향을 유도</b>한다 (양수 up / 음수 down).
     * SHAP 이든 tree importance 든 대개 이 둘 중 하나로 나온다.
     */
    private List<ModelPredictResponse.ContributionDto> contributions(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        List<ModelPredictResponse.ContributionDto> out = new ArrayList<>();

        if (node.isArray()) {
            for (JsonNode c : node) {
                if (!c.isObject()) continue;
                BigDecimal w = decimal(c, "weight", "value", "importance", "shap");
                out.add(new ModelPredictResponse.ContributionDto(
                        firstText(c, "feature", "name", "column"),
                        w,
                        directionOf(text(c, "direction"), w),
                        text(c, "signal")));
            }
        } else if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                JsonNode v = e.getValue();
                if (v == null || !v.isNumber()) continue;
                BigDecimal w = v.decimalValue();
                out.add(new ModelPredictResponse.ContributionDto(
                        e.getKey(), w, directionOf(null, w), null));
            }
        } else {
            log.warn("contributions 형태를 모르겠습니다 (배열/맵이 아님). 무시합니다: {}", node.getNodeType());
            return null;
        }
        return out.isEmpty() ? null : out;
    }

    /** direction 이 명시돼 있으면 그대로, 없으면 weight 부호에서 유도. */
    private String directionOf(String explicit, BigDecimal weight) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        if (weight == null) {
            return null;
        }
        return weight.signum() < 0 ? "down" : "up";
    }

    // ---- JsonNode 헬퍼 ----

    private BigDecimal decimal(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode n = node.get(k);
            if (n != null && n.isNumber()) {
                return n.decimalValue();
            }
        }
        return null;
    }

    private String text(JsonNode node, String key) {
        JsonNode n = node.get(key);
        return n == null || n.isNull() ? null : n.asString();
    }

    private String firstText(JsonNode node, String... keys) {
        for (String k : keys) {
            String v = text(node, k);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String preview(String body) {
        if (body == null) return "(null)";
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
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
