package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.entity.WearableDaily;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * WearableDaily 데이터를 파이썬 예측 서버로 전달하는 서비스.
 *
 * <p>데이터 출처(사용자 요청 바디에서 바로 만든 WearableDaily 인지, DB에서 조회한
 * WearableDaily 인지)는 이 서비스가 신경쓰지 않는다. 호출하는 쪽에서 WearableDaily
 * 객체 하나만 만들어서 {@link #requestPrediction(Long, WearableDaily)} 에 넘기면 된다.
 *
 * <p>파이썬 서버 호출이 오래 걸릴 수 있어 LongTaskService 와 같은 패턴으로
 * @Async 백그라운드 실행 + 완료 시 웹소켓 push 방식을 사용한다. 즉 컨트롤러는
 * 호출만 하고 바로 응답을 반환하며, 실제 예측 결과/실패는 나중에
 * "/topic/prediction/{userId}" 구독자에게 비동기로 전달된다.
 *
 * ============================================================================
 * [ 아래 항목은 실제 연동 전에 반드시 직접 확인/수정할 것 ]
 *
 *   1) PYTHON_PREDICT_URL - 실제 파이썬 서버 주소/포트/경로로 교체
 *   2) PREDICTION_TOPIC_PREFIX - 프론트와 합의된 실제 웹소켓 토픽 규칙인지 확인
 *      (config/WebSocketConfig 의 엔드포인트 경로("/ws")도 함께 확인)
 *   3) 응답 파싱 부분 (.body(String.class)) - 지금은 러프하게 String 하나로만 받음.
 *      실제 응답 JSON 구조 확정되면 정제(파싱) 로직 + 전용 응답 DTO로 교체 필요.
 *   4) 정적 피처 3개(birth_year, age_of_first_menarche, ethnicity) 미포함
 *      - 이번에 전달받은 컬럼 목록(요청 파라미터)에는 없어서 이 서비스에서는 뺐음.
 *        모델이 실제로 이 3개를 함께 요구한다면 users 테이블과 조인해서
 *        toModelFeatures() 결과 Map 에 추가로 채워야 함.
 *   5) 예측 결과/실패 여부 DB 저장 로직 미구현 (TODO 표시된 지점에서 작업 예정)
 *      - 지금은 웹소켓 push만 함. 저장할 테이블/컬럼 설계 후 requestPrediction() 안의
 *        TODO 위치에 정제 + 저장 코드 추가.
 *   6) 인증 방식(API Key, 사설망 등) 필요 여부 확인 - 현재 헤더 없음.
 * ============================================================================
 */
@Service
@RequiredArgsConstructor
public class HormonePredictionService {

    // ---------------------------------------------------------------------
    // ↓↓↓ 확인 필요: 파이썬 예측 서버 주소 / 웹소켓 토픽 규칙 ↓↓↓
    // RestClient 빈 자체는 config/RestClientConfig 에서 등록함.
    // ---------------------------------------------------------------------
    private static final String PYTHON_PREDICT_URL = "http://127.0.0.1:5000/predict"; // TODO: 실제 주소/포트/경로로 교체
    private static final String PREDICTION_TOPIC_PREFIX = "/topic/prediction/";        // TODO: 프론트와 합의된 토픽 규칙으로 교체
    // ---------------------------------------------------------------------

    private final RestClient restClient;
    private final SimpMessagingTemplate template;

    /**
     * WearableDaily 데이터를 모델 피처명으로 변환해 파이썬 서버에 예측 요청을 보낸다.
     * 백그라운드(taskExecutor)에서 실행되며, 완료/실패 결과는 이 메서드의 리턴값이 아니라
     * "/topic/prediction/{userId}" 웹소켓 push 로 전달된다. 컨트롤러는 이 메서드를
     * 호출한 뒤 바로 응답(예: 202 Accepted)을 반환하면 됨.
     */
    @Async("taskExecutor")
    public void requestPrediction(Long userId, WearableDaily data) {
        Map<String, Object> requestBody = toModelFeatures(data);

        try {
            // 러프하게 응답을 String 하나로 받는다고 가정. 실제 응답 구조(JSON/필드) 확정되면 DTO로 교체.
            String result = restClient.post()
                    .uri(PYTHON_PREDICT_URL)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // TODO: 여기서 result 정제(파싱) + DB 저장 로직 작성 예정 (본인 작업)

            template.convertAndSend(PREDICTION_TOPIC_PREFIX + userId, result);
        } catch (Exception e) {
            // TODO: 로깅 프레임워크(slf4j 등)로 교체 권장 - 지금은 콘솔 출력만 함
            e.printStackTrace();
            // TODO: 실패 케이스도 DB에 남길지 결정 후 저장 로직 작성 예정 (본인 작업)
            template.convertAndSend(
                    PREDICTION_TOPIC_PREFIX + userId,
                    (Object) Map.of("status", "FAILED", "message", e.getMessage()));
        }
    }

