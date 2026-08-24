package com.sixletter.hormone_web_backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sixletter.hormone_web_backend.entity.Contribution;
import com.sixletter.hormone_web_backend.entity.CyclePhase;
import com.sixletter.hormone_web_backend.entity.DemoSeedWearable;
import com.sixletter.hormone_web_backend.entity.DemoSession;
import com.sixletter.hormone_web_backend.entity.JobStatus;
import com.sixletter.hormone_web_backend.entity.PredictionJob;
import com.sixletter.hormone_web_backend.entity.PredictionResult;
import com.sixletter.hormone_web_backend.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * B-01 검증: 새로 만든 엔티티가 실제 MySQL 스키마와 맞물려 돌아가는지 확인한다.
 *
 * <p>가장 큰 리스크는 JSON 컬럼 매핑({@code @JdbcTypeCode(SqlTypes.JSON)})이다.
 * 컴파일은 통과해도 런타임에 조용히 깨질 수 있어서, flush + clear 로 실제 DB 왕복을
 * 강제한 뒤 값이 그대로 돌아오는지 본다.
 *
 * <p>{@code @Transactional} 이라 테스트가 끝나면 전부 롤백된다 — 개발 DB 를 더럽히지 않는다.
 *
 * <p>실데이터는 merged_nan.xlsx 의 id=22 / 2024구간 / day_in_study 902~931 구간에서 가져왔다.
 */
@SpringBootTest
@Transactional
@DisplayName("예측 관련 엔티티 영속화")
class PredictionPersistenceTest {

