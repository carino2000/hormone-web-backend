package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.config.DemoProperties;
import com.sixletter.hormone_web_backend.dto.DemoAdvanceDto;
import com.sixletter.hormone_web_backend.dto.DemoStateDto;
import com.sixletter.hormone_web_backend.dto.DemoTimelineDto;
import com.sixletter.hormone_web_backend.dto.PredictionDto;
import com.sixletter.hormone_web_backend.dto.PredictionJobDto;
import com.sixletter.hormone_web_backend.entity.DemoSeedWearable;
import com.sixletter.hormone_web_backend.entity.DemoSession;
import com.sixletter.hormone_web_backend.entity.PredictionResult;
import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.entity.WearableDaily;
import com.sixletter.hormone_web_backend.exception.NotFoundException;
import com.sixletter.hormone_web_backend.repository.DemoSeedWearableRepository;
import com.sixletter.hormone_web_backend.repository.DemoSessionRepository;
import com.sixletter.hormone_web_backend.repository.PredictionJobRepository;
import com.sixletter.hormone_web_backend.repository.PredictionResultRepository;
import com.sixletter.hormone_web_backend.repository.UserRepository;
import com.sixletter.hormone_web_backend.repository.WearableDailyRepository;
import com.sixletter.hormone_web_backend.support.WearableFeatures;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연 진행 로직. 프론트의 "Day N 정보 보내기" 버튼이 여기로 들어온다.
 *
 * <pre>
 *   advance()
 *     1. demo_seed_wearable 에서 다음 일차 데이터를 꺼낸다
 *     2. wearable_daily 로 옮긴다  ← "데이터가 하루씩 쌓이는" 과정을 DB 로도 재현
 *     3. demo_session.current_day 를 올린다
 *     4. 콜드스타트 구간이면 여기서 끝 (COLLECTING 이벤트만)
 *        아니면 예측을 비동기로 트리거하고 즉시 202 반환
 * </pre>
 *
 * <p>버튼 연타로 같은 일차가 두 번 들어와도 안전해야 한다 — 유니크 제약 위반으로
 * 500 이 나면 시연이 망가진다. 모든 저장을 upsert 로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService {

    public static final String STATUS_COLLECTING = "collecting";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DONE = "done";

    private final DemoProperties demoProperties;
    private final DemoSessionRepository sessionRepository;
    private final DemoSeedWearableRepository seedRepository;
    private final WearableDailyRepository wearableDailyRepository;
    private final PredictionResultRepository predictionResultRepository;
    private final PredictionJobRepository predictionJobRepository;
    private final UserRepository userRepository;
    private final HormonePredictionService predictionService;

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    @Transactional
    public DemoStateDto getState(Long userId) {
        DemoSession s = requireSession(userId);
        return toStateDto(s);
    }

    private DemoStateDto toStateDto(DemoSession s) {
        return new DemoStateDto(
                s.getUserId(), s.getStartDate(), s.getCurrentDay(), s.currentDate(),
                s.getTotalDays(), s.getColdStartDays(), statusOf(s));
    }

    private String statusOf(DemoSession s) {
        if (s.getCurrentDay() < s.getColdStartDays()) {
            return STATUS_COLLECTING;
        }
        return s.hasNext() ? STATUS_ACTIVE : STATUS_DONE;
    }

    /**
     * Day 1 ~ 현재까지의 전체 스냅샷. 프론트 새로고침 복구용.
     * <b>미래 일차는 내려주지 않는다</b> — 시연에서 예측을 미리 보여주면 안 된다.
     */
    @Transactional
    public DemoTimelineDto getTimeline(Long userId) {
        DemoSession s = requireSession(userId);

        List<WearableDaily> wearables = wearableDailyRepository
                .findByUserIdOrderByMeasuredOnDesc(userId).stream()
                .sorted(Comparator.comparing(WearableDaily::getMeasuredOn))
                .toList();

        Map<LocalDate, PredictionResult> predictionsByDate = new LinkedHashMap<>();
        predictionResultRepository.findByUserIdOrderByTargetDateAsc(userId)
                .forEach(p -> predictionsByDate.put(p.getTargetDate(), p));

        // 실측 정답 라벨. dayIndex 로 찾을 수 있게 미리 모아둔다.
        Map<Integer, Map<String, Object>> truthByDay = new LinkedHashMap<>();
        seedRepository.findByUserIdOrderByDayIndexAsc(userId)
                .forEach(seed -> truthByDay.put(seed.getDayIndex(), seed.getTruth()));

        List<DemoTimelineDto.Day> days = new ArrayList<>();
        for (WearableDaily w : wearables) {
            int dayIndex = (int) java.time.temporal.ChronoUnit.DAYS
                    .between(s.getStartDate(), w.getMeasuredOn()) + 1;
            PredictionResult prediction = predictionsByDate.get(w.getMeasuredOn());

            // ★ 예측이 있는 날만 실측을 내보낸다.
            //   콜드스타트 구간이나 아직 예측이 안 나온 날의 정답을 미리 보내면
            //   프론트가 화면에 그릴 수 있게 되어 시연 스포일러가 된다.
            //   (아직 도달하지 않은 미래 일차는 wearables 자체에 없어서 자연히 제외된다)
            DemoTimelineDto.Truth truth = prediction == null
                    ? null
                    : toTruth(truthByDay.get(dayIndex));

            days.add(new DemoTimelineDto.Day(
                    dayIndex,
                    w.getMeasuredOn(),
                    WearableFeatures.toMap(w),
                    PredictionDto.from(prediction),
                    truth));
        }

        return new DemoTimelineDto(
                userId, s.getStartDate(), s.getCurrentDay(), s.getTotalDays(),
                s.getColdStartDays(), statusOf(s), baselineOf(wearables), days);
    }

    /** 시드의 truth JSON({phase, lh, estrogen, pdg}) → 응답 DTO. 결측은 null 그대로 둔다. */
    private DemoTimelineDto.Truth toTruth(Map<String, Object> truth) {
        if (truth == null) {
            return null;
        }
        return new DemoTimelineDto.Truth(
                truth.get("phase") == null ? null : String.valueOf(truth.get("phase")),
                decimal(truth.get("lh")),
                decimal(truth.get("estrogen")),
                decimal(truth.get("pdg")));
    }

    private java.math.BigDecimal decimal(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 예측 요청 이력. 프론트 "기록" 탭이 각 날짜 옆에 상태/지연/피처개수를 붙이는 데 쓴다.
     *
     * <p>파이썬을 붙인 뒤 값이 이상할 때 이게 1차 진단이다 — 요청이 나가긴 했는지,
     * 몇 초 걸렸는지, 실패했다면 뭐라고 실패했는지, 피처를 44개 다 보냈는지.
     *
     * <p>최신순. payload 원문은 안 싣는다 (PredictionJobDto 주석 참고).
     */
    @Transactional(readOnly = true)
    public List<PredictionJobDto> getJobs(Long userId) {
        return predictionJobRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(PredictionJobDto::from)
                .toList();
    }

    /**
     * 개인 평소값. 지금까지 수집된 값의 평균이다.
     * 호르몬 원시값 대신 "평소 대비"로 번역할 때 프론트가 쓴다.
     */
    private Map<String, Object> baselineOf(List<WearableDaily> wearables) {
        Map<String, Object> baseline = new LinkedHashMap<>();
        if (wearables.isEmpty()) {
            return baseline;
        }
        List<Map<String, Object>> maps = wearables.stream().map(WearableFeatures::toMap).toList();
        for (String column : WearableFeatures.columnNames()) {
            List<Double> values = maps.stream()
                    .map(m -> m.get(column))
                    .filter(java.util.Objects::nonNull)
                    .map(v -> {
                        try {
                            return Double.parseDouble(v.toString());
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
            // 결측뿐인 피처는 baseline 도 없다. 0 으로 채우지 않는다.
            baseline.put(column, values.isEmpty()
                    ? null
                    : Math.round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 1000.0) / 1000.0);
        }
        return baseline;
    }

    // ------------------------------------------------------------------
    // 하루 넘기기
    // ------------------------------------------------------------------

    @Transactional
    public DemoAdvanceDto advance(Long userId) {
        DemoSession session = requireSession(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + userId));

        if (!session.hasNext()) {
            LocalDate date = session.currentDate();
            return new DemoAdvanceDto(userId, session.getCurrentDay(), date, STATUS_DONE,
                    session.getCurrentDay(), session.getColdStartDays(), null,
                    "마지막 날에 도달했습니다. 초기화 후 다시 시작하세요.");
        }

        int nextDay = session.getCurrentDay() + 1;
        LocalDate date = session.dateOfDay(nextDay);

        DemoSeedWearable seed = seedRepository.findByUserIdAndDayIndex(userId, nextDay)
                .orElseThrow(() -> new NotFoundException(
                        "시드 데이터가 없습니다: userId=%d day=%d. db/seed 를 먼저 적재하세요."
                                .formatted(userId, nextDay)));

        // 버튼 연타 대비 upsert. 유니크 제약 위반으로 500 이 나면 시연이 망가진다.
        WearableDaily daily = wearableDailyRepository
                .findByUserIdAndMeasuredOn(userId, date)
                .orElseGet(() -> WearableDaily.builder().user(user).measuredOn(date).build());
        WearableFeatures.applyToEntity(seed.getPayload(), daily);
        wearableDailyRepository.save(daily);

        session.setCurrentDay(nextDay);
        sessionRepository.save(session);

        boolean coldStart = session.isColdStart(nextDay);
        log.info("데모 진행: userId={} day={} date={} ({})",
                userId, nextDay, date, coldStart ? "수집 중" : "예측 트리거");

        if (coldStart) {
            predictionService.notifyCollecting(userId, nextDay, date);
            return new DemoAdvanceDto(userId, nextDay, date, STATUS_COLLECTING,
                    nextDay, session.getColdStartDays(), null,
                    "데이터 수집 중입니다 (%d/%d일)".formatted(nextDay, session.getColdStartDays()));
        }

        predictionService.notifyPending(userId, nextDay, date);
        predictionService.requestPrediction(user, date, nextDay);

        return new DemoAdvanceDto(userId, nextDay, date, STATUS_ACTIVE,
                nextDay, session.getColdStartDays(), null,
                "예측을 요청했습니다. 결과는 곧 도착합니다.");
    }

    // ------------------------------------------------------------------
    // 초기화
    // ------------------------------------------------------------------

    /**
     * 데모를 Day 0 으로 되돌린다.
     * <b>해당 사용자의 데이터만</b> 지운다. 시드({@code demo_seed_wearable})는 남긴다.
     */
    @Transactional
    public DemoStateDto reset(Long userId) {
        DemoSession session = requireSession(userId);

        predictionResultRepository.deleteByUserId(userId);
        predictionJobRepository.deleteByUserId(userId);
        wearableDailyRepository.deleteAll(wearableDailyRepository.findByUserIdOrderByMeasuredOnDesc(userId));

        session.setCurrentDay(0);
        sessionRepository.save(session);

        log.info("데모 초기화: userId={}", userId);
        return toStateDto(session);
    }

    // ------------------------------------------------------------------
    // 세션 확보
    // ------------------------------------------------------------------

    /** 세션이 없으면 설정 기본값으로 만들어 준다 — 시연 직전에 막히면 안 된다. */
    private DemoSession requireSession(Long userId) {
        Optional<DemoSession> existing = sessionRepository.findById(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("사용자를 찾을 수 없습니다: " + userId + ". db/seed 를 먼저 적재하세요.");
        }
        log.info("데모 세션이 없어 기본값으로 생성합니다: userId={}", userId);
        return sessionRepository.save(DemoSession.builder()
                .userId(userId)
                .startDate(demoProperties.getStartDate())
                .currentDay(0)
                .totalDays(demoProperties.getTotalDays())
                .coldStartDays(demoProperties.getColdStartDays())
                .build());
    }
}
