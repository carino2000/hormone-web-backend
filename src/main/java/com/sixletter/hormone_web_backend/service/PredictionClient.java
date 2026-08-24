package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.dto.model.ModelPredictRequest;
import com.sixletter.hormone_web_backend.dto.model.ModelPredictResponse;

/**
 * 예측 모델 호출 창구. 현재 구현은 {@code PythonPredictionClient} 하나다.
 *
 * <p><b>내장 Mock 은 제거했다.</b> 예전에는 파이썬 없이도 시연이 완주되도록
 * 실측 정답에 노이즈를 얹는 {@code MockPredictionClient} 를 두었지만,
 * 더미값을 전부 걷어내기로 하면서 지웠다.
 *
 * <p>⚠️ <b>그래서 파이썬 서버가 없으면 예측이 나오지 않는다.</b> 화면은 예측 실패로
 * 표시되고 수집 데이터만 보인다. 시연 전에 {@code app.model.base-url} 이 살아 있는지
 * 반드시 확인할 것 — 폴백이 없다.
 *
 * <p>인터페이스 자체는 남겨 둔다. 구현을 갈아끼울 자리를 유지하는 비용이 거의 없고,
 * 주소·프로토콜이 바뀔 때 이 경계가 있으면 호출부를 안 건드린다.
 */
public interface PredictionClient {

    /**
     * @return 응답 DTO. 실패는 예외로 던진다 (호출부가 prediction_job 에 FAILED 로 기록)
     */
    ModelPredictResponse predict(ModelPredictRequest request);

    /** 로그·job 기록용 이름. 현재는 "python" 하나다. */
    String name();

    /** 원본 응답 문자열. prediction_job.response_body 에 남긴다. 없으면 null */
    default String lastRawBody() {
        return null;
    }
}
