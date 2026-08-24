package com.sixletter.hormone_web_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sixletter.hormone_web_backend.config.ModelProperties;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파이썬 응답 파싱 고정 테스트.
 *
 * <p>계약이 아직 안 굳었다. 모델팀이 사소하게 다른 모양으로 줘도 예측 전체가 실패하면
 * 안 되므로, {@code PythonPredictionClient.parse} 가 <b>어디까지 받아내는지</b>를
 * 여기서 못 박는다. 나중에 계약이 확정되면 그때 좁히면 된다.
 */
class PythonResponseParsingTest {

    private PythonPredictionClient client;

    @BeforeEach
    void setUp() {
        // RestClient 는 parse() 경로에서 쓰이지 않는다
        client = new PythonPredictionClient(null, new ModelProperties());
    }

    @Test
    @DisplayName("합의한 평평한 응답을 그대로 읽는다")
    void flatResponse() {
        var r = client.parse("""
                {"lh": 6.2, "estrogen": 88.6, "pdg": 3.8, "phase": "Fertility",
                 "confidence": 0.87,
                 "contributions": [{"feature": "rmssd", "weight": 0.42, "direction": "down"}],
                 "modelVersion": "v0.3"}
                """);

        assertThat(r.lh()).isEqualByComparingTo("6.2");
        assertThat(r.estrogen()).isEqualByComparingTo("88.6");
        assertThat(r.pdg()).isEqualByComparingTo("3.8");
        assertThat(r.phase()).isEqualTo("Fertility");
        assertThat(r.confidence()).isEqualByComparingTo("0.87");
        assertThat(r.modelVersion()).isEqualTo("v0.3");
        assertThat(r.contributions()).singleElement().satisfies(c -> {
            assertThat(c.feature()).isEqualTo("rmssd");
            assertThat(c.direction()).isEqualTo("down");
        });
        assertThat(r.hasError()).isFalse();
        assertThat(r.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("★ 필수 필드가 없다 — phase 만 와도 파싱된다")
    void phaseOnly() {
        var r = client.parse("{\"phase\": \"Luteal\"}");

        assertThat(r.phase()).isEqualTo("Luteal");
        assertThat(r.lh()).isNull();
        assertThat(r.confidence()).isNull();
        assertThat(r.contributions()).isNull();
        assertThat(r.isEmpty()).isFalse();   // phase 가 있으므로 빈 응답이 아니다
    }

    @Test
    @DisplayName("★ contributions 가 맵으로 와도 받는다 — 부호에서 방향을 유도한다")
    void contributionsAsMap() {
        var r = client.parse("""
                {"phase": "Luteal",
                 "contributions": {"rmssd": 0.42, "sleep_resting_heart_rate": -0.18}}
                """);

        assertThat(r.contributions()).hasSize(2);
        assertThat(r.contributions())
                .filteredOn(c -> c.feature().equals("rmssd"))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.weight()).isEqualByComparingTo("0.42");
                    assertThat(c.direction()).isEqualTo("up");     // 양수 -> up
                });
        assertThat(r.contributions())
                .filteredOn(c -> c.feature().equals("sleep_resting_heart_rate"))
                .singleElement()
                .satisfies(c -> assertThat(c.direction()).isEqualTo("down"));  // 음수 -> down
    }

    @Test
    @DisplayName("★ 옛 계약(중첩)으로 와도 평평하게 끌어낸다")
    void nestedLegacyShape() {
        var r = client.parse("""
                {"hormones": {"lh": {"value": 6.2}, "estrogen": {"value": 88.6}, "pdg": null},
                 "phase": {"label": "Fertility", "confidence": 0.91},
                 "model_version": "legacy-1"}
                """);

        assertThat(r.lh()).isEqualByComparingTo("6.2");
        assertThat(r.estrogen()).isEqualByComparingTo("88.6");
        assertThat(r.pdg()).isNull();
        assertThat(r.phase()).isEqualTo("Fertility");
        assertThat(r.confidence()).isEqualByComparingTo("0.91");   // phase.confidence 에서
        assertThat(r.modelVersion()).isEqualTo("legacy-1");        // snake_case 별칭
    }

    @Test
    @DisplayName("모르는 필드가 섞여 와도 무시하고 아는 것만 읽는다")
    void unknownFieldsIgnored() {
        var r = client.parse("""
                {"lh": 1.5, "phase": "Menstrual",
                 "debug_info": {"seed": 42}, "elapsed_ms": 812, "무슨필드": [1,2,3]}
                """);

        assertThat(r.lh()).isEqualByComparingTo("1.5");
        assertThat(r.phase()).isEqualTo("Menstrual");
    }

    @Test
    @DisplayName("결측은 null 로 온다 — 0 으로 바뀌지 않는다")
    void nullStaysNull() {
        var r = client.parse("{\"lh\": 1.5, \"estrogen\": null, \"pdg\": null, \"phase\": \"Menstrual\"}");

        assertThat(r.estrogen()).isNull();
        assertThat(r.pdg()).isNull();
        assertThat(r.lh()).isNotNull();
    }

    @Test
    @DisplayName("200 안에 에러를 실어 보내도 인식한다")
    void inBandError() {
        var r = client.parse("{\"error\": {\"code\": \"NO_DATA\", \"message\": \"day 999 없음\"}}");

        assertThat(r.hasError()).isTrue();
        assertThat(r.error().code()).isEqualTo("NO_DATA");
        assertThat(r.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("JSON 객체가 아니면 예외 — 조용히 빈 예측으로 넘어가면 안 된다")
    void nonObjectFails() {
        assertThatThrownBy(() -> client.parse("[1, 2, 3]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JSON 객체가 아닙니다");
    }

    @Test
    @DisplayName("일차 오프셋은 설정 하나로 보정된다")
    void dayOffset() {
        ModelProperties p = new ModelProperties();

        assertThat(p.getDayOffset()).isZero();          // 기본값: 정렬이 같다고 가정
        assertThat(p.toModelDay(45)).isEqualTo(45);

        // 파이썬이 2024 구간 처음(852)부터 센다면 10일 밀린다
        p.setDayOffset(10);
        assertThat(p.toModelDay(45)).isEqualTo(55);
    }

    @Test
    @DisplayName("contributions 가 이상한 타입이면 무시하고 나머지는 살린다")
    void weirdContributionsIgnored() {
        var r = client.parse("{\"phase\": \"Luteal\", \"contributions\": \"몰라요\"}");

        assertThat(r.phase()).isEqualTo("Luteal");
        assertThat(r.contributions()).isNull();
    }

    @Test
    @DisplayName("숫자가 문자열로 와도 무시한다 — 조용히 잘못된 값을 만들지 않는다")
    void stringNumbersRejected() {
        var r = client.parse("{\"lh\": \"6.2\", \"phase\": \"Luteal\"}");

        // 문자열을 억지로 파싱하지 않는다. 애매하게 맞히느니 비워 두고 로그로 드러나게 한다
        assertThat(r.lh()).isNull();
        assertThat(r.phase()).isEqualTo("Luteal");
    }

    @Test
    @DisplayName("BigDecimal 정밀도가 유지된다")
    void precisionKept() {
        var r = client.parse("{\"lh\": 0.780, \"phase\": \"Luteal\"}");

        // JSON 컬럼은 스케일을 보존하지 않으므로 equals 가 아니라 compareTo 로 비교한다
        assertThat(r.lh()).isEqualByComparingTo(new BigDecimal("0.78"));
    }
}
