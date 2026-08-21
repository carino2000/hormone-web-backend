package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.entity.User;
import com.sixletter.hormone_web_backend.entity.WearableDaily;
import com.sixletter.hormone_web_backend.repository.UserRepository;
import com.sixletter.hormone_web_backend.repository.WearableDailyRepository;
import com.sixletter.hormone_web_backend.service.HormonePredictionService;
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

@RestController
@RequestMapping("/api/wearable-daily")
@RequiredArgsConstructor
public class WearableDailyController {

    private final WearableDailyRepository wearableDailyRepository;
    private final UserRepository userRepository;
    private final HormonePredictionService hormonePredictionService;

    @PostMapping("/user/{userId}")
    public ResponseEntity<WearableDaily> create(@PathVariable Long userId, @RequestBody WearableDaily wearableDaily) {
        return userRepository.findById(userId)
                .map(user -> {
                    wearableDaily.setId(null);
                    wearableDaily.setUser(user);
                    return ResponseEntity.status(HttpStatus.CREATED).body(wearableDailyRepository.save(wearableDaily));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WearableDaily> findById(@PathVariable Long id) {
        return wearableDailyRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<WearableDaily>> findByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(wearableDailyRepository.findByUserIdOrderByMeasuredOnDesc(userId));
    }

    @GetMapping("/user/{userId}/date/{measuredOn}")
    public ResponseEntity<WearableDaily> findByUserAndDate(
            @PathVariable Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate measuredOn) {
        return wearableDailyRepository.findByUserIdAndMeasuredOn(userId, measuredOn)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<WearableDaily> update(@PathVariable Long id, @RequestBody WearableDaily wearableDaily) {
        return wearableDailyRepository.findById(id)
                .map(existing -> {
                    User user = existing.getUser();
                    wearableDaily.setId(id);
                    wearableDaily.setUser(user);
                    return ResponseEntity.ok(wearableDailyRepository.save(wearableDaily));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 비동기 예측 요청 트리거. 결과는 응답 바디가 아니라 "/topic/prediction/{userId}" 웹소켓 push로 전달됨.
    // TODO: 응답 바디(WearableDaily) 없이 202만 반환하는 게 맞는지, 프론트 쪽 흐름과 확인 필요
    @PostMapping("/{id}/predict")
    public ResponseEntity<Void> predict(@PathVariable Long id) {
        return wearableDailyRepository.findById(id)
                .map(data -> {
                    hormonePredictionService.requestPrediction(data.getUser().getId(), data);
                    return ResponseEntity.accepted().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!wearableDailyRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        wearableDailyRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