    @Autowired private EntityManager em;
    @Autowired private UserRepository userRepository;
    @Autowired private PredictionResultRepository predictionResultRepository;
    @Autowired private PredictionJobRepository predictionJobRepository;
    @Autowired private DemoSessionRepository demoSessionRepository;
    @Autowired private DemoSeedWearableRepository demoSeedWearableRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .name("테스트참가자")
                .birthDate(LocalDate.of(2002, 1, 1))
                .ageOfFirstMenarche(10)
                .ethnicity("Southeast Asian")
                .build());
    }

    /** 저장 -> 실제 DB 왕복 -> 재조회. 영속성 컨텍스트 캐시로 통과해버리는 걸 막는다. */
    private <T> T roundTrip(T entity, Class<T> type, Object id) {
        em.flush();
        em.clear();
        return em.find(type, id);
    }

    @Test
    @DisplayName("JSON 컬럼 3종(기여도/확률분포/원본응답)이 값 손실 없이 왕복된다")
    void jsonColumnsRoundTrip() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("model_version", "v0.1.0");
        raw.put("우리가_아직_매핑안한_필드", "이것도 버려지면 안 된다");

        PredictionResult saved = predictionResultRepository.save(PredictionResult.builder()
                .user(user)
                .targetDate(LocalDate.of(2026, 8, 31))
                .dayInStudy(19)
                .lh(new BigDecimal("41.600"))          // 실측: Day 19 LH 서지
                .estrogen(new BigDecimal("225.400"))
                .pdg(new BigDecimal("4.599"))
                .phase(CyclePhase.FERTILITY)
                .phaseConfidence(new BigDecimal("0.780"))
                .contributions(List.of(
                        // ★ 후행 0 을 일부러 넣는다. 아래 scale 검증용
                        new Contribution("rmssd", new BigDecimal("0.340"), "down", "HRV 감소"),
                        new Contribution("value", new BigDecimal("0.18"), "up", "안정시 심박 상승")))
                .rawResponse(raw)
                .build());

        PredictionResult found = roundTrip(saved, PredictionResult.class, saved.getId());

        assertThat(found.getContributions()).hasSize(2);
        assertThat(found.getContributions().get(0).feature()).isEqualTo("rmssd");
        assertThat(found.getContributions().get(0).weight()).isEqualByComparingTo("0.340");
        assertThat(found.getContributions().get(0).direction()).isEqualTo("down");
        assertThat(found.getContributions().get(0).signal()).isEqualTo("HRV 감소");

        // ★ JSON 컬럼은 BigDecimal 의 scale 을 보존하지 않는다 (0.340 -> 0.34).
        //   DECIMAL 컬럼(lh/estrogen/phase_confidence)은 스키마에 scale 이 박혀 있어
        //   보존되지만, JSON(contributions/raw_response) 안의 숫자는 그냥 숫자라
        //   후행 0 이 사라진다. 그래서 equals 가 아니라 compareTo 로 비교해야 한다.
        assertThat(found.getContributions().get(0).weight())
                .isNotEqualTo(new BigDecimal("0.340"))      // scale 이 달라 equals 는 실패
                .isEqualByComparingTo(new BigDecimal("0.340")); // 값은 같다

        // DECIMAL 컬럼은 반대로 scale 이 보존된다
        assertThat(found.getPhaseConfidence()).isEqualByComparingTo("0.780");

        // 슈퍼셋 가정: 우리가 모르는 필드도 raw_response 에 살아남아야 한다
        assertThat(found.getRawResponse()).containsEntry("우리가_아직_매핑안한_필드", "이것도 버려지면 안 된다");
    }

    @Test
    @DisplayName("phase 는 'Fertility' 형태로 저장되고 enum 으로 되돌아온다")
    void phaseStoredAsLabel() {
        PredictionResult saved = predictionResultRepository.save(PredictionResult.builder()
                .user(user).targetDate(LocalDate.of(2026, 8, 31))
                .phase(CyclePhase.FERTILITY).build());
        em.flush();

        String stored = (String) em.createNativeQuery(
                        "SELECT phase FROM prediction_result WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        // 자바 상수명("FERTILITY")이 아니라 계약상의 라벨이 저장돼야 한다.
        // 그래야 DB CHECK 제약과 모델/프론트 계약이 맞는다.
        assertThat(stored).isEqualTo("Fertility");
        assertThat(roundTrip(saved, PredictionResult.class, saved.getId()).getPhase())
                .isEqualTo(CyclePhase.FERTILITY);
    }

    @Test
    @DisplayName("모델이 대소문자를 다르게 보내도 phase 를 알아본다")
    void phaseParsingIsLenient() {
        assertThat(CyclePhase.fromLabel("fertility")).contains(CyclePhase.FERTILITY);
        assertThat(CyclePhase.fromLabel(" Luteal ")).contains(CyclePhase.LUTEAL);
        assertThat(CyclePhase.fromLabel("MENSTRUAL")).contains(CyclePhase.MENSTRUAL);
        // 모르는 값은 예외가 아니라 empty — 예측 저장 전체가 실패하면 안 된다
        assertThat(CyclePhase.fromLabel("ovulation")).isEmpty();
        assertThat(CyclePhase.fromLabel(null)).isEmpty();
    }

    @Test
    @DisplayName("결측(null)은 0으로 변질되지 않는다")
    void nullsAreNotCoercedToZero() {
        // pdg 는 학습 데이터에서 64.7% 결측. null 이 정상 상태다.
        PredictionResult saved = predictionResultRepository.save(PredictionResult.builder()
                .user(user).targetDate(LocalDate.of(2026, 8, 20))
                .lh(new BigDecimal("2.900"))
                .pdg(null)
                .build());

        PredictionResult found = roundTrip(saved, PredictionResult.class, saved.getId());

        assertThat(found.getPdg()).isNull();
        assertThat(found.getEstrogen()).isNull();
        assertThat(found.getPhase()).isNull();
    }

    @Test
    @DisplayName("쪼개져 온 예측 결과가 같은 행에 필드 단위로 병합된다")
    void partialResultsMergeInsteadOfOverwrite() {
        LocalDate date = LocalDate.of(2026, 8, 31);

        // 1) lh 만 담긴 응답이 먼저 도착
        predictionResultRepository.save(PredictionResult.builder()
                .user(user).targetDate(date).lh(new BigDecimal("41.600")).build());
        em.flush();

        // 2) estrogen 만 담긴 응답이 나중에 도착 — lh 를 지우면 안 된다
        PredictionResult existing = predictionResultRepository
                .findByUserIdAndTargetDate(user.getId(), date).orElseThrow();
        existing.mergeFrom(PredictionResult.builder()
                .estrogen(new BigDecimal("225.400"))
                .phase(CyclePhase.FERTILITY)
                .build());
        predictionResultRepository.save(existing);

        PredictionResult merged = roundTrip(existing, PredictionResult.class, existing.getId());

        assertThat(merged.getLh()).isEqualByComparingTo("41.600");       // 살아남아야 함
        assertThat(merged.getEstrogen()).isEqualByComparingTo("225.400"); // 새로 들어와야 함
        assertThat(merged.getPhase()).isEqualTo(CyclePhase.FERTILITY);
        assertThat(predictionResultRepository.findByUserIdOrderByTargetDateAsc(user.getId())).hasSize(1);
    }

    @Test
    @DisplayName("PredictionJob 이 실패 원인과 소요 시간을 기록한다")
    void jobRecordsFailure() {
        PredictionJob job = predictionJobRepository.save(PredictionJob.builder()
                .userId(user.getId())
                .targetDate(LocalDate.of(2026, 8, 31))
                .status(JobStatus.PENDING)
                .requestPayload(Map.of("user_id", user.getId(), "target_date", "2026-08-31"))
                .startedAt(java.time.LocalDateTime.now().minusSeconds(2))
                .build());

        job.markFailed(new IllegalStateException("예측 서버 응답 없음"));
        predictionJobRepository.save(job);

        PredictionJob found = roundTrip(job, PredictionJob.class, job.getId());

        assertThat(found.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(found.getErrorMessage()).isEqualTo("예측 서버 응답 없음");
        assertThat(found.getLatencyMs()).isGreaterThanOrEqualTo(2000);
        assertThat(found.getRequestPayload()).containsKey("user_id");
    }

    @Test
    @DisplayName("메시지 없는 예외도 원인이 남는다")
    void jobHandlesExceptionWithoutMessage() {
        PredictionJob job = PredictionJob.builder()
                .userId(user.getId()).targetDate(LocalDate.of(2026, 8, 31))
                .status(JobStatus.PENDING).build();

        job.markFailed(new NullPointerException());

        assertThat(job.getErrorMessage()).isEqualTo("java.lang.NullPointerException");
    }

    @Test
    @DisplayName("DemoSession 이 일차 <-> 날짜 변환과 콜드스타트 판정을 한다")
    void demoSessionMapsDaysToDates() {
        DemoSession session = demoSessionRepository.save(DemoSession.builder()
                .userId(user.getId())
                .startDate(LocalDate.of(2026, 8, 13))
                .currentDay(0).totalDays(30).coldStartDays(20)
                .build());

        assertThat(session.dateOfDay(1)).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(session.dateOfDay(20)).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(session.dateOfDay(30)).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(session.currentDate()).isNull();   // 아직 시작 전

        assertThat(session.isColdStart(19)).isTrue();  // Day 19 까지 수집
        assertThat(session.isColdStart(20)).isFalse(); // Day 20 부터 예측
        assertThat(session.hasNext()).isTrue();

        DemoSession found = roundTrip(session, DemoSession.class, user.getId());
        assertThat(found.getColdStartDays()).isEqualTo(20);
    }

    @Test
    @DisplayName("시드 payload 의 null 피처가 null 로 보존된다")
    void seedPayloadPreservesNulls() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("rmssd", 39.369);
        payload.put("resting_heart_rate", 85.40);      // 일간 (모델 피처명 "value")
        payload.put("sleep_resting_heart_rate", 80);   // 수면중 (모델 피처명 "resting_heart_rate")
        payload.put("glucose_mean", null);             // 2024 구간은 혈당이 아예 없다
        payload.put("sedentary", null);

        DemoSeedWearable seed = demoSeedWearableRepository.save(DemoSeedWearable.builder()
                .userId(user.getId()).dayIndex(19)
                .payload(payload)
                .truth(Map.of("phase", "Fertility", "lh", 41.6))
                .build());

        DemoSeedWearable found = roundTrip(seed, DemoSeedWearable.class, seed.getId());

        assertThat(found.getPayload()).containsEntry("rmssd", 39.369);
        // 결측이 0 으로 바뀌지 않고 키도 사라지지 않아야 한다
        assertThat(found.getPayload()).containsKey("glucose_mean");
        assertThat(found.getPayload().get("glucose_mean")).isNull();
        assertThat(found.getTruth()).containsEntry("phase", "Fertility");
        assertThat(demoSeedWearableRepository.findByUserIdAndDayIndex(user.getId(), 19)).isPresent();
    }
}
