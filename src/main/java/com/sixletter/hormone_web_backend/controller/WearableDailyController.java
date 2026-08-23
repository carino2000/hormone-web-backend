package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.dto.WearableDailyDto;
import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.entity.WearableDaily;
import com.sixletter.hormone_web_backend.exception.NotFoundException;
import com.sixletter.hormone_web_backend.repository.UserRepository;
import com.sixletter.hormone_web_backend.repository.WearableDailyRepository;
import com.sixletter.hormone_web_backend.service.HormonePredictionService;
import com.sixletter.hormone_web_backend.support.WearableFeatures;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웨어러블 데이터 CRUD.
 *
 * <p>시연 흐름은 이 컨트롤러가 아니라 {@code /api/demo/.../advance} 를 쓴다.
 * 여기는 데이터를 직접 넣어보거나 확인할 때 쓰는 일반 CRUD 다.
 */
@RestController
@RequestMapping("/api/wearable-daily")
@RequiredArgsConstructor
public class WearableDailyController {

    private final WearableDailyRepository wearableDailyRepository;
    private final UserRepository userRepository;
    private final HormonePredictionService hormonePredictionService;

    /** 같은 (user, 날짜) 가 이미 있으면 덮어쓴다 — 중복 저장으로 500 이 나지 않게. */
    @PostMapping("/user/{userId}")
    public ResponseEntity<WearableDailyDto> create(@PathVariable Long userId,
                                                   @Valid @RequestBody WearableDailyDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + userId));

        WearableDaily entity = wearableDailyRepository
                .findByUserIdAndMeasuredOn(userId, request.measuredOn())
                .orElseGet(() -> WearableDaily.builder().user(user).measuredOn(request.measuredOn()).build());
        WearableFeatures.applyToEntity(request.features(), entity);

        WearableDaily saved = wearableDailyRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(WearableDailyDto.from(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WearableDailyDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(WearableDailyDto.from(require(id)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<WearableDailyDto>> findByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(wearableDailyRepository.findByUserIdOrderByMeasuredOnDesc(userId)
                .stream().map(WearableDailyDto::from).toList());
    }

    @GetMapping("/user/{userId}/date/{measuredOn}")
    public ResponseEntity<WearableDailyDto> findByUserAndDate(
            @PathVariable Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate measuredOn) {
        return wearableDailyRepository.findByUserIdAndMeasuredOn(userId, measuredOn)
                .map(WearableDailyDto::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundException("해당 날짜 데이터가 없습니다: " + measuredOn));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WearableDailyDto> update(@PathVariable Long id,
                                                   @Valid @RequestBody WearableDailyDto request) {
        WearableDaily existing = require(id);
        WearableFeatures.applyToEntity(request.features(), existing);
        return ResponseEntity.ok(WearableDailyDto.from(wearableDailyRepository.save(existing)));
    }

    /** 비동기 예측 트리거. 결과는 WebSocket /topic/prediction/{userId} 로 간다. */
    @PostMapping("/{id}/predict")
    public ResponseEntity<Void> predict(@PathVariable Long id) {
        WearableDaily data = require(id);
        hormonePredictionService.requestPrediction(data.getUser(), data.getMeasuredOn(), null);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        wearableDailyRepository.delete(require(id));
        return ResponseEntity.noContent().build();
    }

    private WearableDaily require(Long id) {
        return wearableDailyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("데이터를 찾을 수 없습니다: " + id));
    }
}
