-- ============================================================
-- 생리 주기 예측 앱 — 통합 스키마 (MySQL 8.0)
--
-- 측정 시각은 전부 DATETIME. 하루에 여러 번 측정하는 경우를 열어둔 구조.
-- 덮어쓰지 않고 계속 INSERT 하는 append 방식이므로 이력이 자동 보존됨.
--
-- 각 테이블마다 "보낼 때 / 받을 때" 유의사항을 주석으로 달아뒀습니다.
-- DB 제약으로 강제하지 않으니 애플리케이션에서 지켜야 합니다.
--
-- 타입 근거: merged_nan.csv (5,436행) 전수 검사, 실측 min/max 를 주석에 표기
-- 컬럼 의미: mcPHASES README 기준. 미명시 항목만 (추정) 표기
--
-- 날짜 컬럼 3종 구분
--   *_at  : 실제 측정 / 추론 실행 시각 (DATETIME, UTC 저장)
--   *_on  : *_at 에서 자동 생성되는 날짜. 조인과 인덱스 전용 (생성 컬럼)
--   predicted_date / range_* / started_on : 미래 또는 사건의 "날". 측정값 아니므로 DATE 유지
-- ============================================================

-- DROP VIEW  IF EXISTS v_daily_journal;
-- DROP TABLE IF EXISTS period_logs;
-- DROP TABLE IF EXISTS daily_selfreports;
-- DROP TABLE IF EXISTS period_predictions;
-- DROP TABLE IF EXISTS hormone_predictions;
-- DROP TABLE IF EXISTS daily_features;
-- DROP TABLE IF EXISTS users;


