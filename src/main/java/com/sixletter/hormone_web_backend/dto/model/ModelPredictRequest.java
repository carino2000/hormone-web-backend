package com.sixletter.hormone_web_backend.dto.model;

/**
 * 백엔드 → 파이썬 예측 서버 요청.
 *
 * <pre>
 *   POST /predict   {"day": 45}
 * </pre>
 *
 * <p><b>이게 전부다.</b> 예전에는 웨어러블 47개 피처 × 최대 90일 히스토리를 실어 보냈지만
 * (한 건이 수백 KB였다), 계약이 바뀌어 <b>일차 정수 하나만</b> 보낸다.
 * 파이썬 쪽이 원본 CSV를 통째로 갖고 있고, "몇 일차까지 계산할지"만 알면 되기 때문이다.
 *
 * <p><b>대상 데이터는 고정이다:</b> mcPHASES {@code id=22} / {@code study_interval=2024}.
 * 파이썬이 같은 참가자·같은 구간을 보고 있어야 한다. 다른 참가자를 쓰기로 하면
 * 이 요청에 참가자 식별자를 추가해야 한다.
 *
 * <p><b>★ 일차 정렬이 서로 같아야 한다.</b> 백엔드 Day 1 이 파이썬 day 1 과 같은 날을
 * 가리켜야 한다. 지금 시드는 {@code day_in_study 862~951} 인데 이 참가자의 2024 구간은
 * {@code 852} 부터 시작한다 — 즉 <b>백엔드 Day 1 은 구간의 11번째 행</b>이다.
 * 파이썬이 구간 처음부터 센다면 10일이 어긋난다.
 * 보정은 {@code app.model.day-offset} 하나로 흡수한다 ({@code ModelProperties} 참고).
 *
 * <p>POST 인 이유: GET 은 프록시·브라우저가 캐싱할 수 있어서 모델을 고친 뒤에도 옛 응답이
 * 올 수 있다. 게다가 이 호출은 백엔드 쪽 DB 쓰기를 유발하므로 부작용 없는 요청이 아니다.
 *
 * @param day 예측 대상 일차 (1-base). 파이썬은 이 일차까지의 데이터로 계산한다
 */
public record ModelPredictRequest(Integer day) {
}
