package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.config.AnthropicProperties;
import com.sixletter.hormone_web_backend.entity.AdviceStatus;
import com.sixletter.hormone_web_backend.entity.DailyAdvice;
import com.sixletter.hormone_web_backend.entity.DemoSession;
import com.sixletter.hormone_web_backend.entity.PredictionResult;
import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.entity.WearableDaily;
import com.sixletter.hormone_web_backend.exception.NotFoundException;
import com.sixletter.hormone_web_backend.repository.DailyAdviceRepository;
import com.sixletter.hormone_web_backend.repository.DemoSessionRepository;
import com.sixletter.hormone_web_backend.repository.PredictionResultRepository;
import com.sixletter.hormone_web_backend.repository.UserRepository;
import com.sixletter.hormone_web_backend.repository.WearableDailyRepository;
import com.sixletter.hormone_web_backend.support.WearableFeatures;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * "오늘의 조언" 생성. 최근 N일 웨어러블 + 저장된 예측을 Claude 에 보내고 결과를 저장한다.
 *
 * <p><b>하루 1건이고 멱등하다.</b> 이미 성공한 날은 재호출하지 않고 저장된 걸 돌려준다 —
 * 호출당 1만 토큰이라 탭을 옮길 때마다 다시 부르면 비용이 그대로 곱해진다.
 *
 * <p><b>★ 실측 정답(truth)은 보내지 않는다.</b> 실서비스에는 정답이 없다. 정답을 보여주면
 * "조언이 정확한" 것처럼 보이지만 그건 시연용 눈속임이고, 실제 동작과 달라진다.
 * 모델이 보는 건 <b>웨어러블 + 우리 예측</b>뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdviceService {

    /**
     * 시스템 프롬프트 — 역할과 <b>안전 경계</b>.
     *
     * <p>여성 건강 앱이라 선을 넘기 쉽다. 화면의 안전 고지(의료 진단 아님 / 피임·임신 목적
     * 사용 금지)와 같은 선을 프롬프트에서도 지킨다. 이 문단을 약화시키지 말 것 —
     * 대표 시연에서 모델이 진단·투약을 말하면 그 자리에서 신뢰가 무너진다.
     */
    private static final String SYSTEM_PROMPT = """
            당신은 웨어러블 데이터를 읽고 생활 습관을 짚어 주는 건강 도우미입니다.
            의사가 아니며, 진단하지 않습니다.

            [할 것]
            - 제공된 데이터에서 실제로 보이는 변화만 이야기합니다. 수치를 인용하세요.
            - 주기 단계(월경기/난포기/가임기/황체기)와 신호의 관계를 설명합니다.
            - 수면·활동·스트레스 관리처럼 일반적인 자기관리 제안을 합니다.
            - 데이터가 없거나 부족하면 "이 부분은 데이터가 없어 말할 수 없다"고 밝힙니다.

            [절대 하지 말 것]
            - 질병을 진단하거나 진단명을 추측하지 않습니다.
            - 약·영양제 복용을 권하거나 용량을 말하지 않습니다.
            - 피임이나 임신 시도에 이 정보를 쓰라고 하지 않습니다. 그 목적에 쓸 수 없는 정보입니다.
            - "병원에 가야 한다 / 갈 필요 없다"고 판단하지 않습니다.
              걱정되는 증상이 있으면 전문가와 상의하라고만 안내합니다.
            - 데이터에 없는 사실을 지어내지 않습니다. 호르몬 수치는 '예측값'이지 측정값이 아닙니다.

            [형식]
            - 한국어. 존댓말이되 격식체는 쓰지 않습니다.
            - 소제목·목록 없이 2~3문단. **전체 400자를 넘기지 마세요.**
            - 첫 문장에 오늘 상태를 한 줄로 요약합니다.
            - 마지막 문장은 반드시 완결해서 끝냅니다.
            """;

    private final AnthropicProperties properties;
    private final AnthropicClient anthropicClient;
    private final DailyAdviceRepository adviceRepository;
    private final WearableDailyRepository wearableDailyRepository;
    private final PredictionResultRepository predictionResultRepository;
    private final DemoSessionRepository sessionRepository;
    private final UserRepository userRepository;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    /** 조언 탭 목록. 최신순. */
    @Transactional(readOnly = true)
    public List<DailyAdvice> list(Long userId) {
        return adviceRepository.findByUserIdOrderByTargetDateDesc(userId);
    }

    /**
     * 오늘 날짜의 조언을 얻는다. 이미 성공한 게 있으면 <b>재호출하지 않고</b> 그걸 돌려준다.
     *
     * <p><b>★ 트랜잭션을 걸지 않는다.</b> 이 메서드 안에서 Claude 호출이 15~20초 걸리는데,
     * {@code @Transactional} 을 붙이면 그동안 DB 커넥션을 붙잡고 있게 된다.
     * 기본 풀이 10개라 조언 요청이 겹치면 커넥션이 마른다.
     * 읽기와 저장은 각 리포지토리 호출이 자체 트랜잭션으로 처리한다 —
     * 하루 1건 upsert 라 원자성이 필요한 구간이 없고, 동시 삽입은
     * {@code uk_advice_user_date} 유니크 제약이 막는다.
     *
     * @param force true 면 저장된 게 있어도 다시 생성한다 ("다시 받기" 버튼용)
     */
    public DailyAdvice generate(Long userId, boolean force) {
        DemoSession session = sessionRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("데모 세션이 없습니다: userId=" + userId));

        int day = session.getCurrentDay();
        if (day < 1) {
            throw new IllegalStateException("아직 수집된 데이터가 없습니다 (Day 0)");
        }
        LocalDate targetDate = session.currentDate();

        DailyAdvice existing = adviceRepository.findByUserIdAndTargetDate(userId, targetDate).orElse(null);
        if (existing != null && existing.getStatus() == AdviceStatus.SUCCEEDED && !force) {
            log.debug("조언 캐시 사용: userId={} date={}", userId, targetDate);
            return existing;
        }

        DailyAdvice advice = existing != null ? existing : DailyAdvice.builder()
                .userId(userId)
                .targetDate(targetDate)
                .dayInStudy(day)
                .build();
        advice.setDayInStudy(day);
        advice.setStatus(AdviceStatus.PENDING);
        advice.setErrorMessage(null);
        advice.setStartedAt(LocalDateTime.now());

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("사용자가 없습니다: " + userId));

            String prompt = buildPrompt(user, userId, targetDate, day);
            advice.setSentDays(Math.min(properties.getHistoryDays(), day));
            advice.setSentFeatures(WearableFeatures.featureCount());

            AnthropicClient.Reply reply = anthropicClient.complete(SYSTEM_PROMPT, prompt);
            advice.markSucceeded(reply.text(), reply.model(), reply.inputTokens(), reply.outputTokens(),
                    reply.truncated());

            log.info("조언 생성 완료: userId={} day={} 입력{}토큰 출력{}토큰 ({}ms)",
                    userId, day, reply.inputTokens(), reply.outputTokens(), advice.getLatencyMs());

        } catch (Exception e) {
            log.error("조언 생성 실패: userId={} day={}", userId, day, e);
            advice.markFailed(e);
        }

        try {
            return adviceRepository.save(advice);
        } catch (DataIntegrityViolationException e) {
            // ★ 같은 날짜 조언을 동시에 두 곳에서 만들면 uk_advice_user_date 에 걸린다.
            //   실제로 났다 — 브라우저의 자동 토글과 수동 호출이 겹쳤다.
            //   충돌은 "이미 누가 만들었다"는 뜻이므로 그걸 돌려주는 게 맞다.
            //   에러를 그대로 올리면 화면에 빨간 배너가 뜨는데, 사용자 입장에선
            //   조언이 멀쩡히 만들어졌는데도 실패로 보인다.
            log.info("조언 동시 생성 충돌 — 이미 저장된 것을 사용합니다: userId={} date={}", userId, targetDate);
            return adviceRepository.findByUserIdAndTargetDate(userId, targetDate)
                    .orElseThrow(() -> e);
        }
    }

    // ------------------------------------------------------------------
    // 프롬프트 조립
    // ------------------------------------------------------------------

    /**
     * 최근 N일 웨어러블 + 그 구간의 예측 + 정적 정보를 JSON 으로 싣는다.
     *
     * <p><b>결측 키는 뺀다.</b> 44개 중 3~13개가 매일 비어 있어서 그대로 실으면 토큰이
     * 12% 늘고 모델도 읽기 어렵다. 대신 "무엇이 빠졌는지"를 따로 알려 준다 —
     * 결측 자체가 정보이기 때문이다 (예: 운동 기록이 없는 날).
     */
    private String buildPrompt(User user, Long userId, LocalDate targetDate, int day) {
        int windowDays = properties.getHistoryDays();
        LocalDate from = targetDate.minusDays(windowDays - 1L);

        List<WearableDaily> wearables = wearableDailyRepository
                .findByUserIdAndMeasuredOnBetweenOrderByMeasuredOnDesc(userId, from, targetDate)
                .stream()
                .sorted(java.util.Comparator.comparing(WearableDaily::getMeasuredOn))
                .toList();

        List<Map<String, Object>> daily = wearables.stream().map(w -> {
            Map<String, Object> all = WearableFeatures.toMap(w);
            Map<String, Object> present = new LinkedHashMap<>();
            List<String> missing = new java.util.ArrayList<>();
            all.forEach((k, v) -> {
                if (v == null) {
                    missing.add(k);
                } else {
                    present.put(k, v);
                }
            });
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", w.getMeasuredOn().toString());
            row.put("signals", present);
            if (!missing.isEmpty()) {
                row.put("missing", missing);
            }
            return row;
        }).toList();

        List<Map<String, Object>> predictions = predictionResultRepository
                .findByUserIdAndTargetDateBetweenOrderByTargetDateAsc(userId, from, targetDate)
                .stream()
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", p.getTargetDate().toString());
                    row.put("phase", p.getPhase() == null ? null : p.getPhase().getLabel());
                    row.put("lh", p.getLh());
                    row.put("estrogen", p.getEstrogen());
                    row.put("pdg", p.getPdg());
                    row.put("confidence", p.getPhaseConfidence());
                    return row;
                })
                .toList();

        PredictionResult today = predictionResultRepository
                .findByUserIdAndTargetDate(userId, targetDate).orElse(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("오늘", targetDate.toString());
        payload.put("수집일차", day);
        payload.put("사용자", Map.of(
                "출생연도", user.getBirthDate() == null ? "미상" : user.getBirthDate().getYear(),
                "초경연령", user.getAgeOfFirstMenarche() == null ? "미상" : user.getAgeOfFirstMenarche(),
                "인종", user.getEthnicity() == null ? "미상" : user.getEthnicity()));
        payload.put("오늘의_예측", today == null ? null : Map.of(
                "phase", today.getPhase() == null ? "미상" : today.getPhase().getLabel(),
                "lh", String.valueOf(today.getLh()),
                "estrogen", String.valueOf(today.getEstrogen()),
                "pdg", String.valueOf(today.getPdg())));
        payload.put("최근_예측_이력", predictions);
        payload.put("최근_웨어러블", daily);

        return """
                아래는 한 사용자의 최근 %d일 웨어러블 데이터와 우리 모델의 호르몬/주기 예측입니다.

                - `최근_웨어러블.signals` 는 그날 측정된 값입니다. `missing` 은 그날 측정되지 않은 항목입니다.
                - `lh`/`estrogen`/`pdg` 는 **측정값이 아니라 모델의 예측값**입니다.
                - 단위: rmssd·HRV = ms, 심박 = bpm, nightly_temperature = 피부온도 °C(체온 아님),
                  수면 관련 = 분, glucose = mmol/L, originalduration = 밀리초.
                - `resting_heart_rate` 는 일간, `sleep_resting_heart_rate` 는 수면 중 값입니다.

                오늘 하루의 상태를 짚어 주고, 데이터에 근거한 생활 제안을 해 주세요.

                ```json
                %s
                ```
                """.formatted(daily.size(), mapper.writeValueAsString(payload));
    }
}
