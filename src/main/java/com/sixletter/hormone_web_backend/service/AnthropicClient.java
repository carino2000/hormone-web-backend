package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.config.AnthropicProperties;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude Messages API 호출.
 *
 * <pre>
 *   POST https://api.anthropic.com/v1/messages
 *   headers: x-api-key, anthropic-version, content-type
 *   body: {"model": ..., "max_tokens": ..., "system": ..., "messages": [{"role":"user","content":...}]}
 * </pre>
 *
 * <p><b>API 키를 절대 로그에 남기지 않는다.</b> 요청 본문·헤더를 통째로 찍는 로그를
 * 추가하지 말 것.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicClient {

    private final AnthropicProperties properties;

    @Qualifier("anthropicRestClient")
    private final RestClient restClient;

    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * 응답 한 건.
     *
     * @param truncated max_tokens 에 걸려 <b>문장 중간에서 잘린</b> 응답인지.
     *                  잘린 걸 그대로 화면에 띄우면 말이 끊긴 채로 보인다.
     */
    public record Reply(String text, String model, Integer inputTokens, Integer outputTokens,
                        boolean truncated) {
    }

    /**
     * @param system 시스템 프롬프트 (역할·범위·안전 경계)
     * @param user   사용자 메시지 (데이터 + 요청)
     * @throws IllegalStateException 키가 없거나 응답이 비었을 때
     */
    public Reply complete(String system, String user) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY 가 설정되지 않았습니다. application-local.yaml 의 anthropic.api-key 를 채우세요.");
        }

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)));

        String raw = restClient.post()
                .uri(properties.messagesUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", properties.getApiKey())
                .header("anthropic-version", properties.getApiVersion())
                .body(mapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Claude 가 빈 응답을 반환했습니다");
        }
        return parse(raw);
    }

    /**
     * 응답에서 텍스트와 토큰 사용량을 꺼낸다.
     *
     * <pre>
     * {"content":[{"type":"text","text":"..."}],
     *  "model":"claude-sonnet-5",
     *  "usage":{"input_tokens":9123,"output_tokens":412}}
     * </pre>
     */
    Reply parse(String raw) {
        JsonNode root = mapper.readTree(raw);
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Claude 응답이 JSON 객체가 아닙니다");
        }

        JsonNode error = root.get("error");
        if (error != null && error.isObject()) {
            String type = error.path("type").asString();
            String message = error.path("message").asString();
            throw new IllegalStateException("Claude 오류: " + type + " - " + message);
        }

        StringBuilder text = new StringBuilder();
        JsonNode content = root.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asString())) {
                    text.append(block.path("text").asString());
                }
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Claude 응답에 텍스트 블록이 없습니다");
        }

        JsonNode usage = root.get("usage");
        // stop_reason 이 "max_tokens" 면 문장 중간에서 잘린 것이다.
        boolean truncated = "max_tokens".equals(root.path("stop_reason").asString());
        if (truncated) {
            log.warn("조언이 max_tokens({})에 걸려 잘렸습니다. anthropic.max-tokens 를 올리거나 "
                    + "프롬프트의 분량 지시를 조이세요.", properties.getMaxTokens());
        }
        return new Reply(
                text.toString().trim(),
                root.path("model").asString(properties.getModel()),
                usage == null ? null : intOrNull(usage, "input_tokens"),
                usage == null ? null : intOrNull(usage, "output_tokens"),
                truncated);
    }

    private Integer intOrNull(JsonNode node, String key) {
        JsonNode n = node.get(key);
        return n != null && n.isNumber() ? n.intValue() : null;
    }
}
