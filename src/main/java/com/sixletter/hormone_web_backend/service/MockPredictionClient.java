package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.dto.model.ModelPredictRequest;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictResponse;
import com.sixletter.hormone_web_backend.entity.DemoSeedWearable;
import com.sixletter.hormone_web_backend.entity.DemoSession;
import com.sixletter.hormone_web_backend.repository.DemoSeedWearableRepository;
import com.sixletter.hormone_web_backend.repository.DemoSessionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 파이썬 없이 도는 내장 예측기.
 *
 * <p><b>왜 필요한가</b>
 * <ol>
 *   <li>모델팀 계약이 확정되기 전에도 프론트~백엔드 전 구간을 완성·검증할 수 있다</li>
 *   <li>시연 당일 파이썬이 안 붙어도 데모가 완주된다 (Plan B)</li>
 * </ol>
 *
 * <p><b>어떻게 그럴듯하게 만드는가</b><br>
 * {@code demo_seed_wearable.truth} 에 있는 <b>실측 정답 라벨</b>을 읽어서, 거기에
 * 결정적(deterministic) 오차를 얹어 "예측값"을 만든다. 즉 실제 mcPHASES 참가자의
 * 호르몬 곡선을 따라간다 — 시연에서 LH 서지·황체기 PdG 상승이 진짜로 보인다.
 * 시드가 없으면 28일 주기 모델로 합성한다.
 *
 * <p><b>재현 가능해야 한다.</b> {@code Math.random()} 을 쓰지 않는다 —
 * 발표 중 같은 날짜를 다시 눌렀는데 값이 바뀌면 안 된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.model.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class MockPredictionClient implements PredictionClient {

    private static final String MODEL_VERSION = "mock-v1";

    /** 기여도 문구용 라벨. 모델 피처명 기준(교차 매핑 이후 이름). */
    private static final Map<String, String> SIGNAL_LABEL = Map.of(
            "rmssd", "HRV(rmssd)",
            "nightly_temperature", "야간 피부온도",
            "value", "안정시 심박",
            "overall_score", "수면 점수",
            "steps", "활동량",
            "bpm", "평균 심박"
    );

    private final DemoSeedWearableRepository seedRepository;
    private final DemoSessionRepository sessionRepository;

    @Override
    public ModelPredictResponse predict(ModelPredictRequest request) {
        sleepBriefly(request);

        Long userId = request.userId();
        LocalDate target = request.targetDate();
        int dayIndex = resolveDayIndex(userId, target, request);

        Map<String, Object> truth = seedRepository.findByUserIdAndDayIndex(userId, dayIndex)
                .map(DemoSeedWearable::getTruth)
                .orElse(null);

        ModelPredictResponse.Hormones hormones = truth != null
                ? hormonesFromTruth(truth, userId, dayIndex)
                : hormonesSynthesized(dayIndex);

        String phaseLabel = truth != null && truth.get("phase") != null
                ? String.valueOf(truth.get("phase"))
                : synthesizedPhase(dayIndex);

        log.debug("Mock 예측 생성: userId={} day={} phase={} (시드 truth {})",
                userId, dayIndex, phaseLabel, truth != null ? "사용" : "없음 - 합성");

        return new ModelPredictResponse(
                userId,
                target,
                MODEL_VERSION,
                hormones,
                new ModelPredictResponse.Phase(phaseLabel, confidence(userId, dayIndex), probabilities(phaseLabel)),
                nextPeriod(userId, target, dayIndex),
                contributions(request),
                null);
    }

    // ------------------------------------------------------------------
    // 일차 계산
    // ------------------------------------------------------------------

    /** demo_session 이 있으면 그걸로, 없으면 history 길이로 일차를 추정한다. */
    private int resolveDayIndex(Long userId, LocalDate target, ModelPredictRequest request) {
        Optional<DemoSession> session = sessionRepository.findById(userId);
        if (session.isPresent() && target != null) {
            return (int) ChronoUnit.DAYS.between(session.get().getStartDate(), target) + 1;
        }
        return request.history() == null ? 1 : request.history().size();
    }

    // ------------------------------------------------------------------
    // 호르몬
    // ------------------------------------------------------------------

    /** 실측값 + 결정적 오차(±8% 이내). "예측"이므로 정답과 살짝 달라야 자연스럽다. */
    private ModelPredictResponse.Hormones hormonesFromTruth(Map<String, Object> truth, Long userId, int day) {
        return new ModelPredictResponse.Hormones(
                jitter(truth.get("lh"), userId, day, "lh", 0.08, new BigDecimal("0.85")),
                jitter(truth.get("estrogen"), userId, day, "estrogen", 0.06, new BigDecimal("0.82")),
                // pdg 는 학습 데이터 결측률이 64.7% 라 신뢰도를 의도적으로 낮게 준다
                jitter(truth.get("pdg"), userId, day, "pdg", 0.12, new BigDecimal("0.41")));
    }

    private ModelPredictResponse.HormoneValue jitter(
            Object raw, Long userId, int day, String field, double ratio, BigDecimal confidence) {
        if (raw == null) {
            // 결측은 결측 그대로. 0 으로 채우지 않는다
            return new ModelPredictResponse.HormoneValue(null, confidence);
        }
        BigDecimal base = new BigDecimal(raw.toString());
        double delta = (noise(userId, day, field) * 2 - 1) * ratio;
        BigDecimal value = base.multiply(BigDecimal.valueOf(1 + delta))
                .setScale(3, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
        return new ModelPredictResponse.HormoneValue(value, confidence);
    }

    /** 시드가 없을 때의 합성 곡선. 28일 주기를 가정하되 주기 길이는 상수로 두지 않는다. */
    private ModelPredictResponse.Hormones hormonesSynthesized(int day) {
        int cycleLength = 28;
        int d = ((day - 1) % cycleLength) + 1;
        double ovulation = cycleLength * 0.5;

        // estrogen: 난포기 상승 -> 배란 직전 피크 -> 하강
        double est = 60 + 260 * Math.exp(-Math.pow(d - ovulation, 2) / 18.0)
                + 40 * Math.exp(-Math.pow(d - (ovulation + 7), 2) / 30.0);
        // lh: 평소 낮다가 배란 직전 급상승(서지)
        double lh = 3 + 45 * Math.exp(-Math.pow(d - (ovulation - 1), 2) / 1.2);
        // pdg: 배란 후에만 상승. 그 전에는 아예 결측
        Double pdg = d <= ovulation + 1 ? null : 3 + 22 * Math.exp(-Math.pow(d - (ovulation + 8), 2) / 20.0);

        return new ModelPredictResponse.Hormones(
                new ModelPredictResponse.HormoneValue(round(lh), new BigDecimal("0.72")),
                new ModelPredictResponse.HormoneValue(round(est), new BigDecimal("0.70")),
                new ModelPredictResponse.HormoneValue(pdg == null ? null : round(pdg), new BigDecimal("0.38")));
    }

    private String synthesizedPhase(int day) {
        int d = ((day - 1) % 28) + 1;
        if (d <= 5) return "Menstrual";
        if (d <= 13) return "Follicular";
        if (d <= 21) return "Fertility";
        return "Luteal";
    }

    // ------------------------------------------------------------------
    // phase / 예정일
    // ------------------------------------------------------------------

    private BigDecimal confidence(Long userId, int day) {
        // 0.62 ~ 0.90. 일차가 쌓일수록 조금씩 올라간다 (데이터가 많아지면 확신이 커진다는 서사)
        double growth = Math.min(0.12, day * 0.004);
        return BigDecimal.valueOf(0.62 + growth + noise(userId, day, "conf") * 0.16)
                .setScale(3, RoundingMode.HALF_UP)
                .min(new BigDecimal("0.900"));
    }

    private Map<String, BigDecimal> probabilities(String phaseLabel) {
        List<String> all = List.of("Menstrual", "Follicular", "Fertility", "Luteal");
        Map<String, BigDecimal> probs = new LinkedHashMap<>();
        BigDecimal main = new BigDecimal("0.760");
        BigDecimal rest = new BigDecimal("0.080");
        for (String p : all) {
            probs.put(p, p.equalsIgnoreCase(phaseLabel) ? main : rest);
        }
        return probs;
    }

    /**
     * 다음 월경 예정일.
     *
     * <p><b>시드의 실측 라벨에서 다음 월경 시작일을 찾아</b> 거기에 오차를 얹는다.
     * 주기 길이를 상수로 박으면(예전 코드는 30일 고정) 실측 주기(33일, 31일)와 어긋나서,
     * 화면의 "예측 vs 실측" 비교가 항상 틀린 것처럼 보인다.
     *
     * <p><b>매일 조금씩 흔들리게 만든다.</b> 실제 모델도 누적 데이터로 재계산하므로
     * 반드시 흔들린다. 흔들림이 없으면 프론트의 "변동 안내" UI 를 검증할 수 없다.
     * 다만 예정일이 가까울수록 흔들림과 범위를 좁힌다 — 데이터가 쌓이면 확신이 커진다는 서사.
     */
    private ModelPredictResponse.NextPeriod nextPeriod(Long userId, LocalDate target, int day) {
        if (target == null) {
            return null;
        }

        Integer onsetDay = findNextMenstrualOnset(userId, day);
        if (onsetDay == null) {
            // 시드 범위를 넘어간 경우에만 주기 길이로 추정한다 (실측 33/31일의 중간값)
            onsetDay = day + 32 - ((day - 1) % 32);
        }

        int daysLeft = Math.max(1, onsetDay - day);
        // 멀수록 크게, 가까울수록 작게 흔들린다 (최대 ±3일)
        int maxWobble = Math.min(3, Math.max(0, daysLeft / 7));
        int wobble = maxWobble == 0
                ? 0
                : (int) Math.round((noise(userId, day, "period") * 2 - 1) * maxWobble);

        LocalDate predicted = target.plusDays((long) daysLeft + wobble);
        int spread = Math.max(1, Math.min(5, daysLeft / 5));
        return new ModelPredictResponse.NextPeriod(
                predicted, predicted.minusDays(spread), predicted.plusDays(spread));
    }

    /**
     * {@code day} 이후 첫 월경 시작 일차를 시드 truth 에서 찾는다.
     * 월경 "시작"이므로 직전 날이 Menstrual 이 아닌 첫 Menstrual 날을 고른다.
     *
     * @return 없으면 null (시드 마지막 주기 이후)
     */
    private Integer findNextMenstrualOnset(Long userId, int day) {
        List<DemoSeedWearable> seeds = seedRepository.findByUserIdOrderByDayIndexAsc(userId);
        String previous = null;
        for (DemoSeedWearable seed : seeds) {
            String phase = seed.getTruth() == null ? null : (String) seed.getTruth().get("phase");
            boolean isOnset = "Menstrual".equals(phase) && !"Menstrual".equals(previous);
            if (isOnset && seed.getDayIndex() > day) {
                return seed.getDayIndex();
            }
            previous = phase;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 기여도 — 마지막 날 값이 이력 평균에서 얼마나 벗어났는지로 계산
    // ------------------------------------------------------------------

    private List<ModelPredictResponse.ContributionDto> contributions(ModelPredictRequest request) {
        List<ModelPredictRequest.DayFeatures> history = request.history();
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        Map<String, Object> today = history.get(history.size() - 1).features();

        record Dev(String feature, double deviation, boolean up) {
        }
        List<Dev> devs = new ArrayList<>();

        for (String feature : SIGNAL_LABEL.keySet()) {
            Double todayValue = asDouble(today.get(feature));
            if (todayValue == null) {
                continue;
            }
            // baseline = 그 피처의 이력 평균 (결측 제외)
            List<Double> past = history.stream()
                    .map(d -> asDouble(d.features().get(feature)))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (past.size() < 2) {
                continue;
            }
            double mean = past.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (mean == 0) {
                continue;
            }
            double relative = (todayValue - mean) / Math.abs(mean);
            devs.add(new Dev(feature, Math.abs(relative), relative >= 0));
        }

        double total = devs.stream().mapToDouble(Dev::deviation).sum();
        if (total == 0) {
            return List.of();
        }

        return devs.stream()
                .sorted(Comparator.comparingDouble(Dev::deviation).reversed())
                .limit(4)
                .map(d -> new ModelPredictResponse.ContributionDto(
                        d.feature(),
                        BigDecimal.valueOf(d.deviation() / total).setScale(3, RoundingMode.HALF_UP),
                        d.up() ? "up" : "down",
                        SIGNAL_LABEL.get(d.feature()) + (d.up() ? " 상승" : " 감소")))
                .toList();
    }

    // ------------------------------------------------------------------
    // 유틸
    // ------------------------------------------------------------------

    /**
     * 0.0 ~ 1.0 결정적 의사난수. 같은 (userId, day, field) 면 항상 같은 값이 나온다.
     * Math.random() 을 쓰면 시연 중 같은 날을 다시 눌렀을 때 값이 바뀐다.
     */
    private double noise(Long userId, int day, String field) {
        long h = 1125899906842597L;
        String key = userId + "|" + day + "|" + field;
        for (int i = 0; i < key.length(); i++) {
            h = 31 * h + key.charAt(i);
        }
        return Math.abs((h ^ (h >>> 32)) % 10_000) / 10_000.0;
    }

    private Double asDouble(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }

    /** 비동기 흐름이 실제처럼 보이도록 약간 지연시킨다. 프론트의 로딩 UI 검증에도 필요하다. */
    private void sleepBriefly(ModelPredictRequest request) {
        long ms = 150 + Math.round(noise(request.userId(), request.history().size(), "delay") * 450);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String name() {
        return "mock";
    }
}
