package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.config.DemoProperties;
import com.sixletter.hormone_web_backend.entity.DemoSeedWearable;
import com.sixletter.hormone_web_backend.entity.DemoSession;
import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.repository.DemoSeedWearableRepository;
import com.sixletter.hormone_web_backend.repository.DemoSessionRepository;
import com.sixletter.hormone_web_backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 부팅 시 {@code db/seed_data.json} 을 DB 에 적재한다.
 *
 * <p>이 파일은 {@code scripts/extract_seed.py} 가 merged_nan.xlsx 에서 뽑아낸
 * <b>실제 참가자 90일치</b>다 (id=22 / 2024구간 / day_in_study 862~951).
 *
 * <p>이 저장소에 스크립트가 없다면(프론트/백 분리 배포) HANDOFF 문서의 재생성 절차를 볼 것.
 * 하지만 {@code seed_data.json} 자체가 저장소에 들어 있으므로 보통은 재생성할 일이 없다.
 *
 * <p><b>멱등하다.</b> 이미 시드가 적재돼 있으면 아무것도 하지 않는다. 매 부팅마다
 * 데모 진행 상태를 초기화하면 시연 도중 앱을 재시작했을 때 진행이 날아간다.
 * 다시 넣고 싶으면 {@code app.demo.reload-seed=true} 로 띄우거나 DB 에서 직접 지운다.
 *
 * <p>시드 파일이 없어도 앱은 정상 부팅한다 — 경고만 남긴다.
 * (백엔드만 먼저 띄워보는 경우가 있어서 여기서 막으면 안 된다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoSeedLoader implements ApplicationRunner {

    private static final String SEED_PATH = "db/seed_data.json";

    private final EntityManager entityManager;
    private final DemoProperties demoProperties;
    private final UserRepository userRepository;
    private final DemoSessionRepository sessionRepository;
    private final DemoSeedWearableRepository seedRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long userId = demoProperties.getUserId();

        if (seedRepository.countByUserId(userId) > 0) {
            log.info("데모 시드가 이미 적재되어 있습니다 (userId={}). 건너뜁니다.", userId);
            return;
        }

        ClassPathResource resource = new ClassPathResource(SEED_PATH);
        if (!resource.exists()) {
            log.warn("데모 시드 파일이 없습니다: {}. 시연 기능을 쓰려면 "
                    + "`python scripts/extract_seed.py` 를 먼저 실행하세요.", SEED_PATH);
            return;
        }

        try (InputStream in = resource.getInputStream()) {
            SeedFile seed = objectMapper.readValue(in, SeedFile.class);
            loadUser(userId, seed);
            loadSession(userId, seed);
            loadDays(userId, seed);
            log.info("데모 시드 적재 완료: userId={} {}일치 (참가자 {} / {}구간)",
                    userId, seed.days().size(),
                    seed.source() == null ? "?" : seed.source().participantId(),
                    seed.source() == null ? "?" : seed.source().studyInterval());
        } catch (Exception e) {
            // 시드 적재 실패로 앱이 안 뜨면 안 된다. 데모 API 호출 시점에 다시 알려준다.
            log.error("데모 시드 적재 실패 — 시연 기능이 동작하지 않습니다", e);
        }
    }

    /**
     * 데모 사용자를 <b>고정 id</b> 로 만든다 (프론트 {@code VITE_DEMO_USER_ID} 와 맞추려면 필요).
     *
     * <p>id 를 직접 지정한 엔티티를 {@code save()} 하면 JPA 가 detached 로 보고 UPDATE 를
     * 날려서 0건 갱신 → OptimisticLockingFailure 가 난다. {@code @GeneratedValue(IDENTITY)}
     * 라 {@code persist()} 도 지정한 id 를 무시한다. 그래서 없을 때만 네이티브 INSERT 로
     * id 를 못 박고, 그 뒤부터는 일반 JPA 경로를 쓴다.
     */
    private void loadUser(Long userId, SeedFile seed) {
        SeedFile.SeedUser su = seed.user();
        LocalDate birthDate = su.birthYear() == null ? null : LocalDate.of(su.birthYear(), 1, 1);

        if (!userRepository.existsById(userId)) {
            entityManager.createNativeQuery(
                            "INSERT INTO users (id, name, birth_date, age_of_first_menarche, ethnicity) "
                                    + "VALUES (:id, :name, :birthDate, :menarche, :ethnicity)")
                    .setParameter("id", userId)
                    .setParameter("name", su.name())
                    .setParameter("birthDate", birthDate)
                    .setParameter("menarche", su.ageOfFirstMenarche())
                    .setParameter("ethnicity", su.ethnicity())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();
            return;
        }

        User user = userRepository.findById(userId).orElseThrow();
        user.setName(su.name());
        // 모델은 birth_year 만 쓰므로 생일은 1월 1일로 둔다 (원본에 월/일이 없다)
        user.setBirthDate(birthDate);
        user.setAgeOfFirstMenarche(su.ageOfFirstMenarche());
        user.setEthnicity(su.ethnicity());
        userRepository.save(user);
    }

    private void loadSession(Long userId, SeedFile seed) {
        DemoSession session = sessionRepository.findById(userId)
                .orElseGet(() -> DemoSession.builder().userId(userId).currentDay(0).build());
        session.setStartDate(LocalDate.parse(seed.startDate()));
        session.setTotalDays(seed.totalDays());
        session.setColdStartDays(seed.coldStartDays());
        if (session.getCurrentDay() == null) {
            session.setCurrentDay(0);
        }
        sessionRepository.save(session);
    }

    private void loadDays(Long userId, SeedFile seed) {
        for (SeedFile.SeedDay day : seed.days()) {
            seedRepository.save(DemoSeedWearable.builder()
                    .userId(userId)
                    .dayIndex(day.dayIndex())
                    .payload(day.wearable())
                    .truth(day.truth())
                    .build());
        }
    }

    // ------------------------------------------------------------------
    // seed_data.json 구조
    // ------------------------------------------------------------------

    record SeedFile(Source source, SeedUser user, String startDate,
                    int totalDays, int coldStartDays, List<SeedDay> days) {

        record Source(String dataset, String participantId, String studyInterval) {
        }

        record SeedUser(Long id, String name, Integer birthYear,
                        Integer ageOfFirstMenarche, String ethnicity) {
        }

        /**
         * @param wearable DB 컬럼명 기준 44개. 결측은 null
         * @param truth    실측 정답 라벨. Mock Predictor 참조용 — 모델 입력 금지
         */
        record SeedDay(int dayIndex, String date,
                       Map<String, Object> wearable, Map<String, Object> truth) {
        }
    }
}