    /**
     * DB 컬럼명 -> 모델이 학습된 원본 CSV 피처명으로 변환.
     * 순서와 키 이름은 모델 학습 시 사용한 피처 목록 그대로 맞춤.
     *
     * TODO: 지금은 Map으로 임시 변환. DTO 또는 별도 엔티티로 교체 예정 (본인 작업)
     */
    private Map<String, Object> toModelFeatures(WearableDaily d) {
        Map<String, Object> f = new LinkedHashMap<>();

        f.put("sedentary", d.getSedentary());
        f.put("lightly", d.getLightly());
        f.put("moderately", d.getModerately());
        f.put("very", d.getVery());
        f.put("FAT_BURN", d.getFatBurn());
        f.put("CARDIO", d.getCardio());
        f.put("PEAK", d.getPeak());
        f.put("altitude", d.getAltitude());
        f.put("calories", d.getCalories());
        f.put("temperature_samples", d.getTemperatureSamples());
        f.put("nightly_temperature", d.getNightlyTemperature());
        f.put("filtered_demographic_vo2_max", d.getFilteredDemographicVo2Max());
        f.put("spo2_variation_std", d.getSpo2VariationStd());
        f.put("originalduration", d.getOriginalduration());
        f.put("averageheartrate", d.getAverageheartrate());
        f.put("exercise_calories", d.getExerciseCalories());
        f.put("steps", d.getSteps());
        f.put("glucose_mean", d.getGlucoseMean());
        f.put("glucose_std", d.getGlucoseStd());
        f.put("bpm", d.getBpm());
        f.put("bpm_min", d.getBpmMin());
        f.put("bpm_max", d.getBpmMax());
        f.put("rmssd", d.getRmssd());
        f.put("low_frequency", d.getLowFrequency());
        f.put("high_frequency", d.getHighFrequency());
        f.put("full_sleep_breathing_rate", d.getFullSleepBreathingRate());
        f.put("deep_sleep_breathing_rate", d.getDeepSleepBreathingRate());
        f.put("light_sleep_breathing_rate", d.getLightSleepBreathingRate());
        f.put("rem_sleep_breathing_rate", d.getRemSleepBreathingRate());

        // ※ 이름이 서로 엇갈리는 필드 (WearableDaily 클래스 주석 참고) - 헷갈리기 쉬우니 재확인할 것
        f.put("value", d.getRestingHeartRate());                    // DB: resting_heart_rate       -> 모델: value
        f.put("resting_heart_rate", d.getSleepRestingHeartRate());  // DB: sleep_resting_heart_rate -> 모델: resting_heart_rate

        f.put("minutesasleep", d.getMinutesasleep());
        f.put("efficiency", d.getEfficiency());
        f.put("minutesawake", d.getMinutesawake());
        f.put("nap_minutes_total", d.getNapMinutesTotal());
        f.put("overall_score", d.getOverallScore());
        f.put("deep_sleep_in_minutes", d.getDeepSleepInMinutes());
        f.put("restlessness", d.getRestlessness());
        f.put("stress_score", d.getStressScore());
        f.put("in_default_zone_3", d.getInDefaultZone3());
        f.put("in_default_zone_2", d.getInDefaultZone2());
        f.put("in_default_zone_1", d.getInDefaultZone1());
        f.put("below_default_zone_1", d.getBelowDefaultZone1());
        f.put("temperature_diff_from_baseline", d.getTemperatureDiffFromBaseline());

        return f;
    }
}
