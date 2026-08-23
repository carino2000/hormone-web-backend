package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.dto.model.ModelPredictRequest;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictResponse;

/**
 * 예측 모델 호출 창구. 구현이 두 개다:
 * <ul>
 *   <li>{@code PythonPredictionClient} — 실제 파이썬 서버 (app.model.enabled=true)</li>
 *   <li>{@code MockPredictionClient}   — 백엔드 내장 (app.model.enabled=false)</li>
 * </ul>
 *
 * <p>이 인터페이스가 있어서 파이썬 계약이 확정되기 전에도 프론트~백엔드 전 구간을
 * 완성하고 시연할 수 있다. 모델팀 주소가 나오면 <b>설정 한 줄</b>로 전환된다.
 */
public interface PredictionClient {

    /**
     * @return 응답 DTO. 실패는 예외로 던진다 (호출부가 prediction_job 에 FAILED 로 기록)
     */
    ModelPredictResponse predict(ModelPredictRequest request);

    /** 로그·job 기록용 이름. "python" | "mock" */
    String name();

    /** 원본 응답 문자열. prediction_job.response_body 에 남긴다. 없으면 null */
    default String lastRawBody() {
        return null;
    }
}
