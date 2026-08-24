# hormone_web

웨어러블 신호로 **여성호르몬 3종(LH / Estrogen / PdG)과 월경주기 단계**를 예측해 보여주는 PoC.
식스레터스 과제.

> ### 이건 웹 서비스가 아니다
> **기업 대표에게 예측모델을 보여주기 위한 시연물**이고, 최종 산출물은 **웹 시연 영상**이다.
> 로그인·회원가입·트래픽·배포·보안 강화는 전부 범위 밖이다. 그런 걸 추가하지 말 것.
>
> 이 문서 하나가 프로젝트의 유일한 문서다. 코드 주석과 어긋나면 **코드 주석이 더 최신일 수
> 있다** — 주석은 그 파일이 왜 그렇게 생겼는지를 담고 있고, 이 문서는 전체 지도다.

**목차**

| # | 섹션 | 내용 |
|---|---|---|
| 1 | [5분 요약](#1-5분-요약) | 무엇을 만들었나, 지금 무엇이 진짜인가 |
| 2 | [실행](#2-실행) | 3개 프로세스를 띄우는 법 |
| 3 | [DB](#3-db) | 테이블 7개 · 창고 구조 · 처음부터 만들기 |
| 4 | [백엔드](#4-백엔드) | 흐름 · API · 주요 클래스 · 금지사항 |
| 5 | [프론트엔드](#5-프론트엔드) | 화면 6탭 · 상태 분기 · 금지사항 |
| 6 | [예측모델 연동](#6-예측모델-연동) | 계약 · 관대한 파싱 · 일차 정렬 |
| 7 | [오늘의 조언](#7-오늘의-조언) | Claude 연동 · 안전 경계 · 비용 |
| 8 | [데이터](#8-데이터) | 컬럼 55개 · 교차매핑 · 원본 데이터 문제 |
| 9 | [겪은 사고들](#9-겪은-사고들) | 실제로 났던 버그와 재발 방지 장치 |
| 10 | [지금 상태와 남은 것](#10-지금-상태와-남은-것) | 검증된 것 · 미해결 · 확인 대기 |

---

# 1. 5분 요약

## 하는 일

```
Day 0     아직 하룻밤도 안 지남. 모든 값이 "측정 안 됨"
  ↓ [Day N 정보 보내기]
Day 1~19  웨어러블만 쌓인다. 예측 없음 (콜드스타트)
  ↓
Day 20    ★ 첫 예측 등장. 시연의 하이라이트
  ↓
Day 28    첫 LH 서지 (실측 35.9) / Day 59 두 번째 (41.6)
  ↓
Day 90    끝
```

시연자가 버튼을 누르면 하루가 진행되고, 백엔드가 창고에서 그날 웨어러블을 꺼내
`wearable_daily` 로 옮긴 뒤 파이썬 예측모델을 부른다. 결과는 WebSocket 으로 화면에 밀린다.

## 구성

| 프로세스 | 스택 | 포트 | 없으면 |
|---|---|---|---|
| 프론트 | React 19 / Vite 8 / Tailwind 4 / zustand 5 / recharts 3 | 5173 | 화면 없음 |
| 백엔드 | Spring Boot 4.1 / Java 21 / Gradle 9.7.1 / STOMP | 8085 | 아무것도 안 됨 |
| MySQL | 8.0 (`hormone_web`) | 3306 | 백엔드가 안 뜸 |
| **예측모델** | 파이썬 (모델팀) | 5000 | **예측이 안 나옴** |
| Claude API | 외부 | — | 조언 탭만 안 됨 |

## 지금 무엇이 진짜인가

| | 출처 | 진짜인가 |
|---|---|---|
| 웨어러블 44개 | mcPHASES 참가자 22번 실측 | ✅ 진짜 |
| 호르몬 **실측**선 (흐린 점선) | Mira 기기 측정값 | ✅ 진짜 |
| 호르몬 **예측**선 (굵은 실선) | 파이썬 모델 출력 | 모델에 달림 |
| 조언 | Claude Sonnet 5 | ✅ 진짜 호출 |

> **내장 Mock 예측기는 제거했다.** 예전에는 파이썬 없이도 시연이 완주되도록 실측 정답에
> ±8% 노이즈를 얹어 "예측"을 만드는 폴백이 있었지만, 더미값을 전부 걷어내기로 하면서 지웠다.
>
> ⚠️ **그래서 파이썬이 없으면 예측이 안 나온다.** 폴백이 없다. 시연 전에 5000번 포트가
> 살아 있는지 반드시 확인할 것. 죽어 있으면 화면에 빨간 `예측에 실패했어요` 배너가 뜬다.

---

# 2. 실행

## 준비

| 항목 | 버전 | 비고 |
|---|---|---|
| JDK | 21 | Gradle toolchain 이 자동 설치 |
| Node | 20+ | |
| MySQL | 8.0 | 데이터베이스만 만들어 두면 됨 |
| Gradle | **9.7.1** | wrapper 에 고정. 낮추지 말 것 (§9-6) |

```sql
CREATE DATABASE IF NOT EXISTS hormone_web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

## 설정

`application-local.yaml` 은 **저장소에 없다** (DB 비밀번호와 Anthropic 키가 들어가서 제외).

```bash
cd hormone-web-backend/src/main/resources
cp application-local.yaml.example application-local.yaml
```

MySQL 비밀번호를 채운다. Anthropic 키는 조언 탭을 쓸 때만 필요하고, 비워 두면 그 탭만 꺼진다.

## 띄우기

```bash
cd hormone-web-backend && ./gradlew bootRun
```

```bash
cd hormone-web-frontend && npm install && npm run dev
```

파이썬 예측 서버는 모델팀 것을 띄우고 주소를 맞춘다.

```bash
MODEL_BASE_URL=http://<주소>:<포트> ./gradlew bootRun
```

브라우저에서 http://localhost:5173

## 환경변수

**백엔드**

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | localhost:3306 / root / (없음) | |
| `SERVER_PORT` | `8085` | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | |
| `MODEL_BASE_URL` | `http://127.0.0.1:5000` | **파이썬 예측 서버** |
| `MODEL_PREDICT_PATH` | `/predict` | |
| **`MODEL_DAY_OFFSET`** | **`0`** | **일차 정렬 보정. §6-3 — 확정 전이다** |
| `MODEL_API_KEY` | (없음) | 비면 인증 헤더 안 붙임 |
| `ANTHROPIC_API_KEY` | (없음) | 비면 조언 탭 꺼짐 |
| `ANTHROPIC_MODEL` | `claude-sonnet-5` | |
| `ADVICE_HISTORY_DAYS` | `30` | 조언에 실을 웨어러블 일수 |
| `ADVICE_MAX_TOKENS` | `2000` | §9-9 |

**프론트** — `.env.example` → `.env.local` (없어도 기본값으로 동작)

| 변수 | 기본값 |
|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8085` |
| `VITE_WS_URL` | `ws://localhost:8085/ws` |
| `VITE_DEMO_USER_ID` | `1` |

> ### ❌ `npm run build` 는 깨져 있다
> `vite build` 가 `"2,821 modules transformed"` 직후 네이티브 크래시로 죽는다
> (`0xC0000409` STATUS_STACK_BUFFER_OVERRUN). **코드 문제가 아니다** — 아무것도 안 바꾼
> 상태로 되돌려도 똑같이 죽는 걸 확인했다. Vite 8 의 Rolldown 네이티브 바이너리 문제로 보인다.
> `npm run dev` 는 정상이고 시연은 dev 로 하므로 막히지 않는다. **이걸 고치려고 코드를 뒤지지 말 것.**

---

# 3. DB

## 3-1. 사실 아무것도 안 해도 된다

DB 만 만들고 백엔드를 띄우면 끝난다.

| 순서 | 무슨 일 | 담당 |
|---|---|---|
| 1 | `db/schema.sql` 실행 → 테이블 7개 생성 | Spring `spring.sql.init` |
| 2 | `db/seed_data.json`(90일치) → `users` / `demo_session` / `demo_seed_wearable` 적재 | `DemoSeedLoader` |

두 파일 모두 백엔드 저장소 `src/main/resources/db/` 안에 있다. 전부
`CREATE TABLE IF NOT EXISTS` 라 몇 번 실행해도 안전하고, 시드 적재는 **멱등**하다
(이미 있으면 건너뛴다 — 시연 도중 재시작해도 진행이 안 날아간다).

## 3-2. 테이블 7개

```
users                  시연용 가상 사용자 1명 (id=1)
demo_session           시연 진행 상태 (current_day 가 여기 있다)
demo_seed_wearable     ★ "창고" — 90일치 원본. 아직 안 꺼낸 데이터
wearable_daily         ★ "꺼낸 것" — 하루씩 옮겨진다. 44개 컬럼
prediction_result      예측 결과 (Y)
prediction_job         예측 요청 이력 (성공/실패/지연). 장애 추적용
daily_advice           Claude 조언. 하루 1건
```

### 핵심 개념 — 창고에서 하루씩 꺼낸다

```
[demo_seed_wearable]                    [wearable_daily]
  day_index=1  payload(44) truth   ──┐
  day_index=2  payload(44) truth     │  "하루 넘기기" 누를 때마다
  day_index=3  payload(44) truth     └─▶  1행씩 복사됨
  ...                                     (44개 컬럼으로 펼쳐서)
  day_index=90
```

**왜 나눠 뒀나:** 그래야 "데이터가 하루씩 쌓이는 과정"이 DB 에서도 실제로 재현된다.
실서비스에서 사용자가 매일 웨어러블을 보내는 것과 같은 모양이 된다.

**`truth` 는 실측 정답 라벨(phase/lh/estrogen/pdg)이다.**
★ 절대 모델 입력이나 Claude 프롬프트에 넣지 말 것. **채점 전용**이다.

### 전체 컬럼 명세

DDL 원본은 `hormone-web-backend/src/main/resources/db/schema.sql` 이다.
아래는 그 요약이고, 값이 어디서 오는지를 같이 적었다.

#### `users` (9) — 시연용 단일 사용자. 로그인이 없어 `id=1` 고정

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT PK | 시드가 `1` 로 고정 삽입 |
| `name` | VARCHAR(50) | "데모 사용자" |
| `birth_date` | DATE | **모델 피처 `birth_year` 의 원본.** 연도만 쓴다 |
| `height` / `weight` | DECIMAL(4,1) | 저장만 하고 현재 안 쓴다 |
| `age_of_first_menarche` | INT | 정적 피처 |
| `ethnicity` | VARCHAR(32) | 정적 피처. 실데이터 8종 |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

#### `demo_session` (6) — PK 가 `user_id` (사용자당 1행)

| 컬럼 | 타입 | 시드 초기값 |
|---|---|---|
| `user_id` | BIGINT PK | `1` |
| `start_date` | DATE NOT NULL | `2026-08-13` (= Day 1) |
| `current_day` | INT NOT NULL | `0` — 아직 아무것도 안 보냄 |
| `total_days` | INT NOT NULL | `90` |
| `cold_start_days` | INT NOT NULL | `20` (Day 1~19 수집, Day 20 부터 예측) |
| `updated_at` | DATETIME NOT NULL | |

#### `demo_seed_wearable` (5) — ★ 창고. 90행

| 컬럼 | 타입 | 내용 |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` | BIGINT NOT NULL | |
| `day_index` | INT NOT NULL | `1..90` |
| `payload` | **JSON NOT NULL** | 웨어러블 44개 (**DB 컬럼명 키**) |
| `truth` | JSON | 실측 정답 `{phase, lh, estrogen, pdg}` |

`UNIQUE (user_id, day_index)`.
★ `truth` 는 **채점 전용**. 모델 입력이나 Claude 프롬프트에 절대 넣지 말 것.

#### `wearable_daily` (49) — 꺼낸 것. 하루 1행

`id` / `user_id` / `measured_on` / `day_in_study` / **웨어러블 44개 컬럼** / `created_at`.
`UNIQUE (user_id, measured_on)`.

44개 컬럼명과 타입은 `WearableFeatures.java` 가 단일 진실이다. 결측은 전부 `NULL` 허용.
★ `resting_heart_rate`(일간, DECIMAL)와 `sleep_resting_heart_rate`(수면중, INT)는
**다른 컬럼**이다. §8-3 참고.

#### `prediction_result` (14) — Y. 파이썬이 준 값

| 컬럼 | 타입 | 어디서 오나 |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` / `target_date` | BIGINT / DATE NOT NULL | `UNIQUE (user_id, target_date)` |
| `day_in_study` | INT | 백엔드가 채움 (화면 표시용) |
| `lh` / `estrogen` / `pdg` | DECIMAL(8,3) | **파이썬 응답** `lh` / `estrogen` / `pdg` |
| `phase` | VARCHAR(16) | **파이썬 응답** `phase`. `CHECK IN ('Menstrual','Follicular','Fertility','Luteal')` |
| `phase_confidence` | DECIMAL(4,3) | **파이썬 응답** `confidence` |
| `contributions` | JSON | **파이썬 응답** `contributions` → `[{feature, weight, direction, signal}]` |
| `model_version` | VARCHAR(32) | **파이썬 응답** `modelVersion`. 화면 배지에 그대로 뜬다 |
| `raw_response` | JSON | **응답 원문 통째로.** 우리가 모르는 필드도 여기 살아남는다 (§4-6) |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

> **호르몬별 확신도와 phase 확률분포 컬럼은 없다.** 계약에 없어서 지웠다.
> 모델이 나중에 준다면 컬럼부터 추가해야 한다.

#### `prediction_job` (11) — 예측 요청 이력. 성공/실패 모두

| 컬럼 | 타입 | 내용 |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` / `target_date` | BIGINT / DATE NOT NULL | |
| `status` | VARCHAR(16) NOT NULL | `PENDING` / `SUCCEEDED` / `FAILED` (`JobStatus` enum) |
| `request_payload` | JSON | 보낸 것. 지금은 `{"day": 45}` 하나 |
| `response_body` | **MEDIUMTEXT** | 응답 원문. **JSON 타입이 아니다** — 실패 시 에러 HTML 이 올 수 있어서 |
| `error_message` | TEXT | |
| `latency_ms` | INT | 모델 왕복 시간 |
| `started_at` / `finished_at` | DATETIME | |
| `created_at` | DATETIME NOT NULL | |

FK 를 걸지 않았다. 순수 로그라 조인이 필요 없고, 실패 기록을 남기는 경로에서
User 를 로드하다 또 실패하는 상황을 피하려는 것.

#### `daily_advice` (16) — ★ Claude 조언. 하루 1건

| 컬럼 | 타입 | 내용 |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` / `target_date` | BIGINT / DATE NOT NULL | `UNIQUE (user_id, target_date)` — 하루 1건 강제 |
| `day_in_study` | INT | |
| `status` | VARCHAR(16) NOT NULL | `PENDING` / `SUCCEEDED` / `FAILED` (`AdviceStatus` enum). `CHECK` 제약 있음 |
| `content` | **MEDIUMTEXT** | **조언 본문.** 400자 안팎 |
| `error_message` | TEXT | 실패 사유 |
| `sent_days` | INT | 최근 며칠치를 보냈나 (기본 30) |
| `sent_features` | INT | 하루당 피처 개수 (44) |
| `model` | VARCHAR(64) | 예 `claude-sonnet-5` |
| `input_tokens` / `output_tokens` | INT | 비용 추적용. 화면에 표시된다 |
| `latency_ms` | INT | 실측 16~24초 |
| `truncated` | **BOOLEAN NOT NULL DEFAULT FALSE** | `max_tokens` 에 걸려 문장이 잘렸는지 (§9-9) |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

**프롬프트 원문은 저장하지 않는다.** 한 건이 30KB 가 넘고 시드에서 언제든 재구성할 수
있다. 대신 무엇을 보냈는지 요약(`sent_days` / `sent_features`)만 남긴다.

**실패도 남긴다.** 시연 중 조언이 안 뜰 때 원인을 화면에서 바로 볼 수 있어야 한다.

`FK (user_id) → users(id)` 는 걸려 있다.

### `reset` 이 지우는 것

`POST /api/demo/users/{id}/reset` 은 아래를 지운다. **시드(`demo_seed_wearable`)는 보존한다.**

```
wearable_daily · prediction_result · prediction_job · daily_advice
demo_session.current_day → 0
```

`daily_advice` 를 안 지우면 초기화 후에도 옛 조언이 남아 "Day 0 인데 어제 조언이 보이는"
상태가 된다.

## 3-3. 처음부터 다시 만들기

```bash
mysql -u root -p hormone_web < hormone-web-backend/src/main/resources/db/drop.sql
```

그다음 백엔드 재시작. **시연 진행만 되돌리려면** 앱의 `초기화` 버튼 또는:

```bash
curl -X POST http://localhost:8085/api/demo/users/1/reset
```

## 3-4. 제대로 들어갔는지

**부팅 직후 (아직 하루도 안 넘긴 상태)**

```sql
SHOW TABLES;                                                 -- 7개
SELECT COUNT(*) FROM demo_seed_wearable;                     -- 90
SELECT JSON_LENGTH(payload) FROM demo_seed_wearable WHERE day_index=1;  -- 44
SELECT current_day, total_days, cold_start_days FROM demo_session;      -- 0, 90, 20
SELECT COUNT(*) FROM users;                                  -- 1
SELECT COUNT(*) FROM wearable_daily;                         -- 0
```

테이블 7개: `users` `demo_session` `demo_seed_wearable` `wearable_daily`
`prediction_result` `prediction_job` `daily_advice`

컬럼 수로도 확인할 수 있다 (9 / 6 / 5 / 49 / 14 / 11 / 16):

```sql
SELECT table_name, COUNT(*) AS cols
FROM information_schema.columns
WHERE table_schema = 'hormone_web'
GROUP BY table_name ORDER BY table_name;
```

**하루 넘긴 뒤 — 파이썬 Y 값이 실제로 저장되는지**

```sql
SELECT target_date, phase, lh, estrogen, pdg, phase_confidence, model_version
FROM prediction_result ORDER BY target_date DESC LIMIT 3;
```

`model_version` 이 비어 있으면 모델이 그 필드를 안 준 것이다 (화면 배지가 "예측 대기"로 남는다).

```sql
-- 요청이 실제로 나갔는지 + 무엇을 보냈는지
SELECT target_date, status, latency_ms,
       JSON_EXTRACT(request_payload, '$.day') AS sent_day,
       LEFT(COALESCE(error_message, response_body), 80) AS detail
FROM prediction_job ORDER BY id DESC LIMIT 3;
```

**조언 토글을 켠 뒤**

```sql
SELECT target_date, status, model, input_tokens, output_tokens,
       latency_ms, truncated, LEFT(content, 40) AS head
FROM daily_advice ORDER BY target_date DESC LIMIT 3;
```

`truncated = 1` 이면 문장이 잘린 것이다 → `ADVICE_MAX_TOKENS` 를 올린다 (§9-9).

```sql
-- ★ 교차 매핑 확인. 두 값이 달라야 정상이다 (같으면 §9-1 사고 재발)
SELECT JSON_EXTRACT(payload, '$.resting_heart_rate')       AS 일간_소수,
       JSON_EXTRACT(payload, '$.sleep_resting_heart_rate') AS 수면중_정수
FROM demo_seed_wearable WHERE day_index = 1;   -- 예: 72.30 / 75
```

## 3-5. 시드를 다시 만들려면

보통 필요 없다. 참가자를 바꾸거나 기간을 늘릴 때만:

```bash
python scripts/extract_seed.py data/merged_nan.xlsx
```

재생성 후 기존 시드를 지워야 `DemoSeedLoader` 가 새로 넣는다(멱등이라 있으면 건너뛴다).

---

# 4. 백엔드

## 4-1. 전체 흐름

```
[프론트] "Day N 정보 보내기" 클릭
   │
   ├─▶ POST /api/demo/users/1/advance
   │      ├─ 1. demo_seed_wearable 에서 day_index=N 꺼냄
   │      ├─ 2. wearable_daily 로 INSERT (44개 컬럼으로 펼침)
   │      ├─ 3. demo_session.current_day = N
   │      ├─ 4. 콜드스타트 구간(N < 20)이면 여기서 끝
   │      └─ 5. 아니면 예측을 **비동기로** 트리거
   │
   ◀── 202 Accepted (예측 결과는 아직 없다!)
   │
   │   [백그라운드]
   │      POST {MODEL_BASE_URL}/predict  {"day": N+offset}
   │      → prediction_result 저장 + prediction_job 기록
   │
   ◀── WebSocket /topic/prediction/1  {type: PREDICTION_READY|FAILED, ...}
   │
   ├─▶ GET /api/demo/users/1/timeline   (전체 스냅샷 재구성)
   └─▶ POST /api/advice/users/1         (조언 토글이 켜져 있으면)
```

> ★ **`advance` 는 예측 결과를 반환하지 않는다.** 비동기라 아직 없다.
> 이 사실이 프론트 화면 분기에 직접 영향을 준다 → §5-3 `pending` 상태.

## 4-2. API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/demo/users/{id}/state` | 현재 일차/날짜/총일수/상태 |
| POST | `/api/demo/users/{id}/advance` | 하루 넘기기. **202** |
| POST | `/api/demo/users/{id}/reset` | Day 0 초기화 (시드는 보존) |
| GET | `/api/demo/users/{id}/timeline` | **전체 스냅샷.** 프론트는 이거 하나로 화면 재구성 |
| GET | `/api/demo/users/{id}/jobs` | 예측 요청 이력 (기록 탭) |
| GET | `/api/advice/status` | 조언 기능 on/off + 모델명 (**키는 안 준다**) |
| GET | `/api/advice/users/{id}` | 조언 목록 (최신순) |
| POST | `/api/advice/users/{id}?force=` | 조언 생성 (하루 1건 캐시) |
| GET | `/api/predictions/users/{id}/latest` | 웹소켓 폴백 |
| WS | `/ws` → `/topic/prediction/{id}` | 예측 완료/실패 push |

### `timeline` 응답

```jsonc
{
  "userId": 1, "startDate": "2026-08-13", "currentDay": 22,
  "totalDays": 90, "coldStartDays": 20,
  "status": "active",              // collecting | active | done
  "baseline": { /* 44개. 지금까지 수집된 값의 평균 */ },
  "days": [{
    "day": 1, "date": "2026-08-13",
    "wearable": { /* 44개. 결측은 null */ },
    "prediction": { /* 없으면 null */ },
    "truth": { /* ★ 예측이 있는 날에만 내려온다 — 스포일러 방지 */ }
  }]
}
```

★ **`truth` 는 예측이 존재하는 날에만 실린다.** 아직 안 넘긴 미래 일차의 정답을 미리 보내면
시연에서 스포일러가 된다.

★ **`baseline` 은 고정값이 아니라 "지금까지 수집된 값의 평균"이다.** 그래서 Day 1 에는
baseline 이 그날 값 자신이라 차이가 0 이 된다 → §5-4 참고.

## 4-3. 주요 클래스

| 파일 | 역할 |
|---|---|
| **`support/WearableFeatures`** | ★ 44개 피처의 단일 진실 (DB 컬럼명). 엔티티 ↔ Map |
| `service/DemoService` | `advance` / `reset` / `getTimeline` / `getJobs` |
| `service/DemoSeedLoader` | 부팅 시 시드 적재 (`ApplicationRunner`) |
| `service/HormonePredictionService` | 비동기 예측 실행 + job 기록 + WS 발행 |
| `service/PredictionClient` | 인터페이스. 구현은 Python 하나 |
| `service/PythonPredictionClient` | ★ 관대한 응답 파싱 (§6-2) |
| `service/AdviceService` | 조언 프롬프트 조립 + 저장 (§7) |
| `service/AnthropicClient` | Claude Messages API |
| `entity/PredictionResult` | `mergeFrom()` — 부분 응답 병합 |
| `entity/CyclePhase` + `Converter` | 4개 라벨. `fromLabel()` 은 대소문자 무시 |

## 4-4. 절대 하면 안 되는 것

| 하지 말 것 | 이유 |
|---|---|
| `spring.jpa.hibernate.ddl-auto` 를 `none` 외의 값으로 | 스키마의 단일 진실은 `db/schema.sql` 이다. 엔티티가 테이블을 바꾸면 둘이 갈라진다 |
| 결측을 `0` 이나 평균으로 채우기 | "측정 안 됨"과 "값이 0"이 구분돼야 한다. 임퓨테이션은 모델팀 소관 |
| 안정시 심박 2개 컬럼 매핑을 "고치기" | §8-3. 이름이 엇갈린 게 정상이다 |
| `truth` 를 모델 입력이나 Claude 프롬프트에 넣기 | 정답을 보고 예측/조언하는 게 된다 |
| 주기 28일 하드코딩 | 실측 19~36일. 이 참가자도 33/31일 |
| 같은 클래스 안에서 부르는 메서드에 `@Transactional` | 프록시를 안 타서 아무 일도 안 한다 (§9-7) |
| 외부 HTTP 호출을 `@Transactional` 안에 두기 | DB 커넥션을 20초씩 붙잡는다 (§9-8) |

## 4-5. Jackson 주의 (Boot 4)

Spring Boot 4 는 **Jackson 3**(`tools.jackson`)을 쓰는데 **애노테이션은 Jackson 2**
(`com.fasterxml.jackson.annotation`)를 그대로 쓴다. import 를 섞지 말 것.

`PythonPredictionClient` 와 `AnthropicClient` 는 각자 전용 ObjectMapper 를 갖고 있다
(`FAIL_ON_UNKNOWN_PROPERTIES` off). 전역 매퍼는 `NON_NULL` 이라 응답 파싱을 우리가
통제해야 한다.

## 4-6. ★ 슈퍼셋 가정 — 부분 응답을 허용한다

**예측 응답에 필수 필드가 하나도 없다.**

| 상황 | 처리 |
|---|---|
| 필드가 없음 | `null` 로 파싱. **기존 값을 지우지 않고 병합** (`PredictionResult.mergeFrom`) |
| 모르는 필드가 옴 | 버리지 않고 `raw_response` 에 원문 보관 |
| `lh` 가 `null` | "이번엔 예측 못 함". 화면에 `-` |
| 4xx/5xx 또는 `error.code` | `prediction_job.status=FAILED`, 화면은 직전 예측 유지 + 실패 배너 |

**즉 `phase` 만 와도 돌아간다.** 나중에 호르몬을 추가하면 그때부터 화면이 채워진다.

## 4-7. `lombok.config`

`hormone-web-backend/lombok.config` 에 이 한 줄이 있다.

```
lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier
```

**지우지 말 것.** 없으면 필드에 붙인 `@Qualifier` 가 생성자 파라미터로 복사되지 않아
Spring 이 `@Primary` 빈을 주입한다. 컴파일도 되고 부팅도 되기 때문에 **조용히 틀린 빈이
들어간다.** 실제로 이것 때문에 조언 호출이 잘못된 RestClient 를 쓰고 있었다 (§9-10).

---

# 5. 프론트엔드

## 5-1. 데이터가 흐르는 길

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
   api/http.js + api/endpoints.js  →  백엔드
```

> **화면 컴포넌트가 `simulationSource` 나 `apiSource` 를 직접 부르면 안 된다.**
> 백엔드 계약이 바뀌면 `apiSource.normalizeTimeline` 만 고친다.

★ **프론트 목업은 없다.** `src/mocks/` 를 제거했고 **백엔드가 유일한 데이터 소스**다.
백엔드가 꺼져 있으면 연결 오류 배너가 뜨고 아무것도 진행되지 않는다.

## 5-2. 화면 6탭

| 탭 | 경로 | 성격 | 내용 |
|---|---|---|---|
| 홈 | `/` | 제품 | 오늘의 주기 단계 + 생체신호 6칸 + 지난 하루 몸 상태 44개 + 안전 고지 |
| 예측 상세 | `/prediction` | 제품 | 호르몬 곡선(예측/실측 겹침) + 웨어러블 신호 + 모델이 보는 신호 47개 + 오차 카드 + 기여도 + 오늘의 예측 |
| 달력 | `/calendar` | 제품 | 주기 단계 색칠 + 오늘 단계 |
| 모델 성능 | `/model` | 설득 | MAE/MAPE/상관, phase 혼동행렬, 산점도, 데이터 출처 |
| **조언** | `/advice` | 제품 | Claude 생활 조언 + 자동 토글 + 지난 조언 |
| **기록** | `/history` | **운영자** | DB 에 실제로 뭐가 쌓였나 — 날짜별 X 44개 / Y / 요청 상태 |

기기 프레임 토글(PC / 모바일 / 워치)이 있다. 같은 페이지가 3가지 프레임으로 렌더된다.
→ **그래서 `sm:` `lg:` 같은 뷰포트 브레이크포인트를 쓰면 안 된다.** §5-6

## 5-3. ★ 상태를 4개로 구분한다 — 가장 중요한 로직

| state | 뜻 | 화면 |
|---|---|---|
| `before_start` | Day 0. 하룻밤도 안 지남 | 시작 전 카드, 44개 전부 빈칸 |
| `collecting` | 예측이 **한 번도** 없음 | 콜드스타트 카드 |
| **`pending`** | 이력은 있는데 오늘 것이 아직 안 옴 | **직전 예측 유지 + "계산 중" 배지** |
| `ready` | 오늘 예측 있음 | 정상 |

**왜 중요한가.** 백엔드 `advance` 는 202 를 즉시 주고 예측은 비동기로 돈다.
예전 코드는 `const coldStart = !nextEvents;` 였다 — **"오늘 것이 아직 안 왔다"와 "한 번도
예측이 없었다"를 같은 것으로 취급**해서, Day 26 에서 하루를 넘기면 26일치 예측을 갖고
있는데도 화면이 **"데이터 수집 중"으로 되돌아갔다가** 왔다.

지금은 `pending` 이면 직전 예측이 있는 스냅샷으로 대체해서 내려준다.

| 함수 | pending 동작 |
|---|---|
| `getPredictionSummary` | 직전 예측 + `isPending` / `predictionDay` |
| `getTodayPrediction` | 직전 예측. **null 이면 진짜 콜드스타트** |
| `getContributions` | 직전 값 유지 (막대 깜빡임 방지) |
| `getAccuracyToday` | 직전 값 유지 — null 이면 카드가 사라져 2열 그리드가 출렁인다 |
| `getHormoneSeries` | **변경 없음.** 오늘이 null 이라 선이 어제까지만 그려진다 |

★ **직전 예측을 오늘 것처럼 보여주지 않는다.** `PendingNotice` 가 반드시 밝힌다:
`● Day 27 예측을 계산하는 중이에요 — 지금 보이는 건 Day 26 기준입니다`

★ **실패는 실패로 표시한다.** Mock 폴백을 지운 뒤로 파이썬이 끊기면 예측이 영영 안 온다.
그걸 "계산 중"으로 두면 발표자가 기다리기만 하게 되므로, `PREDICTION_FAILED` 이벤트를
스토어(`predictionError`)에 담아 빨간 배너로 사유까지 보여준다.

★ **Day 19→20 첫 리빌은 `collecting → ready` 라서 pending 을 건너뛴다.** 시연
하이라이트에 군더더기가 안 낀다. (검증함)

## 5-4. 홈 화면

### 생체신호 6칸 — 데이터로 골랐다

90일 실측에서 배란기 신호 강도(|구간평균차| ÷ 표준편차)와 결측률을 재서 골랐다.

| 신호 (DB 컬럼) | 강도 | 결측 |
|---|---|---|
| HRV (`rmssd`) | 0.39 | 0% |
| 안정시 심박 수면 (`sleep_resting_heart_rate`) | **0.79** | 2.2% |
| 뒤척임 (`restlessness`) | **0.69** | 2.2% |
| 스트레스 점수 (`stress_score`) | **0.68** | 4.4% |
| 야간 피부온도 (`nightly_temperature`) | 0.17 | 1.1% |
| 소모 칼로리 (`calories`) | 0.08 | 0% |

**제외한 것:** `glucose_mean`(이 참가자 100% 결측), `resting_heart_rate`(일간, 강도 0.01),
`steps`(손상값 + 20% 결측 — §8-5), `in_default_zone_3`(강도 0.60 이지만 중앙값 0 이라 죽은 칸).

### "평소 대비"는 3일치가 쌓여야 나온다

백엔드 baseline 이 "지금까지 평균"이라 Day 1 에는 그날 값 자신이 되어 6칸 전부
"평소와 같음"이 떴다. **비교한 것처럼 보이는데 비교할 게 없다.**
→ 키별로 실제 관측일 수를 세서 `MIN_BASELINE_DAYS=3` 미만이면 baseline 을 숨긴다.
결측일은 세지 않는다.

## 5-5. 웨어러블 44개를 보여주는 두 자리

`ModelInputPanel` 하나를 `variant` 로 나눠 쓴다.

| | 홈 (`variant="body"`) | 예측 상세 (`variant="model"`) |
|---|---|---|
| 제목 | 지난 하루 몸 상태 — 전체 신호 | 모델이 보는 신호 |
| 큰 숫자 | `41개 신호가 들어왔어요 (전체 44개 중)` | `47개 컬럼 = 웨어러블 44 + 정적 3` |
| 질문 | "어제 내 몸이 어땠나" | "웨어러블 몇 개나 쓰나" |

둘 다 접힌 상태(점 44칸)로 시작한다. 그룹: 수면7 / 호흡4 / 심박·HRV8 / 체온3 /
활동·운동10 / 심박존7 / 대사·기타5.

★ **결측 칸을 숨기지 않는다.** 지우면 44개를 매일 다 채우는 것처럼 보인다.
이 참가자는 하루 31~41개다.

★ **"모델에 전달합니다"라고 쓰지 말 것.** 백엔드는 일차 정수 하나만 보내고
모델이 같은 원본을 직접 읽는다. 패널 각주가 그렇게 설명한다.

## 5-6. ⚠️ 뷰포트 브레이크포인트를 쓰지 말 것

기기 프레임 토글이 있어서 **PC 창(1280px) 안에서 모바일 프레임(카드 폭 332px)** 을 볼 수
있다. `sm:` `lg:` 는 뷰포트를 보므로 이때 4열이 유지돼 **라벨이 22개 잘렸다.**

→ 카드 루트에 `@container`, 격자에 `@[26rem]:` `@[40rem]:` (Tailwind 4 컨테이너 쿼리).

## 5-7. 기록 탭

최신순, 한 페이지 10일, 하단 페이저(버튼 최대 5개). 하루를 넘기면 1페이지로 돌아간다.

```
Day 22  2026-09-03  [X 38/44]  [Y 예측됨]  난포기 (실측 난포기)  17ms  fake-py-1
Day  5  2026-08-17  [X 37/44]  [Y 없음 (수집만)]                      —
Day 32  2026-09-13  [X 38/44]  [요청 실패]                       15ms  —
```

펼치면 3개 섹션: **Y**(예측·실측·오차%), **요청**(상태/소요/보낸 일차/에러/응답 원문),
**X**(44개를 그룹별로, **DB 컬럼명을 monospace 로**, 결측은 `null`).

**설계 의도:**
1. 한글 라벨이 아니라 DB 컬럼명을 찍는다 — 파이썬 쪽과 대조할 때 "야간 피부온도"로는 못 찾는다.
2. 결측은 `—` 가 아니라 `null` — 0 과 구분돼야 하고 그게 실제 값이다.
3. 보낸 일차가 백엔드 Day 와 다르면 노랑 — day-offset 보정이 걸린 것이다.

## 5-8. 절대 하면 안 되는 것

| 하지 말 것 | 이유 |
|---|---|
| 안전 고지(`SafetyNotice`, 조언 탭 하단 문구) 제거 | 의료 진단 아님 / 피임·임신 목적 사용 금지 |
| 헤더 모델 버전 배지 제거 | 어느 모델이 돌고 있는지 알 수 없게 된다 |
| 호르몬 곡선과 웨어러블 차트를 **좌우로** 배치 | X축이 정렬돼야 "LH 서지 때 HRV 하락"이 읽힌다. 반드시 세로 |
| 두 차트의 `domain` / `ticks` 를 다르게 | 눈금이 어긋나면 대응을 못 읽는다 |
| `sm:` `lg:` 뷰포트 브레이크포인트 | §5-6 |
| effect 안에서 `setState` | lint 가 막는다. 렌더 시점 파생을 쓸 것 |

## 5-9. 알아두면 좋은 것

- **`dataRevision` 카운터** — 예측이 웹소켓으로 늦게 도착할 때 `currentDay` 는 그대로라
  zustand 가 변경으로 안 본다. 리렌더 트리거용 카운터를 따로 둔다.
- **`useCountUp`** — StrictMode 이중 마운트에 안전하게 만들어져 있다.
- **스크러버는 읽기 전용이다.** Day 19 까지 미리 가려면 자동재생 4x 또는 `advance` API 반복.

---

# 6. 예측모델 연동

## 6-1. 계약

```jsonc
POST {MODEL_BASE_URL}/predict     {"day": 45}

{
  "lh": 6.2, "estrogen": 88.6, "pdg": 3.8,
  "phase": "Fertility",                 // Menstrual|Follicular|Fertility|Luteal
  "confidence": 0.87,
  "contributions": [{"feature":"rmssd","weight":0.42,"direction":"down"}],
  "modelVersion": "v0.3"
}
```

**요청은 일차 정수 하나뿐이다.** 예전에는 웨어러블 47개 피처 × 최대 90일 히스토리를
실어 보냈지만(한 건이 수백 KB), 파이썬 쪽이 원본 CSV 를 통째로 갖고 있어서
"몇 일차까지 계산할지"만 알면 되게 바뀌었다.

**대상 데이터는 고정이다:** mcPHASES `id=22` / `study_interval=2024`.
다른 참가자를 쓰기로 하면 요청에 참가자 식별자를 추가해야 한다.

**POST 인 이유:** GET 은 프록시·브라우저가 캐싱할 수 있어서 모델을 고친 뒤에도 옛 응답이
올 수 있다. 게다가 이 호출은 백엔드 쪽 DB 쓰기를 유발한다.

## 6-2. ★ 관대한 파싱

계약이 완전히 굳지 않아서, 사소한 형태 차이로 예측 전체가 실패하면 안 된다.
`PythonPredictionClient.parse` 가 아래를 전부 받아낸다. `PythonResponseParsingTest`(12개)가 고정한다.

| 받아내는 것 | 예 |
|---|---|
| 모르는 필드 | `{"debug_info": {...}, "elapsed_ms": 812}` → 무시 |
| `contributions` 배열 | `[{"feature":"rmssd","weight":0.42,"direction":"down"}]` |
| **`contributions` 맵** | `{"rmssd": 0.42, "sleep_resting_heart_rate": -0.18}` → **부호에서 방향 유도** |
| 중첩된 옛 모양 | `{"hormones":{"lh":{"value":6.2}}, "phase":{"label":"Fertility","confidence":0.91}}` |
| snake_case 별칭 | `model_version` / `version` |
| 200 안의 에러 | `{"error":{"code":"NO_DATA","message":"..."}}` |

**거부하는 것:** JSON 객체가 아니면 예외를 던진다. 숫자가 문자열(`"6.2"`)로 오면 무시하고
비워 둔다 — 애매하게 맞히느니 비워서 로그로 드러나게 한다.

## 6-3. ★ 일차 정렬 — 아직 확정 전이다

```yaml
app.model.day-offset: 0     # 파이썬에 보낼 일차 = 백엔드 Day + 이 값
```

백엔드 Day 1 은 시드 기준 `day_in_study 862` 인데, 이 참가자의 2024 구간은
**`852` 부터 시작한다**. 즉 백엔드 Day 1 은 구간의 11번째 행이다.

| 파이썬이 세는 기준 | offset |
|---|---|
| 우리와 같은 90일 창(862~951)을 1일차부터 | `0` |
| 2024 구간 처음(852)부터 | `10` |

> **모델팀에 물어볼 문장: "45를 보내면 `day_in_study` 몇 번까지를 쓰나요?"**
> `906` 이면 0, `896` 이면 10. 설정 한 줄이라 재빌드도 필요 없다.
>
> ⚠️ 이게 틀리면 시연 전체가 엉뚱한 날짜로 간다. LH 서지가 Day 28 에 안 나온다.

## 6-4. 모델팀에 확인/요청할 것

1. **일차 정렬** (§6-3) — 제일 급하다
2. `modelVersion` 문자열을 응답에 넣어 줄 것 — 화면 배지의 유일한 근거
3. `contributions` 를 줄 수 있는지 (SHAP / tree importance 무관) — 시연에서 설득력 1위 카드
4. `confidence` 가 무엇에 대한 확신도인지
5. **`id=22` 를 학습에서 제외했는지** — §10-3
6. 예측 1건 소요 시간 (백엔드 타임아웃 기본 30초)
7. 서버 주소/포트, API 키 필요 여부
8. §8-5 의 데이터 품질 문제 3개

## 6-5. 붙인 뒤 문제가 생기면 — 기록 탭이 1차 진단

```
Day 32  [X 38/44]  [요청 실패]  15ms  —
  └ 상태 실패 · 소요 15ms · 보낸 일차 day=32
    I/O error on POST request for "http://127.0.0.1:5000/predict": Connection refused
```

| 볼 것 | 의미 |
|---|---|
| `보낸 일차` 가 백엔드 Day 와 다름 | day-offset 보정이 걸린 것 (정상일 수 있다) |
| `소요` | 타임아웃 조정 필요 여부 |
| 응답 원문 | 파이썬이 뭘 돌려줬는지 |

---

# 7. 오늘의 조언

## 7-1. 무엇인가

최근 30일 웨어러블 + 우리 예측을 Claude 에 보내고 생활 조언을 받는다.
`조언` 탭의 토글을 켜면 **하루를 넘길 때마다 자동으로** 받는다.

```
POST /api/advice/users/1
→ daily_advice 에 저장 (하루 1건, UNIQUE)
```

## 7-2. ★ 설계에서 정한 것

**실측 정답(`truth`)을 보내지 않는다.** 실서비스에는 정답이 없다. 정답을 주면 조언이
정확해 보이지만 그건 시연용 눈속임이고 실제 동작과 달라진다.
모델이 보는 건 **웨어러블 + 우리 예측**뿐이다.

**결측 키는 빼고 "무엇이 빠졌는지"를 따로 알려 준다.** 44개 중 3~13개가 매일 비어서
그대로 실으면 토큰이 12% 늘고 모델도 읽기 어렵다. 다만 결측 자체가 정보라(예: 운동을
기록 안 한 날) 목록으로 전달한다.

**하루 1건 캐시.** 이미 성공한 날은 재호출하지 않는다 — 탭을 옮길 때마다 다시 부르면
1만 토큰이 그대로 곱해진다. 재호출은 `?force=true` (다시 받기 버튼).

**자동재생 중에는 자동 호출을 건너뛴다.** 4x 로 90일을 돌리면 Claude 를 70번 부르게 되고
응답이 진행 속도를 못 따라간다. (Day 6→15 9일 진행에 호출 0건 확인)

**토글 기본값은 꺼짐이고 localStorage 에 남는다.** 켜는 순간 과금이 시작되므로
사용자가 명시적으로 켜야 한다.

## 7-3. ★ 안전 경계

여성 건강 앱이라 선을 넘기 쉽다. 시스템 프롬프트에 못 박았고 **약화시키지 말 것** —
대표 시연에서 모델이 진단·투약을 말하면 그 자리에서 신뢰가 무너진다.

| 할 것 | 하지 말 것 |
|---|---|
| 데이터에서 실제로 보이는 변화만, 수치를 인용 | 질병 진단·진단명 추측 |
| 주기 단계와 신호의 관계 설명 | 약·영양제 복용 권유, 용량 |
| 수면·활동·스트레스 자기관리 제안 | 피임·임신 시도 목적 사용 |
| 데이터가 없으면 "말할 수 없다"고 밝히기 | "병원 가야 한다/갈 필요 없다" 판단 |
| | 데이터에 없는 사실 지어내기 |

실제 응답에서 지켜지는 걸 확인했다:

> "이건 측정값이 아니라 모델 추정이라 **배란 시점이나 피임·임신 판단에는 쓸 수 없는 정보**예요."
>
> "혈당은 기간 내내 데이터가 없어 이 부분은 말할 수 없어요."
>
> "참고로 오늘 걸음 수 626,947은 **기록 오류로 보여 해석에서 뺐어요.**" ← §8-5 의 손상값을 스스로 인지

화면 하단에도 같은 선의 고지가 항상 떠 있다.

## 7-4. 비용 — 실측

| 모델 | 호출당 | 90일 전체 |
|---|---|---|
| **Sonnet 5** (현재) | **$0.061** | **$5.45** |
| Opus 5 | $0.250 | $22.47 |

입력 약 12,000 토큰 / 출력 약 1,500 토큰 / 지연 **16~24초**.
일차가 늘어도 30일 창으로 고정이라 비용이 일정하다.

> ⚠️ **90일을 토글 켜고 완주하면 $5.45 다.** 학원 계정 잔액이 $5 라 빠듯하다.
> 줄이려면 `ADVICE_HISTORY_DAYS` 를 낮추거나(14일이면 절반), 시연 구간만 켜면 된다.

## 7-5. 지연 17~24초

프론트 HTTP 타임아웃이 10초 고정이라 처음에 잘렸다. 조언 호출만 90초로 늘렸고
백엔드 read-timeout 은 60초다(백엔드가 먼저 판정하게).

시연에서 매번 20초를 기다리는 게 부담이면 미리 몇 일치를 만들어 두면 된다(캐시라 재호출 안 함).

---

# 8. 데이터

## 8-1. 원본 컬럼 55개

`data/merged_nan.xlsx` (mcPHASES). 전체 5,436행 / 2022구간 3,516행·40명 / 2024구간 1,920행·20명.

| 분류 | 개수 | 컬럼 |
|---|---|---|
| 🟩 안 보냄 | 4 | `id`, `study_interval`, `is_weekend`, `day_in_study` |
| 🟨 예측 대상 (Y) | 4 | `phase`, `lh`, `estrogen`, `pdg` |
| 🟧 정적 | 3 | `birth_year`, `age_of_first_menarche`, `ethnicity` |
| ⬜ 웨어러블 (X) | **44** | 나머지 전부 |

**모델이 보는 컬럼 = 44 + 3 = 47개.**

## 8-2. 시연에 쓰는 참가자

**`id=22` / `2024` / `day_in_study 862~951` (90일)**
전형성 20명 중 2위, 결측 11.1%, `pdg` 90/90일, 주기 3개.

### 실제 주기 구조 (`truth.phase` 라벨)

| 주기 | 월경 시작 | 난포기 | 가임기 | 황체기 | LH 최고 |
|---|---|---|---|---|---|
| 1 | D8 | D16~23 | **D24~31** | D32~40 | **D28 (35.9)** |
| 2 | D41 | D49~54 | **D55~62** | D63~71 | **D59 (41.6)** |
| 3 | D72 | D80~85 | **D86~90** | — | D79 (14.2) |

주기 길이 **33일 / 31일**. **28일 하드코딩 금지.**
Estrogen 피크 D27(251.7)/D60(243.8), PdG 피크 D36(30.0)/D67(30.0).

## 8-3. ★★ 안정시 심박 2개 컬럼 — 교차 매핑

```
원본 resting_heart_rate.csv 의 값 컬럼명 = "value"                (일간, 소수 72.3)
원본 sleep_score.csv 의 컬럼명           = "resting_heart_rate"   (수면중, 정수 75)
```

시드 생성 스크립트가 이걸 **DB 컬럼명으로 번역**해서 넣는다.

```python
MERGED_TO_DB_COLUMN = {
    "value": "resting_heart_rate",              # 일간
    "resting_heart_rate": "sleep_resting_heart_rate",  # 수면중
}
```

**두 값은 신호 강도가 완전히 다르다** (90일 3주기 평균):

| | 난포기 | 가임기 | 황체기 | 월경기 |
|---|---|---|---|---|
| `sleep_resting_heart_rate` (수면중) | 69.28 | 70.90 | **73.80** | 71.33 |
| `resting_heart_rate` (일간) | 74.08 | 73.89 | 72.02 | 71.20 |

**수면중에는 주기 패턴이 뚜렷하고, 일간에는 사실상 없다.**
이름만 보고 둘을 바꿔 쓰면 **에러 없이** 조용히 성능이 떨어진다. (§9-1 에서 실제로 났다)

## 8-4. 시연에 쓸 수 있는 신호 / 쓰면 안 되는 신호

| 신호 | 난포기 | 가임기 | 황체기 | 월경기 | 판정 |
|---|---|---|---|---|---|
| `rmssd` (HRV) | 53.49 | **47.08** | 47.39 | 52.09 | ✅ 가임기 12% 하락. **주기1만 보면 50.78 → 37.69 (26%)** |
| `sleep_resting_heart_rate` | 69.28 | 70.90 | **73.80** | 71.33 | ✅ 황체기까지 단조 상승 |
| `resting_heart_rate` (일간) | 74.08 | 73.89 | 72.02 | 71.20 | ❌ 패턴 없음 |
| `stress_score` | 78.33 | 76.38 | 74.46 | 73.39 | ⚠️ 방향은 있으나 약함 |
| `restlessness` | 0.08 | 0.08 | **0.10** | 0.09 | ⚠️ 절대값이 작다 |
| `nightly_temperature` | 33.20 | 33.40 | 33.63 | 33.51 | ⚠️ **BBT 곡선 아님.** 그렇게 설명하지 말 것 |
| `overall_score` | 78.28 | 80.38 | 82.64 | 79.96 | ❌ 신호 없음 |

> **시연 문구는 `HRV 하락` + `수면중 안정시 심박 상승` 둘로 좁히는 게 정직하고 설득력도 높다.**

## 8-5. 원본 데이터의 문제 3개 (모델팀에 알려야 함)

### ① `pdg` 와 `glucose` 는 절대 같이 존재하지 않는다

| 컬럼 | 전체 | 2022구간 | 2024구간 |
|---|---|---|---|
| `pdg` | 64.7% | **100%** | 0% |
| `glucose_mean` / `glucose_std` | 43.9% | 13.2% | **100%** |
| `sedentary` | 36.7% | 2.2% | **100%** |

결측이 랜덤이 아니라 **구간에 따라 통째로 없는 구조적 결측**이다.
**`pdg` 모델을 학습시키면 자동으로 2024구간만 쓰게 되고, 그 구간에는 위 3개가 항상 없다
→ `pdg` 모델은 사실상 41개 피처로 학습된다.**

### ② `steps` 에 물리적으로 불가능한 값

```
id=22 / day_in_study 883:  steps=626,947  운동시간=274분  운동칼로리=61
```

274분에 62만 걸음 = **분당 2,270보.** 데이터셋 전체 `steps` 상위 **0.30%** 가 2만 보 초과,
최댓값 **938,808**. **원본은 고치지 않았다** — 모델팀이 알아야 할 문제라 지우면 안 된다.

### ③ `steps` 는 하루 총 걸음 수가 아니다

`originalduration` / `averageheartrate` / `exercise_calories` 와 **항상 같이 있거나 같이
없다**(90일 표본 예외 0건). `exercise.csv`(기록된 운동 세션)에서 온다.
하루 활동량이 필요하면 `calories`(전체 결측 0.3%) 쪽이 맞다.

## 8-6. 단위 — 잘못 알기 쉬운 것

| 컬럼 | 실제 | 오해 |
|---|---|---|
| `glucose_mean` | **mmol/L** (5.4~6.6) | mg/dL 아님 |
| `nightly_temperature` | **Fitbit 피부온도** (31~35°C) | 체온 아님. **BBT 곡선이 아니다** |
| `originalduration` | **밀리초** (5,334,000 = 89분) | 초/분 아님 |

## 8-7. 결측은 예외가 아니라 기본 상태

**결측은 `null` 그대로 둔다. `0` 으로 채우지 않는다.**
학습 데이터 결측률이 최대 69%. 시연 참가자 기준 하루 44개 중 **31~41개**만 값이 있고,
`sedentary` / `glucose_mean` / `glucose_std` 3개는 **90일 내내 비어 있다.**

---

# 9. 겪은 사고들

같은 실수를 반복하지 않으려고 남긴다. 각 항목에 재발 방지 장치가 붙어 있다.

## 9-1. 시드 컬럼명 사고 — 값 2개가 조용히 뒤바뀜

시드 생성 스크립트가 원본 헤더(모델 피처명)를 그대로 썼는데 백엔드는 DB 컬럼명을
기대했다. 결과: `value`(일간 72.3)는 **알 수 없는 키라 조용히 버려지고**,
`resting_heart_rate`(수면중 75)가 **일간 컬럼 자리에 들어갔다.** 에러가 하나도 안 났다.

**장치:** `extract_seed.py` 에 `MERGED_TO_DB_COLUMN` 매핑,
`WearableFeatures.applyToEntity` 가 **모르는 키를 만나면 `log.warn`**, 회귀 테스트.

## 9-2. `NON_NULL` 직렬화 사고 — 44개가 40개로

전역 ObjectMapper 가 `NON_NULL` 이라 모델 요청을 객체로 넘기니 **결측 피처 4개가 통째로
사라졌다.** `prediction_job.request_payload` 를 보고 발견했다.

**장치:** 전용 매퍼 + String 직렬화. (지금은 요청이 `{"day":N}` 하나라 이 위험은 없어졌다)

## 9-3. `pending` 을 콜드스타트로 취급 — 화면이 되돌아감

`advance` 가 202 를 즉시 주고 예측은 비동기인데, 화면 분기가 "오늘 예측 없음 = 콜드스타트"
였다. Day 26 에서 하루를 넘기면 26일치 예측을 갖고 있는데도 **"데이터 수집 중"으로
되돌아갔다가** 왔다.

**장치:** 상태 4개 분리(§5-3). 클릭 후 3초간 50ms×60프레임 샘플링으로 **콜드스타트 전환
0회 / 카드 6개 고정** 확인.

## 9-4. 실패가 "계산 중"으로 영원히 표시됨

Mock 폴백을 지운 뒤, 파이썬이 끊기면 예측이 영영 안 오는데 화면은 계속 "계산 중"이었다.

**장치:** `PREDICTION_FAILED` 를 `predictionError` 로 잡아 빨간 배너 + 사유 표시.
파이썬 종료 → 실패 표시 → 복구 → 정상 복귀까지 확인.

## 9-5. `useCountUp` StrictMode 이중 마운트

**장치:** `displayRef` 를 콜백에서만 전진, `onComplete` 로 최종값 고정.

## 9-6. Gradle 9.5.1 ↔ JUnit Platform 6.0.3 충돌

**모든 테스트 클래스가 `ClassNotFoundException`** 으로 죽었다. wrapper 를 9.7.1 로 올려 해결.
**낮추지 말 것.**

## 9-7. `@Transactional` 이 아무 일도 안 하고 있었음

`saveResult` 가 `@Transactional protected` 인데 같은 클래스에서 `this.saveResult(...)` 로
호출됐다 → **프록시를 안 타서 애노테이션이 무력.** 있으면 "트랜잭션이 걸려 있다"는 잘못된
인상만 준다.

**장치:** 제거하고 주석으로 이유를 남김. 원자성이 필요하면 별도 빈으로 분리해야 한다.

## 9-8. 트랜잭션 안에서 20초 HTTP 호출

`AdviceService.generate` 가 `@Transactional` 인데 그 안에서 Claude 를 부른다 →
**DB 커넥션을 20초씩 붙잡는다.** 기본 풀이 10개라 겹치면 마른다.

**장치:** `@Transactional` 제거. 하루 1건 upsert 라 원자성이 필요한 구간이 없고,
동시 삽입은 유니크 제약이 막는다.

## 9-9. 조언이 문장 중간에서 잘림

Sonnet 이 `max_tokens: 1200` 을 정확히 채우고 잘렸다(Opus 는 850 이라 안 걸렸다).
한국어는 글자당 토큰이 커서 프롬프트가 요구하는 400자도 600토큰쯤 된다.

**장치:** 2000 으로 상향 + `stop_reason == "max_tokens"` 감지 →
DB `truncated` 컬럼 + 화면 경고 배너 + 로그.

## 9-10. `@Qualifier` 가 무시되고 있었음

`@Qualifier` 를 필드에 붙였는데 `@RequiredArgsConstructor` 가 생성자 파라미터로 복사하지
않아 Spring 이 `@Primary` 빈을 주입했다. **컴파일도 되고 부팅도 돼서 조용히 틀린 빈이
들어갔다.** 조언 호출이 모델용 RestClient(read-timeout 30초)를 쓰고 있었다. 의도는 60초.

**장치:** `lombok.config` 에 `copyableAnnotations` 추가. 바이트코드로 생성자 파라미터에
애노테이션이 붙은 걸 확인했다.

## 9-11. 조언 동시 생성 충돌 (409)

브라우저의 자동 토글과 수동 호출이 겹쳐 같은 날짜를 동시에 만들다 유니크 제약에 걸렸다.
사용자 입장에선 조언이 멀쩡히 만들어졌는데도 실패로 보인다.

**장치:** `DataIntegrityViolationException` 을 잡아 **이미 저장된 것을 돌려준다.**

## 9-12. PowerShell 스크립트 인코딩

BOM 없는 UTF-8 을 PS 5.1 이 ANSI 로 읽어 한글이 깨지고 파싱이 무너졌다.
`push_*.ps1` 은 **UTF-8 BOM 으로 저장**돼 있다. 편집 시 유지할 것.

---

# 10. 지금 상태와 남은 것

## 10-1. 검증된 것

| 항목 | 결과 |
|---|---|
| 백엔드 테스트 | 전체 통과 (`PythonResponseParsingTest` 12개 포함) |
| 프론트 lint | exit 0 |
| 프론트 카탈로그 ↔ 백엔드 피처 | **44/44 완전 일치** |
| 파이썬 연동 | 대역 서버로 E2E — 저장값 vs 반환값 **불일치 0건** |
| 파이썬 장애 | 종료 → 빨간 실패 배너 + 사유 / 복구 → 정상 복귀 |
| 조언 | Sonnet 실측 — 잘림 없음, 안전 경계 지켜짐, 캐시 0.06초 |
| 자동재생 9일 | 조언 호출 **0건** (의도한 대로 건너뜀) |
| 기록 탭 | Day 5/59/60 결측 개수가 **시드와 일치** |
| DB 재생성 | `drop.sql` → 부팅 → 테이블 7개 + 시드 90일 |
| 반응형/다크모드 | PC·모바일·워치, 라벨 잘림 0건, 가로 오버플로 없음 |
| 브라우저 콘솔 | 에러 0건 |

## 10-2. ❌ 미해결

| 항목 | 상태 |
|---|---|
| **`npm run build`** | 네이티브 크래시. **환경 문제, 코드 무관.** dev 로 시연하므로 안 막힌다 |
| **진짜 파이썬 모델과의 E2E** | 대역 서버로만 확인했다. 실제 모델 응답이 다를 수 있다 |
| **시연 리허설** | 처음부터 끝까지 돌려본 적이 없다 |
| **일차 오프셋** | §6-3. 모델팀 확인 대기 |

## 10-3. ⚠️ 정직성 — 확인 사항

**`id=22` 가 학습에서 제외됐는지 확인되지 않았다.**

제외되지 않았다면 `모델 성능` 탭의 정확도가 부풀려지는데, **화면이 그걸 알아낼 방법이
없다.** 내장 Mock 을 쓰던 시절에는 "이 수치는 모델 성능이 아니다"라는 경고 배너가 있었지만,
Mock 을 제거하면서 그 배너도 같이 사라졌다.

> **모델팀 답을 받기 전에는 "이 수치가 실제 성능이다"라고 말하면 안 된다.**
> `데이터 출처` 카드에 "학습 데이터에서 제외 요청된 검증용 표본"이라고 적혀 있는데,
> 이건 **요청**이지 확인이 아니다.

## 10-4. 폴백이 없다

내장 Mock 을 제거해서 **파이썬이 없으면 시연을 못 한다.**
시연 당일 순서: MySQL → 파이썬 → 백엔드 → 프론트. 5000번 포트를 반드시 먼저 확인할 것.

## 10-5. 저장소

로컬 `hormone_web/` 은 **git 저장소가 하나뿐인 모노레포**지만, GitHub 은 **기존 저장소
2개로 분리 배포**한다.

- `github.com/carino2000/hormone-web-backend` (`master`)
- `github.com/carino2000/hormone-web-frontend` (`main`)

**모노레포에 remote 를 걸고 push 하면 안 된다.** 공통 조상이 없어 거부되고, 강제로 밀면
기존 히스토리가 날아간다. 루트의 스크립트를 쓴다.

```powershell
..\push_backend.ps1          # 커밋까지만
..\push_backend.ps1 -Push    # 커밋 + push
```

기존 저장소를 clone → 현재 작업트리를 얹어 커밋 → push 한다.
모노레포를 미리 커밋할 필요 없다.

⚠️ **`application-local.yaml` 은 저장소에 없다.** DB 비밀번호와 Anthropic 키가 들어 있고
저장소가 public 이며, 실제로 GitHub push protection 이 Anthropic 키를 차단했다
(`GH013 - Push cannot contain secrets`). `.example` 만 올라간다.

---

## 부록: 파일 지도

```
hormone_web/
├── README.md                     ← 이 문서
├── data/merged_nan.xlsx          원본 데이터셋
├── scripts/extract_seed.py       xlsx → 백엔드 시드 (표준 라이브러리만)
├── push_backend.ps1 / push_frontend.ps1
│
├── hormone-web-backend/
│   ├── lombok.config             ★ @Qualifier 복사 설정 (§4-7)
│   └── src/main/
│       ├── java/.../
│       │   ├── support/WearableFeatures        ★★ 44개 피처 단일 진실
│       │   ├── service/PythonPredictionClient  ★ 관대한 파싱
│       │   ├── service/AdviceService           ★ 프롬프트 + 안전 경계
│       │   ├── service/AnthropicClient
│       │   ├── service/HormonePredictionService
│       │   ├── service/DemoService / DemoSeedLoader
│       │   └── dto/model/ModelPredictRequest·Response  ★ 계약
│       └── resources/db/         schema.sql · drop.sql · seed_data.json
│
└── hormone-web-frontend/src/
    ├── lib/wearableCatalog.js    ★★ 44개 표시 방법 단일 진실
    ├── api/index.js              ★ 화면이 쓰는 유일한 창구
    ├── data/apiSource.js         백엔드 → 화면 모양 어댑터
    ├── state/simulationStore.js  ★ 일차·조언·실패 상태
    ├── pages/                    Home · PredictionDetail · Calendar ·
    │                             ModelPerformance · Advice · HistoryLog
    └── components/               PendingNotice · DataSourceBadge · SafetyNotice
                                  cards/ModelInputPanel · cards/HormoneChart ...
```

> ★ **피처를 추가·변경할 때는 두 파일을 같이 고친다** —
> 백엔드 `WearableFeatures.java`(DB 컬럼명)와 프론트 `wearableCatalog.js`(라벨·단위·그룹).
> **키가 1:1 로 맞아야 한다.** 현재 44/44 일치.