-- ============================================================
-- 1. 사용자  (subject-info.csv)
-- ============================================================
CREATE TABLE users (
  user_id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  external_uid          VARCHAR(64) NOT NULL UNIQUE
                        COMMENT '인증 시스템 연동용 외부 식별자',
  source_participant_id TINYINT UNSIGNED NULL
                        COMMENT '원본 데이터셋 id. 실측 1~50, 실제 40명. 연구 데이터 임포트용',

  enrolled_at           DATETIME NOT NULL
                        COMMENT '가입 시각(UTC). day_in_study <-> 실제 날짜 변환의 기준점 (추정: 원본에 없음)',
  timezone              VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul'
                        COMMENT '표시용 시간대. UTC 저장값을 이걸로 변환 (추정: 원본에 없음)',

  birth_year            SMALLINT UNSIGNED NULL COMMENT '출생연도. 실측 1993~2004',
  age_of_first_menarche TINYINT UNSIGNED NULL  COMMENT '초경 나이(세). 실측 10~15',
  ethnicity             ENUM('East Asian','Southeast Asian','White','Middle Eastern',
                             'Latina','Caribbean','South Asian','African') NULL
                        COMMENT '자가보고 인종/민족. 원본 8개 범주',

  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 기본 정보 + 정적 피처';


-- ============================================================
-- 2. X 데이터 — 웨어러블 피처
--
--  ┌─ 보낼 때 ────────────────────────────────────────────────
--  │ observed_at 에 "측정 시각"을 넣습니다. 하루 1회만 보낼 거면
--  │ 로컬 자정(00:00:00)으로 고정하면 일 단위처럼 동작합니다.
--  │ 같은 날 여러 번 보내도 됩니다. 덮어쓰지 않고 별도 행으로 쌓입니다.
--  │ 재전송/보정값도 그냥 INSERT 하세요. 이전 값이 그대로 남습니다.
--  │ UNIQUE 는 (user_id, observed_at) 이므로 시각까지 똑같은 것만 막힙니다.
--  └──────────────────────────────────────────────────────────
--  ┌─ 받을 때 ────────────────────────────────────────────────
--  │ 그냥 조회하면 하루에 여러 행이 나올 수 있습니다.
--  │ 하루 대표값 1건이 필요하면 observed_on 으로 묶고 observed_at 최신 1건을
--  │ 고르세요. v_daily_journal 뷰가 이 처리를 이미 하고 있습니다.
--  │ 조인·필터는 observed_at 이 아니라 observed_on(생성 컬럼)을 쓰세요.
--  │ DATE(observed_at) 으로 감싸면 인덱스를 못 탑니다.
--  └──────────────────────────────────────────────────────────
-- ============================================================
CREATE TABLE daily_features (
  feature_id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,

  observed_at           DATETIME NOT NULL
                        COMMENT '측정 시각(UTC). 하루 1회만 쓸 거면 로컬 자정으로 고정',
  observed_on           DATE GENERATED ALWAYS AS (DATE(observed_at)) STORED
                        COMMENT 'observed_at 의 날짜. 조인/인덱스 전용 자동 생성 컬럼. 직접 INSERT 불가',
  day_in_study          SMALLINT UNSIGNED NOT NULL
                        COMMENT 'study_interval 의 Day 1 부터 시작하는 정규화 일자 인덱스. 실측 1~1004',
  study_interval        SMALLINT UNSIGNED NULL
                        COMMENT '수집 구간. 2022 = Interval 1(1~4월), 2024 = Interval 2(7~10월). 연구 데이터 전용',
  is_weekend            TINYINT(1) NOT NULL DEFAULT 0 COMMENT '주말 여부 (boolean)',

  -- ---------- 수면 세션 구간 ----------
  -- 아래 피처들이 전부 이 한 번의 수면에서 파생됨:
  --   nightly_temperature, temperature_samples, temperature_diff_from_baseline,
  --   rmssd, low_frequency, high_frequency, rhr_sleep, spo2_variation_std,
  --   minutesasleep, minutesawake, efficiency, deep_sleep_in_minutes,
  --   restlessness, overall_score, *_breathing_rate
  sleep_start_at        DATETIME NULL
                        COMMENT '수면 세션 시작 시각(UTC). 원본 sleep.csv 의 sleep_start_timestamp',
  sleep_end_at          DATETIME NULL
                        COMMENT '수면 세션 종료 시각(UTC). 자정을 넘기면 시작일과 날짜가 다름',
  sleep_spans_midnight  TINYINT(1)
                        GENERATED ALWAYS AS (DATE(sleep_start_at) <> DATE(sleep_end_at)) STORED
                        COMMENT '자정 경계 여부. 날짜 귀속 규칙 검증용',

  -- ---------- 활동 : active_minutes.csv ----------
  sedentary             SMALLINT UNSIGNED NULL COMMENT '좌식 시간(분). 실측 0~1440',
  lightly               SMALLINT UNSIGNED NULL COMMENT '가벼운 강도 운동 시간(분). 실측 0~572',
  moderately            SMALLINT UNSIGNED NULL COMMENT '중강도 운동 시간(분). 실측 0~276',
  very                  SMALLINT UNSIGNED NULL COMMENT '고강도 운동 시간(분). 실측 0~264',

  -- ---------- 칼로리 / 고도 ----------
  calories              SMALLINT UNSIGNED NULL
                        COMMENT '하루 소모 칼로리. 안정시 + 활동시 포함. 실측 496~6430 (calories.csv)',
  altitude              SMALLINT UNSIGNED NULL
                        COMMENT '고도 상승량(미터). 해발고도 아님, GPS 미사용. 실측 0~2560 (altitude.csv)',

  -- ---------- 운동 세션 : exercise.csv (그룹 전체 결측 69%) ----------
  exercise_start_at     DATETIME NULL
                        COMMENT '운동 세션 시작 시각(UTC). 원본 exercise.csv 의 start_timestamp',
  originalduration      INT UNSIGNED NULL
                        COMMENT '운동 세션 지속시간(밀리초). 수정 전 원본값. 실측 4,000~22,408,000',
  averageheartrate      SMALLINT UNSIGNED NULL
                        COMMENT '운동 세션 중 평균 심박수(bpm). 실측 47~172',
  exercise_calories     SMALLINT UNSIGNED NULL
                        COMMENT '운동 세션 중 소모 칼로리. 실측 0~1102 (추정: 원본 exercise.csv 의 calories)',
  steps                 INT UNSIGNED NULL
                        COMMENT '걸음 수. 실측 0~938,808 (추정: 일일 값이 아닌 세션 단위 합계)',

  -- ---------- 심박 : heart_rate.csv ----------
  bpm                   DECIMAL(6,2) NULL COMMENT '심박수(bpm). 실측 54.23~131.01 (추정: 집계 평균)',
  bpm_min               SMALLINT UNSIGNED NULL COMMENT '최저 심박수(bpm). 실측 30~99',
  bpm_max               SMALLINT UNSIGNED NULL COMMENT '최고 심박수(bpm). 실측 86~213',

  -- ---------- 안정시 심박 : 출처가 다른 두 지표 ----------
  rhr_daily             DECIMAL(5,2) NULL
                        COMMENT '[핵심] 안정시 심박수(bpm). 깨어있고 안정된 상태. 실측 47.56~89.35. 원본 resting_heart_rate.csv 의 value',
  rhr_sleep             SMALLINT UNSIGNED NULL
                        COMMENT '[핵심] 수면 중 안정시 심박수(bpm). 실측 47~90. 원본 sleep_score.csv 의 resting_heart_rate. rhr_daily 와 상관 0.59 로 별개 지표',

  -- ---------- Active Zone Minutes : active_zone_minutes.csv ----------
  fat_burn              SMALLINT UNSIGNED NULL COMMENT '지방연소 존 획득 AZM(분). 실측 0~429. 원본 FAT_BURN',
  cardio                SMALLINT UNSIGNED NULL COMMENT '유산소 존 획득 AZM(분). 실측 0~302. 원본 CARDIO',
  peak                  SMALLINT UNSIGNED NULL COMMENT '피크 존 획득 AZM(분). 실측 0~36. 원본 PEAK',

  -- ---------- 심박존 체류시간 : time_in_heart_rate_zones.csv ----------
  -- 위 AZM 과 출처가 다른 별도 지표. 중복 아님
  below_default_zone_1  SMALLINT UNSIGNED NULL COMMENT '지방연소 존 미만 체류시간. 실측 1~1440',
  in_default_zone_1     SMALLINT UNSIGNED NULL COMMENT '지방연소(fat burn) 존 체류시간. 실측 0~1147',
  in_default_zone_2     SMALLINT UNSIGNED NULL COMMENT '유산소(cardio) 존 체류시간. 실측 0~465',
  in_default_zone_3     SMALLINT UNSIGNED NULL COMMENT '피크(peak) 존 체류시간. 실측 0~201',

  -- ---------- HRV : heart_rate_variability_details.csv ----------
  rmssd                 DECIMAL(7,3) NULL
                        COMMENT '[핵심] 인접 심박간격 차이의 제곱평균제곱근. HRV 대표 지표. 실측 0~194.172',
  low_frequency         DECIMAL(9,3) NULL COMMENT '심박수 장기 변동 성분. 실측 72.551~9253.908',
  high_frequency        DECIMAL(9,3) NULL COMMENT '심박수 단기 변동 성분. 실측 28.255~8405.930',

  -- ---------- 수면 : sleep.csv ----------
  -- 정수처럼 보이나 소수 존재 (추정: 하루 여러 세션 평균)
  minutesasleep         DECIMAL(7,2) NULL COMMENT '총 수면시간(분). 실측 61~1125',
  minutesawake          DECIMAL(6,2) NULL COMMENT '수면 중 각성시간(분). 실측 0~304',
  efficiency            DECIMAL(5,2) NULL COMMENT '수면 효율(%). 침대에 있던 시간 중 실제 수면 비율. 실측 15~100',
  nap_minutes_total     SMALLINT UNSIGNED NULL
                        COMMENT '낮잠 총합(분). 실측 0~676 (추정: mainsleep=false 세션 합계)',

  -- ---------- 수면 점수 : sleep_score.csv ----------
  overall_score         TINYINT UNSIGNED NULL COMMENT '종합 수면 점수(100점 만점). 실측 30~94',
  deep_sleep_in_minutes SMALLINT UNSIGNED NULL COMMENT '깊은 수면 시간(분). 실측 0~254',
  restlessness          DECIMAL(6,4) NULL
                        COMMENT '수면 중 뒤척임. 움직임 기반. 실측 0.0222~0.2651 (추정: 단위 미명시, 비율값)',

  -- ---------- 호흡 : respiratory_rate_summary.csv ----------
  full_sleep_breathing_rate  DECIMAL(4,1) NULL COMMENT '수면 전체 평균 호흡수(회/분). 실측 10.6~29.8',
  deep_sleep_breathing_rate  DECIMAL(4,1) NULL COMMENT '깊은 수면 평균 호흡수(회/분). 실측 9.0~29.8',
  light_sleep_breathing_rate DECIMAL(4,1) NULL COMMENT '얕은 수면 평균 호흡수(회/분). 실측 9.0~29.8',
  rem_sleep_breathing_rate   DECIMAL(4,1) NULL COMMENT 'REM 수면 평균 호흡수(회/분). 실측 0.4~29.8',

  -- ---------- 체온 : computed_temperature.csv ----------
  nightly_temperature   DECIMAL(7,4) NULL
                        COMMENT '[핵심] 야간 평균 피부온도(섭씨). 배란 후 상승. 실측 25.6993~36.4309',
  temperature_samples   SMALLINT UNSIGNED NULL
                        COMMENT '해당 밤 체온 측정 횟수(개수). 온도값 아님. 실측 2~1097. 데이터 신뢰도 지표로 활용 가능',

  -- ---------- 체온 편차 : wrist_temperature.csv ----------
  temperature_diff_from_baseline DECIMAL(6,3) NULL
                        COMMENT '[핵심] 개인 기준선 대비 피부온도 편차(섭씨). 음수 가능. 실측 -8.132~2.216',

  -- ---------- 산소 : estimated_oxygen_variation.csv ----------
  spo2_variation_std    DECIMAL(6,3) NULL
                        COMMENT '수면 중 혈중 산소 변동. 원본은 적외선/적색광 흡수 비율. 실측 0~27.460 (추정: 표준편차 집계)',

  -- ---------- 체력 : demographic_vo2_max.csv ----------
  filtered_demographic_vo2_max DECIMAL(7,4) NULL
                        COMMENT 'VO2 Max 추정치에 Fitbit 내부 필터/스무딩 적용. 실측 24.5483~93.7262',

  -- ---------- 혈당 : glucose.csv (Dexcom CGM) ----------
  glucose_mean          DECIMAL(5,3) NULL COMMENT '혈당 평균(mmol/L). mg/dL 아님. 실측 2.931~9.900',
  glucose_std           DECIMAL(5,3) NULL COMMENT '혈당 표준편차(mmol/L). 실측 0~3.288',

  -- ---------- 스트레스 : stress_score.csv ----------
  stress_score          TINYINT UNSIGNED NULL
                        COMMENT 'Fitbit 스트레스 관리 점수. 수면/반응성/활동량 3개 하위점수 합산. 실측 54~94 (추정: 점수 방향성 미명시)',

  -- ---------- 수집 상태 (원본에 없음) ----------
  device_synced_at      DATETIME NULL
                        COMMENT '웨어러블에서 데이터를 수신한 시각(UTC). observed_at 과 며칠 차이날 수 있음. "데이터가 N일 밀려 있어요" 표시용',
  ingested_at           DATETIME NULL
                        COMMENT '파이프라인이 이 행을 확정한 시각(UTC). created_at 과 구분',
  window_start_at       DATETIME NULL COMMENT '집계 대상 구간 시작(UTC). 연속 측정을 접은 경우',
  window_end_at         DATETIME NULL COMMENT '집계 대상 구간 종료(UTC)',

  -- ---------- 파생 (원본에 없음) ----------
  is_valid_day          TINYINT(1) NOT NULL DEFAULT 0
                        COMMENT '예측 사용 가능 여부. 콜드스타트 20일 카운트 기준 (추정: 판정 기준 자체 정의 필요)',
  completeness          DECIMAL(4,3) NULL COMMENT '피처 충족률 0.000~1.000 (추정: 자체 계산)',

  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_user_observed (user_id, observed_at),
  KEY idx_user_day (user_id, observed_on),
  KEY idx_user_study_day (user_id, day_in_study),
  KEY idx_user_synced (user_id, device_synced_at),
  CONSTRAINT fk_feat_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='X 데이터. 웨어러블 피처 (Fitbit + Dexcom). append 방식, 결측 다수';


-- ============================================================
-- 3. Y 데이터 (1) — 호르몬 추정치
--    출처: hormones_and_selfreport.csv (Mira 배란 측정기)
--
--  ┌─ 보낼 때 ────────────────────────────────────────────────
--  │ target_at = 이 추정치가 가리키는 시점, predicted_at = 추론을 돌린 시각.
--  │ 둘을 헷갈리지 마세요. 같은 target_at 을 재추론해도 그냥 INSERT 하면
--  │ 이전 예측이 남습니다. UPDATE 하지 마세요.
--  │ model_version 을 반드시 채우세요. 나중에 어느 모델이 낸 값인지 구분 불가해집니다.
--  └──────────────────────────────────────────────────────────
--  ┌─ 받을 때 ────────────────────────────────────────────────
--  │ 같은 날짜에 여러 예측이 있을 수 있습니다.
--  │ 화면에는 predicted_at 이 가장 최근인 1건만 쓰세요.
--  │ 모델 비교/회고가 목적이면 전체를 가져와 model_version 별로 나누세요.
--  └──────────────────────────────────────────────────────────
-- ============================================================
CREATE TABLE hormone_predictions (
  prediction_id         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,

  target_at             DATETIME NOT NULL COMMENT '이 추정치가 가리키는 시점(UTC)',
  target_on             DATE GENERATED ALWAYS AS (DATE(target_at)) STORED
                        COMMENT 'target_at 의 날짜. 조인/인덱스 전용 자동 생성 컬럼',

  lh                    DECIMAL(6,2) NULL
                        COMMENT '황체형성호르몬(mIU/mL). 배란 직전 급등(LH surge), 스파이크형. 실측 0~185.60',
  estrogen              DECIMAL(6,2) NULL
                        COMMENT '에스트론-3-글루쿠로니드(ng/mL). 에스트로겐 대사체. 주기당 봉우리 2개. 실측 0~640.00',
  pdg                   DECIMAL(5,2) NULL
                        COMMENT '프레그난디올 글루쿠로니드(mcg/mL). 프로게스테론 대사체. 배란 후 상승 = 사후 확인용. 실측 1.00~30.00. 학습 데이터 65% 결측',

  lh_baseline_ratio       DECIMAL(6,3) NULL COMMENT '개인 평균 대비 비율. UI "평소보다 높음" 표기용 (추정: 자체 계산)',
  estrogen_baseline_ratio DECIMAL(6,3) NULL COMMENT '개인 평균 대비 비율 (추정: 자체 계산)',
  pdg_baseline_ratio      DECIMAL(6,3) NULL COMMENT '개인 평균 대비 비율 (추정: 자체 계산)',

  phase                 ENUM('Menstrual','Follicular','Fertility','Luteal') NULL
                        COMMENT '주기 단계. 원본 4개 값 그대로. README 예시는 ovulation 이나 실제 데이터는 Fertility. 호르몬 수치로 프론트 역산 금지(임상 판단 영역)',
  phase_confidence      DECIMAL(4,3) NULL COMMENT '단계 예측 확률 0.000~1.000 (추정: 모델 출력 포함 여부 미확정)',

  model_version         VARCHAR(32) NOT NULL COMMENT '재현성 확보용 모델 버전',
  predicted_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '모델 추론 실행 시각(UTC)',
  input_through_at      DATETIME NULL COMMENT '이 추론에 반영된 입력 데이터의 마지막 시점(UTC)',

  UNIQUE KEY uk_user_target_run (user_id, target_at, predicted_at),
  KEY idx_user_target_latest (user_id, target_on, predicted_at DESC),
  CONSTRAINT fk_horm_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Y 데이터 1. 호르몬 추정치. append 방식';


-- ============================================================
-- 4. Y 데이터 (2) — 월경 예정일 예측
--
--  ┌─ 보낼 때 ────────────────────────────────────────────────
--  │ 매일 재계산되므로 매번 새 행으로 INSERT 하세요. UPDATE 금지.
--  │ 이전 행이 남아야 "어제보다 하루 늦춰짐" 을 계산할 수 있습니다.
--  │ predicted_date / range_start / range_end 는 DATE 유지.
--  │ 예측 대상은 "며칠" 이지 "몇 시" 가 아니기 때문입니다.
--  │ days_until 은 음수 가능(예정일 경과). UNSIGNED 아님.
--  └──────────────────────────────────────────────────────────
--  ┌─ 받을 때 ────────────────────────────────────────────────
--  │ 화면 표시는 predicted_at 최신 1건.
--  │ 변동폭 계산에는 최근 N건을 전부 가져와야 합니다. 최신 1건만으로는
--  │ 예측이 얼마나 흔들리는지 알 수 없습니다.
--  └──────────────────────────────────────────────────────────
-- ============================================================
CREATE TABLE period_predictions (
  prediction_id         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,

  predicted_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '모델 추론 실행 시각(UTC)',
  predicted_on          DATE GENERATED ALWAYS AS (DATE(predicted_at)) STORED
                        COMMENT 'predicted_at 의 날짜. 조인/인덱스 전용 자동 생성 컬럼',

  predicted_date        DATE NOT NULL COMMENT '예측된 월경 시작일. 매일 변동 가능. 측정값 아니므로 DATE',
  range_start           DATE NULL COMMENT '예측 구간 시작. UI는 점 대신 범위로 표시',
  range_end             DATE NULL COMMENT '예측 구간 끝',

  days_until            SMALLINT NULL
                        COMMENT 'predicted_date - predicted_on. 음수면 예정일 경과(overdue). UNSIGNED 아님',
  input_days_used       SMALLINT UNSIGNED NOT NULL COMMENT '예측에 사용한 누적 입력 일수. 20 부터 증가',
  input_through_at      DATETIME NULL COMMENT '이 추론에 반영된 입력 데이터의 마지막 시점(UTC)',
  confidence            DECIMAL(4,3) NULL
                        COMMENT '모델 제공 시 저장. 없으면 최근 7일 예측 분산으로 대체 계산',

  model_version         VARCHAR(32) NOT NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_user_run (user_id, predicted_at),
  KEY idx_user_latest (user_id, predicted_at DESC),
  KEY idx_user_day (user_id, predicted_on),
  CONSTRAINT fk_pp_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Y 데이터 2. 월경 예정일. 매일 재계산되므로 이력 누적';


-- ============================================================
-- 5. 사용자 자가입력 증상
--    출처: hormones_and_selfreport.csv 의 self-report 부분
--    척도 0(Not at all) ~ 5(Very high)
--
--  ┌─ 보낼 때 ────────────────────────────────────────────────
--  │ reported_at = 증상이 발생한 시점. 사후 입력이면 실제 발생 시점을 넣으세요.
--  │ 앱에 입력한 시각과 다를 수 있고, 그건 정상입니다.
--  │ 하루에 여러 번 기록해도 됩니다. 아침·저녁 컨디션이 다를 수 있으니까요.
--  └──────────────────────────────────────────────────────────
--  ┌─ 받을 때 ────────────────────────────────────────────────
--  │ 하루 대표값이 필요하면 reported_on 으로 묶고 최신 1건 또는 평균을 쓰세요.
--  │ 어느 쪽을 쓸지 화면 성격에 따라 정하고, 섞지 마세요.
--  └──────────────────────────────────────────────────────────
-- ============================================================
CREATE TABLE daily_selfreports (
  report_id             BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,

  reported_at           DATETIME NOT NULL COMMENT '증상이 발생한 시점(UTC)',
  reported_on           DATE GENERATED ALWAYS AS (DATE(reported_at)) STORED
                        COMMENT 'reported_at 의 날짜. 조인/인덱스 전용 자동 생성 컬럼',
  entered_at            DATETIME NULL COMMENT '사용자가 앱에 입력한 시각(UTC). reported_at 과 다를 수 있음',

  flow_volume           TINYINT UNSIGNED NULL COMMENT '월경량. Likert (Not at all ~ Very Heavy)',
  flow_color            VARCHAR(32) NULL COMMENT '월경혈/분비물 색상 범주 (예: not at all, dark brown, bright red)',

  appetite              TINYINT UNSIGNED NULL COMMENT '식욕 수준 0~5',
  exerciselevel         TINYINT UNSIGNED NULL COMMENT '체감 운동/활동량 0~5',
  headaches             TINYINT UNSIGNED NULL COMMENT '두통 정도 0~5',
  cramps                TINYINT UNSIGNED NULL COMMENT '생리통/복통 정도 0~5',
  sorebreasts           TINYINT UNSIGNED NULL COMMENT '유방 압통 정도 0~5',
  fatigue               TINYINT UNSIGNED NULL COMMENT '피로/탈진 수준 0~5',
  sleepissue            TINYINT UNSIGNED NULL COMMENT '수면의 질/수면 유지 어려움 0~5',
  moodswing             TINYINT UNSIGNED NULL COMMENT '기분 변화 빈도/정도 0~5',
  stress                TINYINT UNSIGNED NULL COMMENT '체감 스트레스 수준 0~5',
  foodcravings          TINYINT UNSIGNED NULL COMMENT '음식 갈망 강도 0~5',
  indigestion           TINYINT UNSIGNED NULL COMMENT '소화기 불편감 0~5',
  bloating              TINYINT UNSIGNED NULL COMMENT '복부 팽만 정도 0~5',

  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_user_reported (user_id, reported_at),
  KEY idx_user_day (user_id, reported_on),
  CONSTRAINT fk_sr_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 자가입력 증상 기록';


-- ============================================================
-- 6. 월경 시작/종료 기록 — 예측 평가용 정답
--
--  ┌─ 보낼 때 ────────────────────────────────────────────────
--  │ started_on / ended_on 은 DATE 유지. 월경 시작은 "시점" 이 아니라 "날" 이고
--  │ 사용자도 날짜로만 기억합니다. 시각까지 요구하면 입력 부담만 커집니다.
--  │ 진행 중이면 ended_on 은 NULL 로 두세요.
--  └──────────────────────────────────────────────────────────
--  ┌─ 받을 때 ────────────────────────────────────────────────
--  │ 예측 정확도 평가의 정답값입니다.
--  │ period_predictions.predicted_date 와 비교해 오차를 계산하세요.
--  └──────────────────────────────────────────────────────────
-- ============================================================
CREATE TABLE period_logs (
  log_id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,

  started_on            DATE NOT NULL COMMENT '월경 시작일. 예측 정확도 평가의 정답값',
  ended_on              DATE NULL COMMENT '종료일. 진행 중이면 NULL',
  logged_at             DATETIME NULL COMMENT '사용자가 기록을 남긴 시각(UTC). 사후 입력이면 started_on 과 크게 차이남',
  source                ENUM('user','derived','imported') NOT NULL DEFAULT 'user'
                        COMMENT '직접 입력 / flow_volume 파생 / 외부 앱 가져오기',
  note                  VARCHAR(255) NULL,

  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY uk_user_start (user_id, started_on),
  CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='월경 시작/종료 기록';


-- ============================================================
-- 7. 통합 조회 뷰 — "그 날 하루" 를 한 줄로
--
--    각 테이블에서 날짜별 최신 1건만 골라 조인합니다.
--    하루에 여러 번 측정한 경우 최신값만 나오므로, 하루 안의 변화를 보려면
--    이 뷰가 아니라 원본 테이블을 직접 조회하세요.
--
--    period_predictions 는 predicted_on 으로 조인 = 그 날 시점에 예측했던 값.
--    ("9월 3일에는 12일이라고 했는데 실제로는 15일이었네" 회고용)
-- ============================================================
CREATE OR REPLACE VIEW v_daily_journal AS
WITH f AS (
  SELECT *, ROW_NUMBER() OVER (PARTITION BY user_id, observed_on ORDER BY observed_at DESC) AS rn
  FROM daily_features
),
h AS (
  SELECT *, ROW_NUMBER() OVER (PARTITION BY user_id, target_on ORDER BY predicted_at DESC) AS rn
  FROM hormone_predictions
),
p AS (
  SELECT *, ROW_NUMBER() OVER (PARTITION BY user_id, predicted_on ORDER BY predicted_at DESC) AS rn
  FROM period_predictions
),
s AS (
  SELECT *, ROW_NUMBER() OVER (PARTITION BY user_id, reported_on ORDER BY reported_at DESC) AS rn
  FROM daily_selfreports
)
SELECT
  f.user_id,
  f.observed_on,
  f.observed_at,

  -- X : 주기 예측 핵심 신호
  f.nightly_temperature,
  f.temperature_diff_from_baseline,
  f.rhr_daily,
  f.rhr_sleep,
  f.rmssd,
  f.minutesasleep,
  f.overall_score,
  f.stress_score,
  f.completeness,
  f.is_valid_day,
  f.device_synced_at,

  -- Y : 호르몬
  h.lh,
  h.estrogen,
  h.pdg,
  h.phase,
  h.predicted_at AS hormone_predicted_at,

  -- Y : 그 날 시점에 예측했던 월경 예정일
  p.predicted_date,
  p.range_start,
  p.range_end,
  p.days_until,

  -- 자가입력
  s.cramps,
  s.fatigue,
  s.moodswing,
  s.bloating,
  s.flow_volume,

  -- 실제 월경 여부
  (l.log_id IS NOT NULL) AS period_active

FROM f
LEFT JOIN h ON h.user_id = f.user_id AND h.target_on    = f.observed_on AND h.rn = 1
LEFT JOIN p ON p.user_id = f.user_id AND p.predicted_on = f.observed_on AND p.rn = 1
LEFT JOIN s ON s.user_id = f.user_id AND s.reported_on  = f.observed_on AND s.rn = 1
LEFT JOIN period_logs l
  ON  l.user_id = f.user_id
  AND f.observed_on BETWEEN l.started_on AND COALESCE(l.ended_on, l.started_on)
WHERE f.rn = 1;


-- ============================================================
-- 참고 쿼리
-- ============================================================

-- 특정 날짜 전체 그림
-- SELECT * FROM v_daily_journal WHERE user_id = ? AND observed_on = '2026-09-03';

-- 특정 날짜의 시간대별 변화 (하루 여러 번 측정한 경우)
-- SELECT observed_at, rhr_daily, nightly_temperature
-- FROM daily_features
-- WHERE user_id = ? AND observed_on = '2026-09-03'
-- ORDER BY observed_at;

-- 콜드스타트 진행률 (날짜 기준 중복 제거)
-- SELECT COUNT(DISTINCT observed_on) AS valid_days
-- FROM daily_features WHERE user_id = ? AND is_valid_day = 1;

-- 최신 예정일 + 직전 예측 대비 변동
-- SELECT predicted_date,
--        LAG(predicted_date) OVER (ORDER BY predicted_at) AS prev_date,
--        DATEDIFF(predicted_date, LAG(predicted_date) OVER (ORDER BY predicted_at)) AS shift_days
-- FROM period_predictions
-- WHERE user_id = ? ORDER BY predicted_at DESC LIMIT 1;

-- 최근 7일 예측 변동폭 (신뢰도 대용 지표)
-- SELECT STDDEV_POP(days_until) AS volatility_days
-- FROM period_predictions
-- WHERE user_id = ? AND predicted_at >= UTC_TIMESTAMP() - INTERVAL 7 DAY;

-- 데이터 신선도
-- SELECT TIMESTAMPDIFF(HOUR, MAX(device_synced_at), UTC_TIMESTAMP()) AS hours_stale
-- FROM daily_features WHERE user_id = ?;

-- 예측 정확도 (정답 대비 오차)
-- SELECT p.predicted_on, p.predicted_date, l.started_on,
--        DATEDIFF(p.predicted_date, l.started_on) AS error_days
-- FROM period_predictions p
-- JOIN period_logs l ON l.user_id = p.user_id AND l.started_on > p.predicted_on
-- WHERE p.user_id = ?;