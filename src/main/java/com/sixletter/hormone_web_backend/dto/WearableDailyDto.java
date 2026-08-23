package com.sixletter.hormone_web_backend.dto;

import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.entity.WearableDaily;
import com.sixletter.hormone_web_backend.support.WearableFeatures;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;

/**
 * 웨어러블 하루치 데이터의 입출력 형태.
 *
 * <p>44개 피처를 개별 필드로 나열하지 않고 {@code features} Map 하나로 묶은 이유:
 * 키 이름이 곧 모델 계약(snake_case)인데, 자바 필드명은 camelCase 라 매번
 * {@code @JsonProperty} 를 44개 붙여야 한다. Map 으로 두면 키가 그대로 나간다.
 *
 * @param features DB 컬럼명 기준 44개 피처. 결측은 null (0 아님)
 */
public record WearableDailyDto(
        Long id,
        @NotNull(message = "measuredOn 은 필수입니다") LocalDate measuredOn,
        Map<String, Object> features
) {

    public static WearableDailyDto from(WearableDaily entity) {
        return new WearableDailyDto(entity.getId(), entity.getMeasuredOn(), WearableFeatures.toMap(entity));
    }

    /** 요청 → 엔티티. id/user 는 서버가 정하므로 클라이언트 값을 받지 않는다. */
    public WearableDaily toEntity(User user) {
        WearableDaily entity = WearableDaily.builder()
                .user(user)
                .measuredOn(measuredOn)
                .build();
        WearableFeatures.applyToEntity(features, entity);
        return entity;
    }
}
