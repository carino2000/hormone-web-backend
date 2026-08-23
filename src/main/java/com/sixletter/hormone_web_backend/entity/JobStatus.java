package com.sixletter.hormone_web_backend.entity;

/**
 * 예측 요청 작업 상태. 자바 상수명이 그대로 DB 에 저장된다
 * ({@code @Enumerated(EnumType.STRING)}) — 이름을 바꾸면 기존 행을 못 읽으니 주의.
 */
public enum JobStatus {

    /** 파이썬 서버에 요청을 보냈고 아직 응답을 못 받음. */
    PENDING,

    /** 응답을 받아 예측 결과 저장까지 완료. */
    SUCCEEDED,

    /** 호출 실패 / 타임아웃 / 파싱 실패. 원인은 error_message 참고. */
    FAILED
}
