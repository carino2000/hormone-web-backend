# hormone_web — 인수인계 문서 (2026-08-24)

> **읽는 대상:** 다른 기기의 Claude Code, 또는 이 프로젝트를 처음 여는 사람.
>
> **당신이 가진 것:** ① 프론트 코드 ② 백엔드 코드 ③ 이 문서 ④ `merge.csv`(원본 데이터셋)
> **당신이 갖고 있지 않은 것:** 루트 문서들(`README.md`, `PROJECT_CONTEXT.md`,
> `TODO_ROADMAP.md`, `docs/*.md`), 시드 추출 스크립트(`scripts/extract_seed.py`).
> **그 문서들에 있던 내용 중 필요한 건 전부 이 문서에 옮겨 담았다.** 스크립트는 §7 부록에 있다.
>
> 코드 주석과 이 문서가 어긋나면 **이 문서가 맞다.** 주석은 작업 중 일부가 낡았을 수 있다.

**목차**
| # | 섹션 | 내용 |
|---|---|---|
| 1 | [프로젝트가 뭔가](#1-프로젝트가-뭔가--5분-요약) | 5분 요약. **Mock 이 정답을 베낀다는 것부터 읽을 것** |
| 2 | [**DB**](#2-db--처음부터-만들기) | 테이블 생성 → 창고 데이터 적재 → 검증 |
| 3 | [**백엔드**](#3-백엔드) | 실행 · API · 흐름 · 하면 안 되는 것 |
| 4 | [**프론트엔드**](#4-프론트엔드) | 실행 · 화면 5탭 · 상태 분기 · 하면 안 되는 것 |
| 5 | [데이터 지식](#5-데이터-지식--이걸-모르면-값이-왜-그런지-이해-못-한다) | 컬럼 55개 분류 · 교차 매핑 · 과거 사고 · 원본 데이터 문제 |
| 6 | [파이썬 모델 연동](#6-파이썬-모델-연동) | 요청/응답 계약 · 슈퍼셋 가정 · 진단 방법 |
| 7 | [부록](#7-부록) | 시드 재생성 스크립트 · 소스 트리 · 검증 결과 |

---

# 1. 프로젝트가 뭔가 — 5분 요약

웨어러블 신호로 **여성호르몬 3종(LH / Estrogen / PdG) + 월경주기 단계 + 다음 월경 예정일**을
예측해 보여주는 **PoC**. 식스레터스 과제.

> ### 이건 웹 서비스가 아니다.
> **기업 대표에게 예측모델을 보여주기 위한 시연물**이고, 최종 산출물은 **웹 시연 영상**이다.
> 로그인·회원가입·트래픽·배포·보안은 전부 범위 밖이다. 그런 걸 추가하지 말 것.

| 구성 | 스택 | 포트 |
|---|---|---|
| 프론트 | React 19 / Vite 8 / Tailwind 4 / zustand 5 / recharts 3 / framer-motion 13 / @stomp/stompjs | 5173 |
| 백엔드 | Spring Boot 4.1.0 / Java 21 / Gradle 9.7.1 / STOMP WebSocket | 8085 |
| DB | MySQL 8.0 (`hormone_web`) | 3306 |
| 예측 모델 | 파이썬 (**아직 미연동**) | 미정 |

## 1-1. 시연 시나리오

```
Day 0   아직 하룻밤도 안 지남. 모든 값이 "측정 안 됨"
  ↓ [하루 넘기기] 버튼
Day 1~19  웨어러블만 쌓인다. 예측 없음 (콜드스타트)
  ↓
Day 20  ★ 첫 예측 등장 (리빌). 여기가 시연의 하이라이트
  ↓
Day 28  첫 LH 서지 (실측 35.9)
  ↓
Day 90  끝
```

## 1-2. 🚨 가장 중요한 것 — 지금 예측은 진짜가 아니다

`MODEL_ENABLED=false`(기본값)면 백엔드 내장 **Mock 예측기**가 돈다.
Mock 은 **실측 정답(`demo_seed_wearable.truth`)을 읽어서 ±8% 결정적 노이즈를 얹는다.**
즉 **정답을 베낀다.**

```
Day    LH  예측/실측              Estrogen 예측/실측         PdG 예측/실측
D20     6.2 /   6.6 ( -6.6%)      88.6 /  92.9 ( -4.6%)     3.8 /  3.8 (+1.2%)
D24     5.4 /   5.3 ( +1.6%)     102.6 / 101.7 ( +0.9%)     2.4 /  2.7 (-10.6%)
D59    44.7 /  41.6 ( +7.4%)     226.0 / 225.4 ( +0.3%)     4.9 /  4.6 (+6.5%)
```

41일 내내 오차가 ±10% 를 안 벗어난다. 당연하다 — 정답을 보고 그렸으니까.

> **Mock 수치를 모델 성능으로 제시하면 안 된다.**
> 헤더의 `Mock 예측기 (mock-v1)` 배지와 `모델 성능` 탭의 경고 배너가 이걸 막는 장치다.
> **절대 지우거나 약화시키지 말 것.** 대표가 정확도를 물으면 배지를 가리키는 게 정직한 답이다.

`Math.random()` 을 쓰지 않는다 — 발표 중 같은 날짜를 다시 눌렀는데 값이 바뀌면 안 되기 때문.

---

# 2. DB — 처음부터 만들기

## 2-1. 결론부터: 사실 아무것도 안 해도 된다

**DB 만 만들고 백엔드를 띄우면 테이블 생성과 시드 적재가 자동으로 끝난다.**

```sql
CREATE DATABASE IF NOT EXISTS hormone_web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

```bash
cd hormone-web-backend && ./gradlew bootRun
```

부팅 시 두 가지가 일어난다:

| 순서 | 무슨 일 | 담당 |
|---|---|---|
| 1 | `db/schema.sql` 실행 → 테이블 6개 생성 | Spring `spring.sql.init` |
| 2 | `db/seed_data.json`(148KB, 90일치) → `users` / `demo_session` / `demo_seed_wearable` 적재 | `DemoSeedLoader` |

**두 파일 모두 백엔드 저장소 `src/main/resources/db/` 안에 있다.** 따로 받을 필요 없다.

전부 `CREATE TABLE IF NOT EXISTS` 라 몇 번 실행해도 안전하고,
시드 적재는 **멱등**하다 (이미 있으면 건너뛴다 — 시연 도중 재시작해도 진행이 안 날아간다).

## 2-2. 접속 설정

`application.yaml` 은 모든 값이 `${ENV:기본값}` 형태다. **비밀번호를 여기 쓰지 말 것.**

| 방법 | 어떻게 |
|---|---|
| A. 파일 | `src/main/resources/application-local.yaml.example` → `application-local.yaml` 로 복사 후 값 채우기 |
| B. 환경변수 | `DB_PASSWORD=... ./gradlew bootRun` |

`spring.profiles.default: local` 이라 프로필을 지정하지 않아도 `application-local.yaml` 이
자동 로드된다. 파일이 없어도 부팅은 된다(비밀번호 없는 root 로 시도).

```yaml
# application-local.yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hormone_web
    username: root
    password: 여기에_실제_비밀번호
```

## 2-3. 테이블 6개 — 무엇이 어디에 저장되나

```
users                  시연용 가상 사용자 1명 (id=1)
demo_session           시연 진행 상태 (current_day 가 여기 있다)
demo_seed_wearable     ★ "창고" — 90일치 원본. 아직 안 꺼낸 데이터
wearable_daily         ★ "꺼낸 것" — 하루씩 여기로 옮겨진다. 44개 컬럼
prediction_result      예측 결과 (Y)
prediction_job         예측 요청 이력 (성공/실패/지연). 장애 추적용
```

### 핵심 개념: 창고 → 일별 데이터

```
[demo_seed_wearable]                    [wearable_daily]
  day_index=1  payload(44) truth   ──┐
  day_index=2  payload(44) truth     │  "하루 넘기기" 누를 때마다
  day_index=3  payload(44) truth     └─▶  1행씩 복사됨
  ...                                     (44개 컬럼으로 펼쳐서)
  day_index=90
```

**왜 나눠 뒀나:** 그래야 "데이터가 하루씩 쌓이는 과정"이 DB 에서도 실제로 재현된다.
실서비스에서 사용자가 매일 웨어러블 데이터를 보내는 것과 같은 모양이 된다.

**`truth` 는 실측 정답 라벨(phase/lh/estrogen/pdg)이다.**
★ **절대 모델 입력으로 보내지 말 것.** Mock 참조용 + 정확도 채점용이다.

### 테이블별 상세

**`users`** — 시연용 단일 사용자. 로그인이 없어서 `id=1` 고정.

| 컬럼 | 용도 |
|---|---|
| `birth_date` | 모델 피처 `birth_year` 의 원본 (연도만 추출해서 보냄) |
| `age_of_first_menarche` | 모델 피처 |
| `ethnicity` | 모델 피처. 실데이터 8종 (`White` / `East Asian` / `Southeast Asian` / ...) |
| `height` / `weight` | 저장만 함. 현재 모델에 안 보냄 |

**`demo_session`** — PK 가 `user_id` 다 (사용자당 1행).

| 컬럼 | 값 |
|---|---|
| `start_date` | `2026-08-13` (= Day 1) |
| `current_day` | `0` = 아직 아무것도 안 보냄 |
| `total_days` | `90` |
| `cold_start_days` | `20` (Day 1~19 수집, Day 20 부터 예측) |

**`wearable_daily`** — 44개 피처 컬럼 + `user_id` + `measured_on`.
`UNIQUE KEY uk_user_date (user_id, measured_on)` — 하루 1건.

**`prediction_result`** — `UNIQUE KEY (user_id, target_date)`.

| 컬럼 그룹 | 내용 |
|---|---|
| 호르몬 | `lh` / `estrogen` / `pdg` + 각각 `_confidence` |
| 주기 단계 | `phase` + `phase_confidence` + `phase_probabilities`(JSON) |
| 다음 월경 | `next_period_date` / `_range_start` / `_range_end` — **점이 아니라 범위** |
| 근거 | `contributions`(JSON) `[{feature, weight, direction}]` |
| 메타 | `model_version`, `raw_response`(JSON, 모르는 필드 원문 보관) |

★ `CHECK (phase IN ('Menstrual','Follicular','Fertility','Luteal'))` 제약이 걸려 있다.
**5번째 라벨이 생기면 이 제약부터 고쳐야 한다.**

**`prediction_job`** — 성공/실패 모두 남긴다.
`request_payload`(JSON), `response_body`(MEDIUMTEXT), `error_message`, `latency_ms`.

`response_body` 가 JSON 타입이 아닌 이유: 실패 시 에러 HTML 이나 빈 응답이 올 수 있어서
**파싱 실패한 응답도 그대로 남길 수 있어야** 한다.

`user` 를 연관관계로 두지 않고 `user_id` 로만 둔 이유: 순수 로그라 조인이 필요 없고,
실패 기록을 남기는 경로에서 User 를 로드하다 또 실패하는 상황을 피하려고. DB FK 도 없다.

## 2-4. 처음부터 다시 만들기

```bash
mysql -u root -p hormone_web < hormone-web-backend/src/main/resources/db/drop.sql
```

그다음 백엔드를 다시 띄우면 스키마 생성 + 시드 적재가 다시 일어난다.

**시연 진행만 되돌리려면** (테이블은 그대로, 수집/예측만 삭제):

```bash
curl -X POST http://localhost:8085/api/demo/users/1/reset
```

또는 앱 우측 패널의 `초기화` 버튼.

## 2-5. 제대로 들어갔는지 확인

```sql
SELECT COUNT(*) FROM demo_seed_wearable;   -- 90 이어야 함
SELECT current_day, total_days, cold_start_days FROM demo_session;  -- 0 / 90 / 20
SELECT COUNT(*) FROM users;                 -- 1
SELECT COUNT(*) FROM wearable_daily;        -- 0 (아직 하루도 안 넘겼으므로)
```

```sql
-- 창고 1일차에 44개가 다 들어갔는지
SELECT JSON_LENGTH(payload) FROM demo_seed_wearable WHERE day_index = 1;   -- 44
```

```sql
-- ★ 교차 매핑 확인. 두 값이 달라야 정상이다 (같으면 §5-3 버그가 재발한 것)
SELECT JSON_EXTRACT(payload, '$.resting_heart_rate')        AS 일간_소수,
       JSON_EXTRACT(payload, '$.sleep_resting_heart_rate')  AS 수면중_정수
FROM demo_seed_wearable WHERE day_index = 1;
-- 예: 72.30 / 75
```

## 2-6. 시드를 `merge.csv` 에서 다시 만들어야 한다면

**보통은 필요 없다.** `seed_data.json` 이 백엔드 저장소에 들어 있다.
참가자를 바꾸거나 기간을 늘릴 때만 §7-1 의 스크립트를 쓴다.

> ⚠️ **파일명 주의.** 원본 데이터셋의 실제 파일명은 `merged_nan.xlsx` 였다.
> `merge.csv` 를 받았다면 같은 데이터를 CSV 로 내보낸 것으로 보인다.
> §7-1 스크립트는 **CSV 와 XLSX 를 둘 다 읽는다.** 컬럼 55개가 §5-1 목록과
> 일치하는지 먼저 확인할 것.

---

# 3. 백엔드

## 3-1. 실행

```bash
cd hormone-web-backend && ./gradlew bootRun
```

Windows 는 `gradlew.bat`. JDK 21 은 Gradle toolchain 이 자동으로 받는다.

> **Gradle 9.7.1 이 필요하다.** 9.5.1 에서는 JUnit Platform 6.0.3 과 충돌해서
> **모든 테스트 클래스가 `ClassNotFoundException`** 으로 죽는다. wrapper 를 낮추지 말 것.

```bash
cd hormone-web-backend && ./gradlew test
```

## 3-2. 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/hormone_web` | |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / (없음) | |
| `SERVER_PORT` | `8085` | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | |
| **`MODEL_ENABLED`** | **`false`** | `false`=내장 Mock / `true`=파이썬 서버 |
| `MODEL_BASE_URL` | `http://127.0.0.1:5000` | |
| `MODEL_PREDICT_PATH` | `/predict` | |
| `MODEL_INPUT_MODE` | `FULL_HISTORY` | `SINGLE_DAY` / `FULL_HISTORY` / `WINDOW` |
| `MODEL_API_KEY` | (없음) | 비어 있으면 인증 헤더를 안 붙임 |
| `ANTHROPIC_API_KEY` | (없음) | 향후 의학 어드바이스용. **현재 사용처 없음** |

## 3-3. 전체 흐름

```
[프론트] "Day N 정보 보내기" 클릭
   │
   ├─▶ POST /api/demo/users/1/advance
   │      │
   │      ├─ 1. demo_seed_wearable 에서 day_index=N 꺼냄
   │      ├─ 2. wearable_daily 로 INSERT (44개 컬럼으로 펼침)
   │      ├─ 3. demo_session.current_day = N
   │      ├─ 4. 콜드스타트 구간(N < 20)이면 여기서 끝
   │      └─ 5. 아니면 예측을 **비동기로** 트리거
   │
   ◀── 202 Accepted (예측 결과는 아직 없다!)
   │
   │   [백그라운드]
   │      ModelInputBuilder → 47개 조립 → PredictionClient
   │      → prediction_result 저장 + prediction_job 기록
   │
   ◀── WebSocket /topic/prediction/1  {type: PREDICTION_READY, ...}
   │
   └─▶ GET /api/demo/users/1/timeline  (전체 스냅샷 다시 받아 화면 재구성)
```

> ★ **`advance` 는 예측 결과를 반환하지 않는다.** 비동기라 아직 없다.
> 이 사실이 프론트 화면 분기에 직접 영향을 준다 → §4-4 `pending` 상태.

## 3-4. API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/demo/users/{id}/state` | 현재 일차/날짜/총일수/상태 |
| POST | `/api/demo/users/{id}/advance` | 하루 넘기기. **202** |
| POST | `/api/demo/users/{id}/reset` | Day 0 초기화 (시드는 보존) |
| GET | `/api/demo/users/{id}/timeline` | **전체 스냅샷.** 프론트는 이거 하나로 화면을 재구성 |
| GET | `/api/demo/users/{id}/jobs` | 예측 요청 이력 (기록 탭용) |
| GET | `/api/predictions/users/{id}/latest` | 웹소켓 폴백 |
| WS | `/ws` → `/topic/prediction/{id}` | 예측 완료 push |

### `timeline` 응답 모양

```jsonc
{
  "userId": 1,
  "startDate": "2026-08-13",
  "currentDay": 22,
  "totalDays": 90,
  "coldStartDays": 20,
  "status": "active",              // collecting | active | done
  "baseline": { /* 44개. 지금까지 수집된 값의 평균 */ },
  "days": [
    {
      "day": 1,
      "date": "2026-08-13",
      "wearable": { /* 44개. 결측은 null */ },
      "prediction": { /* 없으면 null */ },
      "truth": { /* ★ 예측이 있는 날에만 내려온다 — 스포일러 방지 */ }
    }
  ]
}
```

★ **`truth` 는 예측이 존재하는 날에만 실린다.** 아직 안 넘긴 미래 일차의 정답을
미리 보내면 시연에서 스포일러가 된다.

★ **`baseline` 은 "지금까지 수집된 값의 평균"이다** (고정값이 아님).
그래서 Day 1 에는 baseline 이 그날 값 자신이라 차이가 0 이 된다 → §4-5 참고.

## 3-5. 주요 클래스 — 여기만 알면 된다

| 파일 | 역할 |
|---|---|
| **`support/WearableFeatures.java`** | ★ **44개 피처의 단일 진실 (DB 컬럼명 기준).** 엔티티 ↔ Map 변환 |
| **`service/ModelInputBuilder.java`** | ★ DB 컬럼명 → 모델 피처명 번역 + 정적 3개 추가 = 47개 |
| `service/PredictionClient.java` | 인터페이스. Mock / Python 을 갈아끼우는 지점 |
| `service/MockPredictionClient.java` | `@ConditionalOnProperty(app.model.enabled=false)` |
| `service/PythonPredictionClient.java` | `@ConditionalOnProperty(app.model.enabled=true)` |
| `service/DemoService.java` | `advance` / `reset` / `getTimeline` / `getJobs` |
| `service/DemoSeedLoader.java` | 부팅 시 시드 적재 (`ApplicationRunner`) |
| `service/HormonePredictionService.java` | 비동기 예측 실행 + job 기록 + WS 발행 |
| `entity/PredictionResult.java` | `mergeFrom()` — 부분 응답 병합 |
| `entity/CyclePhase.java` + `CyclePhaseConverter.java` | 4개 라벨. `fromLabel()` 은 대소문자 무시 |

## 3-6. ⚠️ 백엔드에서 절대 하면 안 되는 것

| 하지 말 것 | 이유 |
|---|---|
| `spring.jpa.hibernate.ddl-auto` 를 `none` 외의 값으로 | 스키마의 단일 진실은 `db/schema.sql` 이다. 엔티티가 테이블을 바꾸면 둘이 갈라진다 |
| 결측을 `0` 이나 평균으로 채우기 | "측정 안 됨"과 "값이 0"이 구분돼야 한다. 임퓨테이션은 모델팀 소관 |
| 안정시 심박 2개 컬럼 매핑을 "고치기" | §5-3. 이름이 엇갈린 게 정상이다 |
| `Include.NON_NULL` 로 모델 요청 직렬화 | 결측 피처가 통째로 사라진다. 실제로 44→40 사고가 났다. §5-4 |
| `truth` 를 모델 입력에 넣기 | 정답을 보고 예측하는 게 된다 |
| Mock 에 `Math.random()` 쓰기 | 발표 중 같은 날짜를 다시 눌렀는데 값이 바뀌면 안 된다 |
| 주기 28일 하드코딩 | 실측 19~36일. 이 참가자도 33/31일 |

## 3-7. Jackson 주의 (Boot 4)

Spring Boot 4 는 **Jackson 3** (`tools.jackson`) 을 쓴다.
그런데 **애노테이션은 Jackson 2** (`com.fasterxml.jackson.annotation`) 를 그대로 쓴다.
import 를 섞지 말 것.

`PythonPredictionClient` 는 **전용 ObjectMapper** 를 갖고 있다
(`JsonInclude.Include.ALWAYS`). 전역 매퍼는 `NON_NULL` 이라 그대로 쓰면 결측이 날아간다.
그래서 객체를 넘기지 않고 **String 으로 직렬화한 뒤 전송**한다.

---

# 4. 프론트엔드

## 4-1. 실행

```bash
cd hormone-web-frontend && npm install && npm run dev
```

```bash
cd hormone-web-frontend && npm run lint
```

`.env.example` → `.env.local` (없어도 기본값으로 동작)

| 변수 | 기본값 |
|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8085` |
| `VITE_WS_URL` | `ws://localhost:8085/ws` |
| `VITE_DEMO_USER_ID` | `1` |

> ### ❌ `npm run build` 는 지금 깨져 있다
> `vite build` 가 `"2,821 modules transformed"` 직후 **네이티브 크래시**로 죽는다
> (`0xC0000409` STATUS_STACK_BUFFER_OVERRUN).
> **코드 문제가 아니다** — 커밋된 상태로 되돌려도 똑같이 죽는 걸 확인했다.
> Vite 8 의 Rolldown 네이티브 바이너리 문제로 보인다.
> `npm run dev` 는 정상이고 시연은 dev 로 하므로 당장 막히지 않는다.
> **이걸 고치려고 코드를 뒤지지 말 것.** 환경 문제다.

## 4-2. 데이터가 흐르는 길 — 층이 정해져 있다

```
화면 컴포넌트 (pages/, components/)
      │  ← 반드시 이 창구만 쓴다
      ▼
   api/index.js          "무엇을 보여줄지" 결정. 홈 6칸 선별, pending 대체 등
      │
      ▼
 data/simulationSource.js  캐시 + ensureLoaded / invalidateCache
      │
      ▼
   data/apiSource.js      백엔드 응답 → 화면 모양 어댑터. **가공하지 않고 통과**
      │
      ▼
   api/http.js + api/endpoints.js
      │
      ▼
     백엔드
```

> **화면 컴포넌트가 `simulationSource` 나 `apiSource` 를 직접 부르면 안 된다.**
> 반드시 `api/index.js` 를 통한다. 백엔드 계약이 바뀌면 `apiSource.normalizeTimeline` 만 고친다.

★ **프론트 목업은 제거됐다.** `src/mocks/` 없음. **백엔드가 유일한 데이터 소스**이고,
백엔드가 꺼져 있으면 화면에 연결 오류 배너가 뜨고 아무것도 진행되지 않는다.
("목업 ↔ 백엔드 토글"이라는 게 예전에 있었지만 지금은 없다.)

## 4-3. 화면 구성 — 탭 5개

| 탭 | 경로 | 성격 | 내용 |
|---|---|---|---|
| 홈 | `/` | 제품 | 예측 요약 + 생체신호 6칸 + **지난 하루 몸 상태(44개 전체)** + 안전 고지 |
| 예측 상세 | `/prediction` | 제품 | 호르몬 곡선(예측/실측 겹침) + 웨어러블 신호 + **모델 입력 47개** + 오차 카드 + 기여도 |
| 달력 | `/calendar` | 제품 | 주기 단계 색칠 + 다음 월경 예상 범위 |
| 모델 성능 | `/model` | 설득 | MAE/MAPE/상관, phase 혼동행렬, 산점도 + **Mock 경고 배너** |
| 기록 | `/history` | **운영자** | **DB 에 실제로 뭐가 쌓였나** — 날짜별 X 44개 / Y / 요청 상태 |

앞 3개는 "제품이 이렇게 생겼다", `모델 성능` 은 "모델이 이만큼 맞힌다",
`기록` 은 **파이썬 연동 후 값이 이상할 때 제일 먼저 열 자리**다.

기기 프레임 토글(PC / 모바일 / 워치)이 있다. 같은 페이지가 3가지 프레임으로 렌더된다.
→ **그래서 `sm:` `lg:` 같은 뷰포트 브레이크포인트를 쓰면 안 된다.** §4-6

## 4-4. ★ 상태를 4개로 구분한다 — 가장 중요한 로직

`api/index.js` 의 `getPredictionStatus(day)`:

| state | 뜻 | 화면 |
|---|---|---|
| `before_start` | Day 0. 하룻밤도 안 지남 | 시작 전 카드, 44개 전부 빈칸 |
| `collecting` | 예측이 **한 번도** 없음 | 콜드스타트 카드 |
| **`pending`** | 이력은 있는데 오늘 것이 아직 안 옴 | **직전 예측 유지 + "계산 중" 배지** |
| `ready` | 오늘 예측 있음 | 정상 |

**왜 이게 중요한가.** 백엔드 `advance` 는 202 를 즉시 주고 예측은 비동기로 돈다(§3-3).
예전 코드는 이랬다:

```js
const coldStart = !nextEvents;   // ❌ "오늘 예측 없음" = 콜드스타트
```

**"오늘 것이 아직 안 왔다"와 "한 번도 예측이 없었다"를 같은 것으로 취급**했다.
그래서 Day 26 에서 하루를 넘기면 26일치 예측을 이미 갖고 있는데도
화면이 **"데이터 수집 중"으로 되돌아갔다가** 왔다. Mock 이라 0.3~0.6초 깜빡임이었지만
**파이썬이 붙으면 몇 초짜리가 된다.**

지금은 `pending` 이면 **직전 예측이 있는 스냅샷으로 대체**해서 내려준다:

| 함수 | pending 동작 |
|---|---|
| `getPredictionSummary` | 직전 예측 + `isPending` / `predictionDay` |
| `getNextEvents` | 직전 예측. **null 이면 진짜 콜드스타트** |
| `getContributions` | 직전 값 유지 (막대가 사라졌다 나타나는 깜빡임 방지) |
| `getAccuracyToday` | 직전 값 유지 — null 이면 카드가 사라져 2열 그리드가 출렁인다 |
| `getHormoneSeries` | **변경 없음.** 오늘이 null 이라 선이 어제까지만 그려진다. 그게 맞다 |

★ **직전 예측을 오늘 것처럼 보여주지 않는다.** `components/PendingNotice.jsx` 가
반드시 밝힌다: `● Day 27 예측을 계산하는 중이에요 — 지금 보이는 건 Day 26 기준입니다`.
어제 예측을 오늘 것처럼 두면 거짓말이고, 시연에서 들키면 신뢰가 통째로 날아간다.

**Day 19→20 첫 리빌은 `collecting → ready` 라서 pending 을 건너뛴다.**
시연 하이라이트에 군더더기가 안 낀다. (검증함)

## 4-5. 홈 화면

### 생체신호 6칸 — 데이터로 골랐다

90일 실측에서 배란기 신호 강도(|구간평균차| ÷ 표준편차)와 결측률을 재서 골랐다. 도메인 감이 아니다.

| 신호 (DB 컬럼) | 강도 | 결측 |
|---|---|---|
| HRV (`rmssd`) | 0.39 | 0% |
| 안정시 심박 수면 (`sleep_resting_heart_rate`) | **0.79** | 2.2% |
| 뒤척임 (`restlessness`) | **0.69** | 2.2% |
| 스트레스 점수 (`stress_score`) | **0.68** | 4.4% |
| 야간 피부온도 (`nightly_temperature`) | 0.17 | 1.1% |
| 소모 칼로리 (`calories`) | 0.08 | 0% |

**제외한 것과 이유:**

| 신호 | 왜 뺐나 |
|---|---|
| `glucose_mean` | 이 참가자(2024구간)는 CGM 미착용 → **90일 내내 100% 결측** |
| `resting_heart_rate`(일간) | 배란기 신호 **0.01**. 사실상 없다 |
| `steps` | 원본에 손상값 + 20% 결측. §5-5 |
| `in_default_zone_3` | 강도 0.60 으로 세지만 90일 중앙값이 0, 최댓값 11분 → 거의 매일 "0분"인 죽은 칸 |

### "평소 대비" 는 3일치가 쌓여야 나온다

백엔드 baseline 이 "지금까지 수집된 값의 평균"이라(§3-4), Day 1 에는 baseline 이
그날 값 자신이 되어 **6칸 전부 "평소와 같음"** 이 떴다. 비교한 것처럼 보이는데 비교할 게 없다.

→ `getVitals` 가 키별로 "실제로 값이 있었던 날 수"를 세서
`MIN_BASELINE_DAYS = 3` 미만이면 baseline 을 `null` 로 내린다. 화면은 `—` 를 보여준다.
**결측일은 세지 않는다** (`steps` 처럼 띄엄띄엄 들어오는 피처가 있어서).

## 4-6. 웨어러블 44개를 보여주는 두 자리

`components/cards/ModelInputPanel.jsx` 하나를 `variant` 로 나눠 쓴다.
같은 데이터인데 **보는 사람이 다르고 답하는 질문이 다르다.**

| | 홈 (`variant="body"`) | 예측 상세 (`variant="model"`) |
|---|---|---|
| 제목 | 지난 하루 몸 상태 — 전체 신호 | 모델 입력 신호 |
| 큰 숫자 | `41개 신호가 들어왔어요 (전체 44개 중)` | `47개 = 웨어러블 44 + 정적 3` |
| 질문 | "어제 내 몸이 어땠나" | "웨어러블 몇 개나 쓰나" |

둘 다 **접힌 상태(점 44칸)로 시작**한다. 펼치면 그룹별 격자(수면7 / 호흡4 / 심박·HRV8 /
체온3 / 활동·운동10 / 심박존7 / 대사·기타5).

★ **결측 칸을 숨기지 않는다.** 지우면 44개를 매일 다 채우는 것처럼 보이는데 사실이 아니다.
이 참가자는 하루 31~41개다.

### ⚠️ 뷰포트 브레이크포인트를 쓰지 말 것

기기 프레임 토글이 있어서, **PC 창(1280px) 안에서 모바일 프레임(카드 폭 332px)** 을 볼 수 있다.
`sm:` `lg:` 는 뷰포트를 보므로 이때 4열이 유지돼 **라벨이 22개 잘렸다.**

→ 카드 루트에 `@container`, 격자에 `@[26rem]:` `@[40rem]:` (Tailwind 4 컨테이너 쿼리).

## 4-7. 기록 탭 (`pages/HistoryLog.jsx`)

**최신순, 한 페이지 10일**, 하단 페이저(버튼 최대 5개, 현재 페이지 중심으로 창이 밀림).
하루를 넘기면 1페이지로 돌아간다 — 3페이지 보다가 새 기록이 안 보이면 "왜 안 늘지?" 하게 되니까.

```
Day 59  2026-10-10  [X 41/44]  [Y 예측됨]  가임기 (실측 가임기)  412ms  mock-v1
Day  5  2026-08-17  [X 37/44]  [Y 없음 (수집만)]                        —
Day 60  2026-10-11  [X 38/44]  [요청 실패]                        72ms  —
```

펼치면 3개 섹션:

| 섹션 | 내용 |
|---|---|
| **Y — 내놓은 출력** | LH/E2/PdG 예측·실측·오차%, 주기단계 일치여부, 모델버전, 확신도, 다음 월경 예상+범위 |
| **요청 — 모델에 어떻게 보냈나** | 상태 / 소요ms / 보낸 일수 / **피처 개수** / 에러 메시지 / 응답 원문 앞부분 |
| **X — 받은 입력 41/44** | 44개를 그룹별로. **DB 컬럼명을 monospace 로**, 결측은 `null` |

**설계 의도 3가지:**

1. **한글 라벨이 아니라 DB 컬럼명을 찍는다.** 파이썬 쪽과 대조할 때 "야간 피부온도"로는 못 찾는다. `nightly_temperature` 라고 적혀 있어야 쓴다.
2. **결측은 `—` 가 아니라 `null`.** 0 과 구분돼야 하고, 그게 실제로 전달되는 값이다.
3. **`피처` 가 44 가 아니면 빨갛게 뜬다.** 직렬화 설정 하나로 40개가 나간 전례가 있다(§5-4).

`보낸 일수` 로 `MODEL_INPUT_MODE` 가 의도대로인지 확인한다.

## 4-8. 프론트에서 절대 하면 안 되는 것

| 하지 말 것 | 이유 |
|---|---|
| `Mock 예측기` 배지 / 모델 성능 경고 배너 제거 | Mock 을 실제 성능으로 오인하게 만든다 |
| 안전 고지(`SafetyNotice`) 제거 | 의료 진단 아님 / 피임·임신 목적 사용 금지 |
| 호르몬 곡선과 웨어러블 차트를 **좌우로** 배치 | X축이 정렬돼야 "LH 서지 때 HRV 하락"이 읽힌다. 반드시 세로 |
| 두 차트의 `domain` / `ticks` 를 다르게 | 눈금이 어긋나면 대응을 못 읽는다 |
| `sm:` `lg:` 뷰포트 브레이크포인트 | §4-6 |
| effect 안에서 `setState` | `react-hooks/set-state-in-effect` 로 lint 가 막는다. 렌더 시점 파생을 쓸 것 |

## 4-9. 알아두면 좋은 자잘한 것

- **`dataRevision` 카운터** — 예측이 웹소켓으로 늦게 도착할 때 `currentDay` 는 그대로라
  zustand 가 변경으로 안 본다. 그래서 리렌더 트리거용 카운터를 따로 둔다.
- **`useCountUp`** — StrictMode 이중 마운트에 안전하게 만들어져 있다.
  `displayRef` 를 콜백에서만 전진시키고 `onComplete` 로 최종값을 고정한다.
- **스크러버는 읽기 전용이다.** 드래그해서 날짜를 점프할 수 없다.
  Day 19 까지 미리 가려면 **자동재생 4x**(약 12초) 또는 `advance` API 를 19번 호출한다.

---

# 5. 데이터 지식 — 이걸 모르면 값이 왜 그런지 이해 못 한다

## 5-1. 원본 컬럼 55개의 분류

`merge.csv` (= `merged_nan.xlsx`, mcPHASES). 전체 5,436행 / 2022구간 3,516행·40명 / 2024구간 1,920행·20명.

| 분류 | 개수 | 컬럼 | 용도 |
|---|---|---|---|
| 🟩 안 보냄 | 4 | `id`, `study_interval`, `is_weekend`, `day_in_study` | 식별자/파생값 |
| 🟨 예측 대상 (Y) | 4 | `phase`, `lh`, `estrogen`, `pdg` | 모델이 맞혀야 하는 것 |
| 🟧 정적 (DB 에 이미 있음) | 3 | `birth_year`, `age_of_first_menarche`, `ethnicity` | 모델 입력 |
| ⬜ 웨어러블 (X) | **44** | 나머지 전부 | 모델 입력 |

**모델 입력 = 44 + 3 = 47개.**

### 웨어러블 44개 — **모델 피처명 기준**, 요청에 나가는 순서

```
sedentary, lightly, moderately, very,
FAT_BURN, CARDIO, PEAK,
altitude, calories,
temperature_samples, nightly_temperature,
filtered_demographic_vo2_max, spo2_variation_std,
originalduration, averageheartrate, exercise_calories, steps,
glucose_mean, glucose_std,
bpm, bpm_min, bpm_max,
value, resting_heart_rate,                     ← ★ 여기가 교차 매핑
rmssd, low_frequency, high_frequency,
full_sleep_breathing_rate, deep_sleep_breathing_rate,
light_sleep_breathing_rate, rem_sleep_breathing_rate,
minutesasleep, efficiency, minutesawake, nap_minutes_total,
overall_score, deep_sleep_in_minutes, restlessness,
stress_score,
in_default_zone_3, in_default_zone_2, in_default_zone_1, below_default_zone_1,
temperature_diff_from_baseline
```

## 5-2. 시연에 쓰는 참가자

**`id=22` / `study_interval=2024` / `day_in_study 862~951` (90일)**

| 항목 | 값 |
|---|---|
| 전형성 순위 | 20명 중 **2위** |
| 결측률 | 11.1% |
| `pdg` | 90/90일 존재 |
| 주기 | **3개** |

→ **이 참가자는 test 로 빼고 학습에서 제외해야 한다.** (모델팀에 전달 필요)

### 실제 주기 구조 (`truth.phase` 라벨 기준)

| 주기 | 월경 시작 | 난포기 | 가임기 | 황체기 | LH 최고 |
|---|---|---|---|---|---|
| 1 | D8 | D16~23 | **D24~31** | D32~40 | **D28 (35.9)** |
| 2 | D41 | D49~54 | **D55~62** | D63~71 | **D59 (41.6)** |
| 3 | D72 | D80~85 | **D86~90** | — | D79 (14.2) |

주기 길이 **33일 / 31일**. **28일 하드코딩 금지.**

호르몬 실측 피크: Estrogen D27(251.7) / D60(243.8), PdG D36(30.0) / D67(30.0).

## 5-3. ★★ 안정시 심박 2개 컬럼 — 교차 매핑. 절대 "고치지" 말 것

```
DB resting_heart_rate       (일간, 소수 72.3)  ->  모델 피처명 "value"
DB sleep_resting_heart_rate (수면중, 정수 75)  ->  모델 피처명 "resting_heart_rate"
```

**근거:** mcPHASES 원본에서 `resting_heart_rate.csv` 의 값 컬럼명이 `value` 이고,
`sleep_score.csv` 의 컬럼명이 `resting_heart_rate` 다.

`ModelInputBuilderTest.restingHeartRateCrossMapping()` 이 이걸 고정한다. 테스트를 지우지 말 것.

**두 값은 신호 강도가 완전히 다르다** (90일 3주기 평균):

| | 난포기 | 가임기 | 황체기 | 월경기 |
|---|---|---|---|---|
| `sleep_resting_heart_rate` (수면중) | 69.28 | 70.90 | **73.80** | 71.33 |
| `resting_heart_rate` (일간) | 74.08 | 73.89 | 72.02 | 71.20 |

**수면중에는 주기 패턴이 뚜렷하고, 일간에는 사실상 없다.**
이름만 보고 둘을 바꿔 쓰면 **에러 없이** 조용히 성능이 떨어진다.

## 5-4. 과거에 실제로 난 사고 2건 — 같은 실수를 반복하지 말 것

### ① 시드 컬럼명 사고

`extract_seed.py` 가 merged_nan 헤더(=모델 피처명)를 그대로 시드에 썼는데,
백엔드 `WearableFeatures` 는 DB 컬럼명을 기대했다. 결과:

- `value`(일간 72.3)는 **알 수 없는 키라 조용히 버려짐**
- `resting_heart_rate`(수면중 75)가 **일간 컬럼 자리에 들어감**

→ 모델이 `value=75`, `resting_heart_rate=null` 을 받았다. **에러가 하나도 안 났다.**

**대책 (지금 코드에 들어 있음):**
- `extract_seed.py` 에 `MERGED_TO_DB_COLUMN` 매핑 추가
- `WearableFeatures.applyToEntity` 가 **모르는 키를 만나면 `log.warn`** 을 남긴다
- 회귀 테스트 2개 추가

### ② `NON_NULL` 직렬화 사고

전역 ObjectMapper 가 `NON_NULL` 이라, 모델 요청을 객체로 넘기니
**결측 피처 4개가 통째로 사라져 44개가 40개로** 나갔다.
`prediction_job.request_payload` 를 보고 발견했다.

**대책:** `PythonPredictionClient` 가 전용 매퍼(`Include.ALWAYS`)로
**String 까지 직렬화한 뒤 전송**한다. + 회귀 테스트.
+ 기록 탭이 `피처 44개` 를 표시하고 44 가 아니면 빨갛게 띄운다.

## 5-5. 원본 데이터의 문제 3개 (모델팀에 알려야 함)

### ① `pdg` 와 `glucose` 는 절대 같이 존재하지 않는다

| 컬럼 | 전체 | 2022구간 | 2024구간 |
|---|---|---|---|
| `pdg` | 64.7% | **100%** | 0% |
| `glucose_mean` / `glucose_std` | 43.9% | 13.2% | **100%** |
| `sedentary` | 36.7% | 2.2% | **100%** |

결측이 랜덤이 아니라 **구간에 따라 통째로 없는 구조적 결측**이다.

**`pdg` 를 예측하는 모델을 학습시키면 자동으로 2024구간만 쓰게 되고,
그 구간에는 위 3개가 항상 없다 → `pdg` 모델은 사실상 41개 피처로 학습된다.**

반대로 `glucose` 를 쓰면 학습 데이터가 2022구간으로 좁혀지고 `pdg` 라벨이 사라진다.
결측을 평균/0 으로 임퓨트하면 이 구조가 가려져서 모델이 "구간 구분자"를 학습할 수 있다.

### ② `steps` 에 물리적으로 불가능한 값

```
id=22 / day_in_study 883:  steps=626,947   운동시간=274분   운동칼로리=61
```

274분에 62만 걸음 = **분당 2,270보.** 불가능하다.
데이터셋 전체 `steps` 상위 **0.30%** 가 2만 보 초과, 최댓값 **938,808**.

**원본은 고치지 않았다.** 모델팀이 알아야 할 문제라 지우면 안 된다.
대신 홈 타일에서 내리고 44개 패널에만 남겼다.

### ③ `steps` 는 하루 총 걸음 수가 아니다

`originalduration` / `averageheartrate` / `exercise_calories` 와
**항상 같이 있거나 같이 없다** (90일 표본 예외 0건). 결측률도 거의 같다
(69.2% / 68.8% / 68.7% / 68.7%).

`exercise.csv`(기록된 운동 세션)에서 온다. **운동을 기록 안 한 날은 통째로 결측.**
하루 활동량 대용으로 쓰면 의미가 어긋난다. 필요하면 `calories`(전체 결측 0.3%) 쪽이 맞다.

## 5-6. 단위 — 잘못 알기 쉬운 것

| 컬럼 | 실제 | 오해 |
|---|---|---|
| `glucose_mean` | **mmol/L** (실측 5.4~6.6) | mg/dL 아님 |
| `nightly_temperature` | **Fitbit 피부온도** (실측 31~35°C) | 체온(36.5°C) 아님. **BBT 곡선이 아니다** |
| `originalduration` | **밀리초** (5,334,000 = 89분) | 초/분 아님 |

## 5-7. 시연에 쓸 수 있는 신호 / 쓰면 안 되는 신호

90일 3주기, `truth.phase` 라벨 기준 평균:

| 신호 | 난포기 | 가임기 | 황체기 | 월경기 | 판정 |
|---|---|---|---|---|---|
| `rmssd` (HRV) | 53.49 | **47.08** | 47.39 | 52.09 | ✅ 가임기 12% 하락. **주기1만 보면 50.78 → 37.69 (26%)** |
| `sleep_resting_heart_rate` | 69.28 | 70.90 | **73.80** | 71.33 | ✅ 황체기까지 단조 상승 |
| `resting_heart_rate` (일간) | 74.08 | 73.89 | 72.02 | 71.20 | ❌ 패턴 없음 |
| `stress_score` | 78.33 | 76.38 | 74.46 | 73.39 | ⚠️ 방향은 있으나 약함 |
| `restlessness` | 0.08 | 0.08 | **0.10** | 0.09 | ⚠️ 절대값이 작다 |
| `nightly_temperature` | 33.20 | 33.40 | 33.63 | 33.51 | ⚠️ **BBT 곡선 아님.** 그렇게 설명하지 말 것 |
| `overall_score` (수면 점수) | 78.28 | 80.38 | 82.64 | 79.96 | ❌ 신호 없음 |

> **시연 문구는 `HRV 하락` + `수면중 안정시 심박 상승` 둘로 좁히는 게 정직하고 설득력도 높다.**
> 체온·수면점수를 근거로 내세우면 데이터가 뒷받침하지 않는다.

## 5-8. 결측은 예외가 아니라 기본 상태

**결측은 `null` 그대로 둔다. `0` 으로 채우지 않는다.**
"측정 안 됨"과 "값이 0"이 구분돼야 한다. 학습 데이터 결측률이 최대 69% 다.

시연 참가자 기준 하루 44개 중 **31~41개**만 값이 있다.
`sedentary` / `glucose_mean` / `glucose_std` 3개는 **90일 내내 비어 있다.**

---

# 6. 파이썬 모델 연동

## 6-1. 켜는 법 — 코드는 한 줄도 안 고쳐도 된다

```bash
MODEL_ENABLED=true MODEL_BASE_URL=http://<주소>:<포트> ./gradlew bootRun
```

`PredictionClient` 인터페이스로 분리돼 있어서 `@ConditionalOnProperty` 가 알아서 갈아끼운다.
계약이 다르면 **`dto/model/ModelPredictRequest.java` 와 `ModelPredictResponse.java` 두 파일만** 고친다.

## 6-2. 요청 (제안안 — 미확정)

```jsonc
POST {MODEL_BASE_URL}/predict          // Content-Type: application/json
                                        // MODEL_API_KEY 있으면 X-API-Key 헤더
{
  "userId": 1,
  "targetDate": "2026-09-03",
  "staticInfo": {
    "birthYear": 2002,              // 생년월일이 아니라 연도만
    "ageOfFirstMenarche": 10,
    "ethnicity": "Southeast Asian"  // 문자열 그대로. 인코딩 필요하면 협의
  },
  "history": [
    { "date": "2026-08-13", "features": { /* 44개. 결측은 null. 키는 항상 44개 */ } }
    // ... targetDate 까지 오름차순
  ]
}
```

`history` 길이는 `MODEL_INPUT_MODE` 로 정한다:

| 모드 | 길이 |
|---|---|
| `SINGLE_DAY` | 1 |
| **`FULL_HISTORY`** (기본) | 누적 전체 (최대 90) |
| `WINDOW` | 최근 N일 (`MODEL_WINDOW_SIZE`, 기본 20) |

## 6-3. 응답 (제안안 — 미확정)

```jsonc
{
  "userId": 1,
  "targetDate": "2026-09-03",
  "modelVersion": "lgbm-v0.3",       // 화면 배지에 그대로 뜬다. 넣어주면 좋다
  "hormones": {
    "lh":       { "value": 35.8,  "confidence": 0.82 },
    "estrogen": { "value": 243.1, "confidence": 0.77 },
    "pdg":      { "value": null,  "confidence": null }   // 못 하면 null. 0 아님
  },
  "phase": {
    "label": "Fertility",            // Menstrual | Follicular | Fertility | Luteal
    "confidence": 0.88,
    "probabilities": { "Menstrual": 0.02, "Follicular": 0.08, "Fertility": 0.88, "Luteal": 0.02 }
  },
  "nextPeriod": { "predictedDate": "2026-09-22", "rangeStart": "2026-09-20", "rangeEnd": "2026-09-24" },
  "contributions": [ { "feature": "rmssd", "weight": 0.42, "direction": "down", "signal": "HRV" } ],
  "error": null
}
```

## 6-4. ★ 슈퍼셋 가정 — 부분 응답을 허용한다

**응답 필수 필드가 하나도 없다.** 전부 optional 이다.

| 상황 | 백엔드 처리 |
|---|---|
| 필드가 없음 | `null` 로 파싱. **기존 값을 지우지 않고 병합** (`PredictionResult.mergeFrom`) |
| 모르는 필드가 옴 | 버리지 않고 `prediction_result.raw_response` 에 원문 보관 |
| `hormones.lh.value` 가 `null` | "이번엔 예측 못 함". 화면에 `-` |
| 4xx/5xx 또는 `error.code` | `prediction_job.status=FAILED`, 화면은 직전 예측 유지 |

**즉 처음엔 `phase` 만 보내도 돌아간다.** 나중에 호르몬을 추가하면 그때부터 화면이 채워진다.
모델이 죽어도 데모는 계속 돌아가고 예측만 안 갱신된다.

★ `phase.label` 은 **4개 고정**이다. 5번째가 필요하면 **미리** 말해야 한다 —
DB 에 CHECK 제약이 걸려 있다(§2-3).

## 6-5. 모델팀에 확인해야 할 것

1. 모델 입력이 정말 47개가 맞나? `day_in_study` / `is_weekend` 를 쓰나?
2. `history` 를 며칠치 보내면 되나?
3. 결측을 `null` 로 보내면 되나, 백엔드가 채워야 하나? (지금은 `null` 그대로)
4. 응답 형태가 §6-3 대로 되나?
5. `phase` 라벨이 4개가 맞나?
6. `modelVersion` 문자열을 넣어줄 수 있나? (Mock/실모델 구분의 유일한 근거)
7. 피처 기여도(`contributions`)를 줄 수 있나? (SHAP이든 tree importance든)
8. 예측 1건에 몇 초 걸리나? (타임아웃 기본 30초)
9. `ethnicity` 를 문자열 그대로 보내면 되나? 인코딩 필요하면 매핑표
10. 서버 주소/포트, API 키 필요 여부

**그리고 §5-5 의 데이터 품질 문제 3개를 반드시 전달할 것.**

## 6-6. 붙인 뒤 문제가 생기면 — `기록` 탭이 1차 진단이다

실패 경로는 **실제로 확인했다** (`MODEL_ENABLED=true` + 서버 없음):

```
Day 60  2026-10-11  [X 38/44]  [요청 실패]  72ms  —
  └ 상태 실패 · 소요 72ms · 보낸 일수 60일 · 피처 44개
    I/O error on POST request for "http://127.0.0.1:5000/predict": Connection refused
```

| 볼 것 | 의미 |
|---|---|
| `피처` 가 44 가 아님 | **직렬화 버그.** §5-4 ② |
| `보낸 일수` | `MODEL_INPUT_MODE` 가 의도대로인지 |
| `소요` | 타임아웃 조정 필요 여부 |
| 응답 원문 | Mock 은 HTTP 를 안 타서 비어 있다. **파이썬이면 여기 찍힌다** |

---

# 7. 부록

## 7-1. 시드 재생성 스크립트 (`merge.csv` → `seed_data.json`)

> **보통 필요 없다.** `seed_data.json` 이 백엔드 저장소에 들어 있다.
> 참가자를 바꾸거나 기간을 늘릴 때만 쓴다.
>
> 아래를 `regen_seed.py` 로 저장하고 실행:
> `python regen_seed.py merge.csv hormone-web-backend/src/main/resources/db/seed_data.json`

```python
#!/usr/bin/env python3
"""merge.csv (또는 merged_nan.xlsx) -> 백엔드 시드 JSON.

표준 라이브러리만 쓴다 (pandas/openpyxl 불필요).

★ 이 스크립트가 존재하는 이유의 절반은 컬럼명 매핑이다.
  원본 헤더는 '모델 피처명'이고 백엔드가 기대하는 건 'DB 컬럼명'이다.
  둘을 안 바꾸면 값 2개가 조용히 뒤바뀐다 (실제로 사고가 났다).
"""
import csv, json, os, re, sys, zipfile
import xml.etree.ElementTree as ET
from datetime import date, timedelta

PARTICIPANT_ID     = "22"
STUDY_INTERVAL     = "2024"
START_DAY_IN_STUDY = 862
TOTAL_DAYS         = 90
COLD_START_DAYS    = 20
DEMO_START_DATE    = date(2026, 8, 13)   # Day 1 의 달력 날짜

# 🟩 모델에 안 보내는 컬럼
GREEN = {"id", "study_interval", "is_weekend", "day_in_study"}
# 🟨 예측 대상 (Y). 시드의 truth 로 들어간다
YELLOW = ["phase", "lh", "estrogen", "pdg"]
# 🟧 정적 피처
ORANGE = {"birth_year", "age_of_first_menarche", "ethnicity"}

# ★★ 원본 헤더(모델 피처명) -> DB 컬럼명. 여기 없는 건 이름이 같다.
#    resting_heart_rate.csv 의 값 컬럼명이 'value',
#    sleep_score.csv 의 컬럼명이 'resting_heart_rate' 라서 이렇게 엇갈린다.
MERGED_TO_DB_COLUMN = {
    "value": "resting_heart_rate",
    "resting_heart_rate": "sleep_resting_heart_rate",
}

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"


def read_csv(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        r = csv.DictReader(f)
        return list(r.fieldnames), list(r)


def read_xlsx(path):
    z = zipfile.ZipFile(path)
    sst = ["".join(t.text or "" for t in si.iter(NS + "t"))
           for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall(NS + "si")]
    data = z.read("xl/worksheets/sheet1.xml").decode("utf-8")
    row_re = re.compile(r'<row [^>]*r="(\d+)"[^>]*>(.*?)</row>', re.S)
    cell_re = re.compile(r'<c r="([A-Z]+)\d+"((?:\s+[a-z]+="[^"]*")*)\s*(?:/>|>(.*?)</c>)', re.S)
    t_re, v_re = re.compile(r'\bt="(\w+)"'), re.compile(r"<v>([^<]*)</v>")
    rows = row_re.findall(data)

    header = {}
    for ref, attrs, inner in cell_re.findall(rows[0][1]):
        t, v = t_re.search(attrs), v_re.search(inner or "")
        header[ref] = sst[int(v.group(1))] if (t and t.group(1) == "s" and v) else (v.group(1) if v else "")

    records = []
    for _, body in rows[1:]:
        rec = {}
        for ref, attrs, inner in cell_re.findall(body):
            t, v = t_re.search(attrs), v_re.search(inner or "")
            if not v:
                continue
            raw = v.group(1)
            rec[header[ref]] = sst[int(raw)] if (t and t.group(1) == "s") else raw
        records.append(rec)
    return list(header.values()), records


def num(v):
    """빈 값 -> None. ★ 결측을 0 으로 바꾸지 않는다."""
    if v is None or v == "" or str(v).strip().lower() in ("nan", "na", "null"):
        return None
    try:
        f = float(v)
    except (TypeError, ValueError):
        return v
    return int(f) if f.is_integer() else round(f, 6)


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "merge.csv"
    out = sys.argv[2] if len(sys.argv) > 2 else "seed_data.json"

    cols, rows = (read_xlsx(src) if src.lower().endswith((".xlsx", ".xlsm"))
                  else read_csv(src))

    feature_cols = [c for c in cols if c not in GREEN and c not in YELLOW and c not in ORANGE]
    if len(feature_cols) != 44:
        sys.exit(f"웨어러블 피처가 44개가 아닙니다: {len(feature_cols)}개\n{feature_cols}")

    sel = [r for r in rows
           if str(r.get("id")) == PARTICIPANT_ID
           and str(r.get("study_interval")) == STUDY_INTERVAL]
    sel.sort(key=lambda r: int(float(r["day_in_study"])))
    win = [r for r in sel
           if START_DAY_IN_STUDY <= int(float(r["day_in_study"])) < START_DAY_IN_STUDY + TOTAL_DAYS]
    if len(win) != TOTAL_DAYS:
        sys.exit(f"기간에 {len(win)}행뿐입니다 (기대 {TOTAL_DAYS}). 참가자/구간/시작일을 확인하세요.")

    first = win[0]
    seed = {
        "_comment": "regen_seed.py 로 생성됨. 직접 수정하지 말 것.",
        "source": {
            "dataset": os.path.basename(src),
            "participantId": PARTICIPANT_ID,
            "studyInterval": STUDY_INTERVAL,
            "dayInStudyRange": [START_DAY_IN_STUDY, START_DAY_IN_STUDY + TOTAL_DAYS - 1],
        },
        "user": {
            "id": 1,
            "name": "데모 사용자",
            "birthYear": num(first.get("birth_year")),
            "ageOfFirstMenarche": num(first.get("age_of_first_menarche")),
            "ethnicity": first.get("ethnicity") or None,
        },
        "startDate": DEMO_START_DATE.isoformat(),
        "totalDays": TOTAL_DAYS,
        "coldStartDays": COLD_START_DAYS,
        "days": [],
    }

    for i, r in enumerate(win):
        # ★ 여기서 원본 헤더를 DB 컬럼명으로 바꾼다
        wearable = {MERGED_TO_DB_COLUMN.get(c, c): num(r.get(c)) for c in feature_cols}
        truth = {k: num(r.get(k)) if k != "phase" else (r.get("phase") or None) for k in YELLOW}
        seed["days"].append({
            "dayIndex": i + 1,
            "date": (DEMO_START_DATE + timedelta(days=i)).isoformat(),
            "wearable": wearable,
            "truth": truth,
        })

    with open(out, "w", encoding="utf-8") as f:
        json.dump(seed, f, ensure_ascii=False, indent=2)

    # --- 검증 출력 ---
    d1 = seed["days"][0]["wearable"]
    print(f"생성: {out}  ({TOTAL_DAYS}일 x {len(feature_cols)}피처)")
    print(f"  Day 1 측정된 피처: {sum(1 for v in d1.values() if isinstance(v, (int, float)))}/44")
    print(f"  ★ 교차 매핑 확인 (두 값이 달라야 정상):")
    print(f"      resting_heart_rate       (일간)   = {d1.get('resting_heart_rate')}")
    print(f"      sleep_resting_heart_rate (수면중) = {d1.get('sleep_resting_heart_rate')}")
    always_missing = [k for k in d1
                      if all(not isinstance(d['wearable'][k], (int, float)) for d in seed["days"])]
    print(f"  90일 내내 결측: {len(always_missing)}개 {always_missing}")


if __name__ == "__main__":
    main()
```

**재생성 후 반드시 할 것:**

```sql
-- 기존 시드를 지워야 DemoSeedLoader 가 새로 넣는다 (멱등이라 있으면 건너뛴다)
DELETE FROM wearable_daily;
DELETE FROM prediction_result;
DELETE FROM prediction_job;
DELETE FROM demo_seed_wearable;
DELETE FROM demo_session;
```

그다음 백엔드 재시작.

## 7-2. 소스 트리

<details>
<summary>백엔드 (<code>src/main/java/com/sixletter/hormone_web_backend/</code>)</summary>

```
HormoneWebBackendApplication.java
config/       AsyncConfig, DemoProperties, ModelProperties,
              RestClientConfig, WebConfig, WebSocketConfig
controller/   DemoController ★, PredictionController, UserController, WearableDailyController
dto/          ApiError, DemoAdvanceDto, DemoStateDto, DemoTimelineDto,
              PredictionDto, PredictionEventDto, PredictionJobDto, UserDto, WearableDailyDto
dto/model/    ModelPredictRequest ★, ModelPredictResponse ★
entity/       Contribution, CyclePhase, CyclePhaseConverter, DemoSeedWearable,
              DemoSession, JobStatus, PredictionJob, PredictionResult, User, WearableDaily
exception/    GlobalExceptionHandler, NotFoundException
repository/   DemoSeedWearableRepository, DemoSessionRepository, PredictionJobRepository,
              PredictionResultRepository, UserRepository, WearableDailyRepository
service/      DemoSeedLoader, DemoService ★, HormonePredictionService,
              MockPredictionClient, ModelInputBuilder ★, PredictionClient, PythonPredictionClient
support/      WearableFeatures ★★
```

`src/main/resources/db/` — `schema.sql`, `drop.sql`, `seed_data.json`
</details>

<details>
<summary>프론트 (<code>src/</code>)</summary>

```
App.jsx, main.jsx
api/          endpoints.js, http.js, index.js ★★
data/         apiSource.js ★, simulationSource.js
lib/          wearableCatalog.js ★★, navItems.js, phase.js, formatDate.js,
              calendarGrid.js, useCalendarNotes.js, useChangeFlash.js,
              useCountUp.js, usePredictionSocket.js
state/        simulationStore.js ★, useSimulationAutoplay.js
pages/        Home.jsx, PredictionDetail.jsx, Calendar.jsx,
              ModelPerformance.jsx, HistoryLog.jsx
components/   DataSourceBadge.jsx, PendingNotice.jsx, SafetyNotice.jsx
components/cards/      AccuracyCard, ColdStartCard, ContributionBars, CycleCalendar,
                       HormoneChart, ModelInputPanel ★, PredictionSummaryCard,
                       VitalsSummary, WearableSignalChart
components/calendar/   DiaryPanel, MonthGrid, PhaseLegend
components/devices/    PCFrame, MobileFrame, WatchFrame
components/simulator/  SimulatorBar
```
</details>

> ★ **피처를 추가·변경할 때는 두 파일을 같이 고친다** —
> 백엔드 `support/WearableFeatures.java`(DB 컬럼명)와
> 프론트 `lib/wearableCatalog.js`(라벨·단위·소수자리·그룹).
> **키가 1:1 로 맞아야 한다.** 현재 44/44 일치 확인됨.

## 7-3. 검증된 것 / 안 된 것

### ✅ 검증됨

| 항목 | 결과 |
|---|---|
| 백엔드 테스트 | `ModelInputBuilderTest` 6개 통과 (exit 0) |
| 프론트 lint | 통과 (exit 0) |
| 프론트 카탈로그 ↔ 백엔드 피처 | **44/44 완전 일치** |
| 90일 E2E (Mock) | phase 71/71, LH MAE 0.24, 다음 월경 D41 예측 → 실제 D41 |
| 기록 탭 X 개수 | Day 5 / 59 / 60 → 37 / 41 / 38 — **셋 다 시드와 일치** |
| 모델 요청 피처 수 | 41일 내내 44개 유지 (`prediction_job` 확인) |
| pending 전환 | 클릭 후 3초간 50ms×60프레임 샘플 — **콜드스타트 전환 0회**, 카드 6개 고정 |
| Day 19→20 첫 리빌 | `collecting → ready` 라 pending 을 건너뜀 |
| 실패 경로 | `MODEL_ENABLED=true` + 서버 없음 → `요청 실패` 배지 + 사유 표시 확인 |
| 반응형/다크모드 | PC·모바일 프레임, 라벨 잘림 0건, 가로 오버플로 없음 |
| 브라우저 콘솔 | 에러 0건 |

### ❌ 안 됨 / 미해결

| 항목 | 상태 |
|---|---|
| **`npm run build`** | **네이티브 크래시.** 환경 문제, 코드 무관. §4-1 |
| **파이썬 연동 E2E** | 실제 파이썬 서버와 붙여본 적 없다. 실패 경로만 확인 |
| 시연 리허설 | 처음부터 끝까지 돌려본 적 없다 |
| `overdue` / `insufficient_data` 상태 | 의도적 보류 |
| 예정일 변동 안내("어제보다 하루 늦춰졌어요") | 의도적 보류 |

## 7-4. 자주 쓰는 명령

```bash
cd hormone-web-backend && ./gradlew test
```

```bash
cd hormone-web-frontend && npm run lint
```

```bash
curl http://localhost:8085/api/demo/users/1/state
```

```bash
curl -X POST http://localhost:8085/api/demo/users/1/reset
```

```bash
curl -s http://localhost:8085/api/demo/users/1/jobs | head -c 600
```

Day 19 까지 한 번에 진행 (스크러버로는 못 간다):

```bash
for i in $(seq 1 19); do curl -s -X POST http://localhost:8085/api/demo/users/1/advance -o /dev/null; done
```

> 터미널로 진행했으면 **브라우저를 새로고침해야** 화면이 따라온다.

## 7-5. ⚠️ 보안

**`application-local.yaml` 은 저장소에 없다.** DB 비밀번호와 Anthropic API 키가
들어 있어서 `.gitignore` 로 제외했다. 저장소가 public 이고, 실제로 GitHub
push protection 이 Anthropic 키를 차단했다 (`GH013 - Push cannot contain secrets`).

**다른 기기에서 할 일:** `.example` 을 복사하고 **MySQL 비밀번호만** 채우면 된다.
Anthropic 키는 현재 코드에 사용처가 없어서 비워 둬도 전부 동작한다.

```bash
cd hormone-web-backend/src/main/resources
cp application-local.yaml.example application-local.yaml
```

환경변수로 넣어도 된다:

```bash
DB_PASSWORD=<비밀번호> ./gradlew bootRun
```

자세한 건 §2-2.
