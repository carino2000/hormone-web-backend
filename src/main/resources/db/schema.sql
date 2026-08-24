-- ===========================================================================
-- hormone_web 스키마 (MySQL 8.0)
--
-- 이 파일이 스키마의 유일한 진실이다. JPA 는 ddl-auto: none 이라
-- 엔티티가 테이블을 만들지 않는다. 엔티티를 고치면 이 파일도 같이 고칠 것.
--
-- 실행 방법 (둘 중 하나)
--   a) 자동  : application.yaml 의 spring.sql.init.mode: always 가 부팅 시 실행
--   b) 수동  : mysql -u root -p hormone_web < src/main/resources/db/schema.sql
--
-- 전부 CREATE TABLE IF NOT EXISTS 라 몇 번 실행해도 안전하다.
-- 처음부터 다시 만들려면 db/drop.sql 을 먼저 실행할 것.
--
-- created_at / updated_at 은 엔티티에서 insertable=false, updatable=false 이므로
-- 반드시 DB 기본값으로 채워야 한다. DEFAULT 절을 지우지 말 것.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- users — 사용자. 모델의 정적 피처(주황색 컬럼) 보유
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(50)  NULL,
    birth_date            DATE         NULL,                    -- 모델 피처 birth_year 의 원본
    height                DECIMAL(4,1) NULL,                    -- cm
    weight                DECIMAL(4,1) NULL,                    -- kg
    age_of_first_menarche INT          NULL,                    -- 모델 피처
    ethnicity             VARCHAR(32)  NULL,                    -- 모델 피처. 실데이터 8종
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- wearable_daily — 웨어러블 하루치 데이터. 사용자당 하루 1건.
--
-- 무색 44개 컬럼 = 모델 입력 피처. 순서는 merged_nan.xlsx 원본 순서를 따른다.
--
-- ★ 안정시 심박 2개 컬럼은 모델 피처명과 이름이 엇갈려 있다. 고치지 말 것:
--     resting_heart_rate       (일간, 소수)  -> 모델 피처명 "value"
--     sleep_resting_heart_rate (수면중, 정수) -> 모델 피처명 "resting_heart_rate"
--   근거: mcPHASES 원본에서 resting_heart_rate.csv 의 컬럼명이 "value" 이고,
--         sleep_score.csv 의 컬럼명이 "resting_heart_rate" 이다.
--
-- DECIMAL 자릿수는 merged_nan.xlsx 5,436행 전수 검사로 오버플로가 없음을 확인했다.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wearable_daily (
    id                             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                        BIGINT       NOT NULL,
    measured_on                    DATE         NOT NULL,

    -- 활동 (분)
    sedentary                      INT          NULL,
    lightly                        INT          NULL,
    moderately                     INT          NULL,
    very                           INT          NULL,

    -- Active Zone Minutes (분)
    FAT_BURN                       INT          NULL,
    CARDIO                         INT          NULL,
    PEAK                           INT          NULL,

    -- 고도 / 칼로리
    altitude                       INT          NULL,
    calories                       INT          NULL,

    -- 체온 (Fitbit 피부온도. 체온 아님 — 실측 31~35°C)
    temperature_samples            INT          NULL,
    nightly_temperature            DECIMAL(7,4) NULL,

    -- 체력 / 산소
    filtered_demographic_vo2_max   DECIMAL(7,4) NULL,
    spo2_variation_std             DECIMAL(6,3) NULL,

    -- 운동 세션
    originalduration               BIGINT       NULL,           -- ms
    averageheartrate               INT          NULL,
    exercise_calories              INT          NULL,
    steps                          BIGINT       NULL,

    -- 혈당 (CGM). ★ 단위 mmol/L — mg/dL 아님. 실측 5.4~6.6
    glucose_mean                   DECIMAL(5,3) NULL,
    glucose_std                    DECIMAL(5,3) NULL,

    -- 심박
    bpm                            DECIMAL(6,2) NULL,
    bpm_min                        INT          NULL,
    bpm_max                        INT          NULL,

    -- 안정시 심박 (위 주석의 교차 매핑 참고)
    resting_heart_rate             DECIMAL(5,2) NULL,           -- -> 모델 "value"
    sleep_resting_heart_rate       INT          NULL,           -- -> 모델 "resting_heart_rate"

    -- HRV
    rmssd                          DECIMAL(7,3) NULL,
    low_frequency                  DECIMAL(9,3) NULL,
    high_frequency                 DECIMAL(9,3) NULL,

    -- 호흡 (회/분)
    full_sleep_breathing_rate      DECIMAL(4,1) NULL,
    deep_sleep_breathing_rate      DECIMAL(4,1) NULL,
    light_sleep_breathing_rate     DECIMAL(4,1) NULL,
    rem_sleep_breathing_rate       DECIMAL(4,1) NULL,

    -- 수면
    minutesasleep                  DECIMAL(7,2) NULL,
    efficiency                     DECIMAL(5,2) NULL,
    minutesawake                   DECIMAL(6,2) NULL,
    nap_minutes_total              INT          NULL,
    overall_score                  INT          NULL,
    deep_sleep_in_minutes          INT          NULL,
    restlessness                   DECIMAL(6,4) NULL,

    -- 스트레스
    stress_score                   INT          NULL,

    -- 심박존 체류시간 (분)
    in_default_zone_3              INT          NULL,
    in_default_zone_2              INT          NULL,
    in_default_zone_1              INT          NULL,
    below_default_zone_1           INT          NULL,

    -- 체온 편차
    temperature_diff_from_baseline DECIMAL(6,3) NULL,

    created_at                     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_date (user_id, measured_on),
    CONSTRAINT fk_wearable_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- prediction_result — 예측 결과. (user, 날짜) 하루 1건.
--
-- ★ 슈퍼셋 가정 (TODO_ROADMAP.md 규칙 10):
--   모델이 무엇을 돌려주든 다 담을 수 있게 전부 nullable 로 둔다.
--   호르몬마다 모델이 따로 돌아 결과가 쪼개져 와도 같은 행에 필드 단위로 병합한다.
--   응답 원문은 raw_response 에 통째로 남겨서 계약이 바뀌어도 데이터를 잃지 않는다.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prediction_result (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT       NOT NULL,
    target_date             DATE         NOT NULL,
    day_in_study            INT          NULL,

    -- 호르몬 3종
    lh                      DECIMAL(8,3) NULL,
    estrogen                DECIMAL(8,3) NULL,
    pdg                     DECIMAL(8,3) NULL,

    -- 주기 단계. Menstrual | Follicular | Fertility | Luteal 4개만 허용
    phase                   VARCHAR(16)  NULL,
    -- 모델이 확신도를 하나만 준다. 호르몬별 확신도와 phase 확률분포는 계약에 없어서 뺐다.
    phase_confidence        DECIMAL(4,3) NULL,

    -- 다음 월경 예정일은 받지 않는다. 모델이 오늘자 phase 만 알 수 있어서 합의 하에 제외했다.
    -- 되살리려면 next_period_date / _range_start / _range_end 를 다시 추가할 것.

    -- 예측 근거 [{feature, weight, direction}, ...]
    contributions           JSON         NULL,

    model_version           VARCHAR(32)  NULL,
    raw_response            JSON         NULL,

    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_pred_user_date (user_id, target_date),
    CONSTRAINT fk_pred_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_pred_phase CHECK (
        phase IS NULL OR phase IN ('Menstrual', 'Follicular', 'Fertility', 'Luteal')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- prediction_job — 예측 요청 이력(성공/실패 모두). 시연 중 장애 추적용.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prediction_job (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    target_date     DATE        NOT NULL,
    status          VARCHAR(16) NOT NULL,                       -- PENDING | SUCCEEDED | FAILED
    request_payload JSON        NULL,
    response_body   MEDIUMTEXT  NULL,
    error_message   TEXT        NULL,
    latency_ms      INT         NULL,
    started_at      DATETIME    NULL,
    finished_at     DATETIME    NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_job_user_date (user_id, target_date),
    KEY idx_job_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- demo_session — 시연용 진행 커서. "하루 넘기기" 버튼이 여기를 올린다.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS demo_session (
    user_id         BIGINT   NOT NULL,
    start_date      DATE     NOT NULL,                          -- Day 1 에 해당하는 달력 날짜
    current_day     INT      NOT NULL DEFAULT 0,                -- 0 = 아직 아무것도 안 보냄
    total_days      INT      NOT NULL DEFAULT 90,
    cold_start_days INT      NOT NULL DEFAULT 20,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_demo_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- demo_seed_wearable — 시연 시드 원본 (merged_nan 실참가자 90일치, id=22/2024/D862~951).
--
-- wearable_daily 와 분리해 두는 이유: advance 가 여기서 하루씩 꺼내
-- wearable_daily 로 옮긴다. 그래야 "수집이 진행되는 느낌"이 DB 에서도 실제로 재현된다.
--
-- truth 는 실측 정답 라벨(phase/lh/estrogen/pdg)이다.
-- ★ 절대 모델 입력으로 보내지 말 것. 시연 후 정확도 비교(채점) 전용이다.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS demo_seed_wearable (
    id        BIGINT NOT NULL AUTO_INCREMENT,
    user_id   BIGINT NOT NULL,
    day_index INT    NOT NULL,                                  -- 1..90
    payload   JSON   NOT NULL,                                  -- 웨어러블 44개 피처
    truth     JSON   NULL,                                      -- 실측 정답 라벨
    PRIMARY KEY (id),
    UNIQUE KEY uk_seed_user_day (user_id, day_index)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- daily_advice — Claude API 로 받은 "오늘의 조언". 하루 1건.
--
-- 왜 저장하나: 같은 날짜를 다시 열 때 재호출하지 않기 위해서다. 호출당 1만 토큰이라
-- 화면을 옮길 때마다 다시 부르면 비용이 그대로 곱해진다. UNIQUE 로 하루 1건을 강제한다.
--
-- 실패도 남긴다(status=FAILED). 시연 중 조언이 안 뜰 때 원인을 찾을 수 있어야 한다.
--
-- ★ 프롬프트 원문은 저장하지 않는다. 최근 30일 웨어러블이 통째로 들어가서 한 건이
--   30KB 가 넘고, 어차피 시드에서 언제든 재구성할 수 있다. 대신 무엇을 보냈는지
--   요약(sent_days / sent_features)만 남긴다.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_advice (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    target_date    DATE         NOT NULL,
    day_in_study   INT          NULL,

    status         VARCHAR(16)  NOT NULL,                       -- PENDING | SUCCEEDED | FAILED
    content        MEDIUMTEXT   NULL,                           -- 조언 본문 (마크다운)
    error_message  TEXT         NULL,

    -- 무엇을 보냈나 (프롬프트 원문 대신)
    sent_days      INT          NULL,                           -- 최근 며칠치를 보냈나
    sent_features  INT          NULL,                           -- 하루당 피처 개수

    model          VARCHAR(64)  NULL,                           -- 예 claude-sonnet-5
    input_tokens   INT          NULL,
    output_tokens  INT          NULL,
    latency_ms     INT          NULL,
    -- max_tokens 에 걸려 문장 중간에서 잘렸는지. 화면이 "잘림" 표시를 띄운다.
    truncated      BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_advice_user_date (user_id, target_date),
    CONSTRAINT fk_advice_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_advice_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
