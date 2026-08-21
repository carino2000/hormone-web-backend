# 확인 체크리스트

코드에 흩어진 TODO/확인 필요 주석을 모아서, 확인하면 좋은 순서대로 정리했습니다.
각 항목은 `파일:줄번호` → 내용 → 한 줄 추천 순서입니다.

## 1. 지금 당장 (안 하면 예측 기능 자체가 안 돌아감)

1. **파이썬 서버 주소** — `HormonePredictionService.java:50` (`PYTHON_PREDICT_URL`, 현재 `http://127.0.0.1:5000/predict` 더미값)
   → 추천: 파이썬 서버 주소/포트 나오는 즉시 이것부터 교체.

2. **웹소켓 접속 경로** — `WebSocketConfig.java:23` (`/ws`)
   → 추천: 프론트 쪽이 접속할 경로랑 이름이 같은지 프론트 개발자와 먼저 맞추기.

3. **웹소켓 토픽 규칙** — `HormonePredictionService.java:51` (`PREDICTION_TOPIC_PREFIX = "/topic/prediction/"`)
   → 추천: 2번과 동시에 프론트와 합의 — 프론트가 구독할 문자열이 한 글자라도 다르면 결과를 영영 못 받음.

## 2. 모델 연동 정확성 (조용히 틀린 값이 나올 수 있는 부분)

4. **정적 피처 3개 누락** — `HormonePredictionService.java:32-35` (`birth_year`, `age_of_first_menarche`, `ethnicity`)
   → 추천: 모델이 실제로 이 3개를 요구하는지 먼저 확인하고, 필요하면 `users` 테이블 조인해서 `toModelFeatures()`에 추가.

5. **파이썬 응답 구조 미확정** — `HormonePredictionService.java:68-73` (지금은 `String` 하나로만 받음)
   → 추천: 파이썬 쪽 응답 JSON 예시 하나만 받아서 구조부터 확정 — 그래야 아래 6, 7번도 진행 가능.

## 3. 본인이 진행 예정인 작업 (이미 표시해둔 자리)

6. **`toModelFeatures()` Map → DTO/엔티티 전환** — `HormonePredictionService.java:92`
   → 추천: 5번(응답 구조) 정해진 뒤에 요청/응답 DTO를 같이 설계하는 게 두 번 손 안 감.

7. **예측 결과/실패 DB 저장 로직 미구현** — `HormonePredictionService.java:75, 81`
   → 추천: 저장할 테이블·컬럼부터 설계하고 이 TODO 위치에 저장 코드 채우기.

8. **`predict` 엔드포인트 응답 스펙** — `WearableDailyController.java:77` (지금은 202만 반환, 바디 없음)
   → 추천: 프론트가 즉시 뭔가 필요한지(예: 처리 시작 확인용 id) 물어보고 필요하면 바디 추가.

## 4. 운영 배포 직전에만 신경 쓰면 되는 것들

9. **인증 방식 미정** — `HormonePredictionService.java:39` (현재 파이썬 서버 호출에 인증 헤더 없음)
   → 추천: 사내망/사설 네트워크로만 통신하면 생략 가능, 외부 노출되면 API Key 헤더 추가.

10. **CORS 와일드카드** — `WebSocketConfig.java:24` (`setAllowedOriginPatterns("*")`)
    → 추천: 개발 중엔 그대로 두고, 배포 직전에 실제 프론트 도메인으로 제한.

11. **콘솔 로그(`printStackTrace`)** — `HormonePredictionService.java:79`
    → 추천: 급하지 않음, 나중에 slf4j 같은 로깅 프레임워크로 한 번에 교체.

12. **비동기 스레드풀 사이즈** — `AsyncConfig.java:20-22` (core 2 / max 4 / queue 50)
    → 추천: 지금 값 그대로 두고 실제 동시 요청량 보이면 그때 조정.

## 완료됨

- [x] Claude API 키 — `application.yaml`에 값 채워넣음, `.gitignore` 처리도 완료돼 git에 안 올라감.
