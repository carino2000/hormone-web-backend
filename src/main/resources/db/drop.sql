-- ===========================================================================
-- 스키마를 처음부터 다시 만들 때만 사용한다. ⚠️ 데이터가 전부 사라진다.
--
--   mysql -u root -p hormone_web < src/main/resources/db/drop.sql
--   mysql -u root -p hormone_web < src/main/resources/db/schema.sql
--
-- 이 파일은 부팅 시 자동 실행되지 않는다 (application.yaml 은 schema.sql 만 지정).
-- 외래키 때문에 삭제 순서가 중요하다 — 자식 테이블부터 지운다.
-- ===========================================================================

DROP TABLE IF EXISTS daily_advice;
DROP TABLE IF EXISTS demo_seed_wearable;
DROP TABLE IF EXISTS demo_session;
DROP TABLE IF EXISTS prediction_job;
DROP TABLE IF EXISTS prediction_result;
DROP TABLE IF EXISTS wearable_daily;
DROP TABLE IF EXISTS users;
