package com.sixletter.hormone_web_backend.entity;

/**
 * 조언 생성 상태. 자바 상수명이 그대로 DB 에 저장된다
 * ({@code @Enumerated(EnumType.STRING)}) — 이름을 바꾸면 기존 행을 못 읽으니 주의.
 * schema.sql 의 {@code ck_advice_status} CHECK 제약과도 맞아야 한다.
 */
public enum AdviceStatus {

    /** Claude 에 요청을 보냈고 아직 응답을 못 받음. */
    PENDING,

    /** 조언 본문 저장까지 완료. */
    SUCCEEDED,

    /** 호출 실패 / 타임아웃 / 빈 응답. 원인은 error_message 참고. */
    FAILED
}
