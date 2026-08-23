package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.dto.PredictionDto;
import com.sixletter.hormone_web_backend.dto.PredictionEventDto;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictRequest;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictResponse;
import com.sixletter.hormone_web_backend.entity.Contribution;
import com.sixletter.hormone_web_backend.entity.CyclePhase;
import com.sixletter.hormone_web_backend.entity.JobStatus;
import com.sixletter.hormone_web_backend.entity.PredictionJob;
import com.sixletter.hormone_web_backend.entity.PredictionResult;
import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.entity.WearableDaily;
import com.sixletter.hormone_web_backend.repository.PredictionJobRepository;
import com.sixletter.hormone_web_backend.repository.PredictionResultRepository;
import com.sixletter.hormone_web_backend.repository.WearableDailyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 예측 파이프라인.
 *
 * <pre>
 *   prediction_job 을 PENDING 으로 기록
 *     -> PredictionClient 호출 (파이썬 or Mock)
 *     -> 응답 파싱 -> prediction_result 에 <b>병합 저장</b>
 *     -> job 을 SUCCEEDED 로
 *     -> WebSocket 으로 PREDICTION_READY push
 *   실패하면 job 을 FAILED 로 남기고 PREDICTION_FAILED push
 * </pre>
 *
 * <p>파이썬 호출이 오래 걸릴 수 있어 {@code @Async} 백그라운드로 돈다.
 * 컨트롤러는 호출만 하고 즉시 202 를 반환한다.
 *
 * <p><b>슈퍼셋 가정(규칙 10) 적용 지점</b>
 * <ul>
 *   <li>응답에 없는 필드는 null 로 두고, 기존 행의 값을 <b>지우지 않는다</b>
 *       ({@link PredictionResult#mergeFrom})</li>
 *   <li>알 수 없는 phase 라벨이 와도 예외를 던지지 않고 phase 만 비운다</li>
 *   <li>우리가 매핑하지 않은 필드는 {@code raw_response} 에 통째로 남는다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HormonePredictionService {

    private final PredictionClient predictionClient;
    private final ModelInputBuilder modelInputBuilder;
    private final WearableDailyRepository wearableDailyRepository;
    private final PredictionResultRepository predictionResultRepository;
    private final PredictionJobRepository predictionJobRepository;
    private final SimpMessagingTemplate template;
    private final ObjectMapper objectMapper;

    @Value("${app.ws.prediction-topic-prefix:/topic/prediction/}")
    private String topicPrefix;

    /**
     * 예측을 백그라운드로 실행하고 결과를 WebSocket 으로 보낸다.
     *
     * @param dayInStudy 화면 표시용 일차. 모델 입력으로는 보내지 않는다(엑셀 초록색)
     */
    @Async("taskExecutor")
    public void requestPrediction(User user, LocalDate targetDate, Integer dayInStudy) {
        Long userId = user.getId();
        PredictionJob job = predictionJobRepository.save(PredictionJob.builder()
                .userId(userId)
                .targetDate(targetDate)
                .status(JobStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build());

        try {
            List<WearableDaily> history = wearableDailyRepository
                    .findByUserIdAndMeasuredOnBetweenOrderByMeasuredOnDesc(
                            userId, targetDate.minusYears(2), targetDate);
            if (history.isEmpty()) {
                throw new IllegalStateException("예측할 웨어러블 데이터가 없습니다: " + targetDate);
            }

            ModelPredictRequest request = modelInputBuilder.build(user, history, targetDate);
            job.setRequestPayload(toMap(request));

            ModelPredictResponse response = predictionClient.predict(request);
            if (response == null) {
                throw new IllegalStateException("예측 서버가 null 을 반환했습니다");
            }
            if (response.hasError()) {
                throw new IllegalStateException(
                        "예측 서버 오류: " + response.error().code() + " - " + response.error().message());
            }

            PredictionResult saved = saveResult(user, targetDate, dayInStudy, response);
            job.markSucceeded(predictionClient.lastRawBody());
            predictionJobRepository.save(job);

            log.info("예측 완료: userId={} date={} day={} phase={} client={} ({}ms)",
                    userId, targetDate, dayInStudy, saved.getPhase(), predictionClient.name(), job.getLatencyMs());

            send(PredictionEventDto.ready(userId, dayInStudy, targetDate, PredictionDto.from(saved)));

        } catch (Exception e) {
            log.error("예측 실패: userId={} date={} client={}", userId, targetDate, predictionClient.name(), e);
            job.markFailed(e);
            job.setResponseBody(predictionClient.lastRawBody());
            predictionJobRepository.save(job);

            send(PredictionEventDto.failed(userId, dayInStudy, targetDate,
                    "MODEL_UNAVAILABLE", "예측 서버에 연결할 수 없습니다."));
        }
    }

    /**
     * 예측 결과를 (user, 날짜) 행에 <b>병합 저장</b>한다.
     *
     * <p>덮어쓰기가 아니라 병합인 이유: 호르몬마다 모델이 따로 돌면 응답이 쪼개져 온다.
     * 나중 응답이 앞 응답을 지우면 안 된다.
     */
    @Transactional
    protected PredictionResult saveResult(User user, LocalDate targetDate, Integer dayInStudy,
                                          ModelPredictResponse response) {
        PredictionResult incoming = toEntity(user, targetDate, dayInStudy, response);

        PredictionResult target = predictionResultRepository
                .findByUserIdAndTargetDate(user.getId(), targetDate)
                .orElse(null);

        if (target == null) {
            return predictionResultRepository.save(incoming);
        }
        target.mergeFrom(incoming);
        return predictionResultRepository.save(target);
    }

    private PredictionResult toEntity(User user, LocalDate targetDate, Integer dayInStudy,
                                      ModelPredictResponse r) {
        var b = PredictionResult.builder()
                .user(user)
                .targetDate(targetDate)
                .dayInStudy(dayInStudy)
                .modelVersion(r.modelVersion())
                .rawResponse(toMap(r));

        if (r.hormones() != null) {
            b.lh(value(r.hormones().lh()))
                    .estrogen(value(r.hormones().estrogen()))
                    .pdg(value(r.hormones().pdg()))
                    .lhConfidence(confidence(r.hormones().lh()))
                    .estrogenConfidence(confidence(r.hormones().estrogen()))
                    .pdgConfidence(confidence(r.hormones().pdg()));
        }

        if (r.phase() != null) {
            // 모르는 라벨이 와도 예외를 던지지 않는다. phase 만 비고 나머지는 저장된다.
            CyclePhase parsed = CyclePhase.fromLabel(r.phase().label()).orElse(null);
            if (parsed == null && r.phase().label() != null) {
                log.warn("알 수 없는 phase 라벨 '{}' — phase 를 비우고 나머지만 저장합니다. userId={} date={}",
                        r.phase().label(), user.getId(), targetDate);
            }
            b.phase(parsed)
                    .phaseConfidence(r.phase().confidence())
                    .phaseProbabilities(r.phase().probabilities());
        }

        if (r.nextPeriod() != null) {
            b.nextPeriodDate(r.nextPeriod().predictedDate())
                    .nextPeriodRangeStart(r.nextPeriod().rangeStart())
                    .nextPeriodRangeEnd(r.nextPeriod().rangeEnd());
        }

        if (r.contributions() != null && !r.contributions().isEmpty()) {
            b.contributions(r.contributions().stream()
                    .map(c -> new Contribution(c.feature(), c.weight(), c.direction(), c.signal()))
                    .toList());
        }

        return b.build();
    }

    private BigDecimal value(ModelPredictResponse.HormoneValue v) {
        return v == null ? null : v.value();
    }

    private BigDecimal confidence(ModelPredictResponse.HormoneValue v) {
        return v == null ? null : v.confidence();
    }

    /** 콜드스타트 구간에서 "아직 수집 중"임을 프론트에 알린다. */
    public void notifyCollecting(Long userId, int day, LocalDate date) {
        send(PredictionEventDto.collecting(userId, day, date));
    }

    public void notifyPending(Long userId, int day, LocalDate date) {
        send(PredictionEventDto.pending(userId, day, date));
    }

    /** WebSocket push. 실패해도 예측 자체는 이미 DB 에 있으므로 로그만 남기고 삼킨다. */
    private void send(PredictionEventDto event) {
        try {
            template.convertAndSend(topicPrefix + event.userId(), event);
        } catch (Exception e) {
            log.warn("WebSocket push 실패 (예측 결과는 DB 에 저장됨): userId={} type={}",
                    event.userId(), event.type(), e);
        }
    }

    /**
     * 로그/보관용 JSON 변환.
     *
     * <p>주입받은 전역 ObjectMapper 를 쓰면 안 된다. application.yaml 의
     * {@code jackson.default-property-inclusion: NON_NULL} 때문에 <b>결측 피처가
     * 통째로 사라진 채</b> 기록되어, prediction_job 을 보고도 "실제로 뭘 보냈는지"를
     * 알 수 없게 된다. 그래서 null 을 남기는 전용 매퍼를 쓴다.
     */
    private static final ObjectMapper LOG_MAPPER = tools.jackson.databind.json.JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl ->
                    incl.withValueInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS))
            .build();

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object o) {
        try {
            return LOG_MAPPER.convertValue(o, Map.class);
        } catch (Exception e) {
            log.warn("JSON 변환 실패 — 해당 필드는 비워 둡니다: {}", e.getMessage());
            return null;
        }
    }
}
