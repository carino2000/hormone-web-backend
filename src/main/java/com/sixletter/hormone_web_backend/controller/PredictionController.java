package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.dto.PredictionDto;
import com.sixletter.hormone_web_backend.exception.NotFoundException;
import com.sixletter.hormone_web_backend.repository.PredictionResultRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예측 결과 조회. WebSocket 이 막혔을 때 프론트의 폴백 경로이기도 하다.
 */
@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionResultRepository repository;

    /** 기간 조회. 호르몬 곡선용이라 날짜 오름차순이다. */
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<PredictionDto>> byPeriod(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<PredictionDto> results = (from == null || to == null)
                ? repository.findByUserIdOrderByTargetDateAsc(userId).stream().map(PredictionDto::from).toList()
                : repository.findByUserIdAndTargetDateBetweenOrderByTargetDateAsc(userId, from, to)
                        .stream().map(PredictionDto::from).toList();
        return ResponseEntity.ok(results);
    }

    /** 최신 1건. Home 의 "오늘의 예측" 카드용. */
    @GetMapping("/users/{userId}/latest")
    public ResponseEntity<PredictionDto> latest(@PathVariable Long userId) {
        return repository.findTopByUserIdOrderByTargetDateDesc(userId)
                .map(PredictionDto::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundException("아직 예측 결과가 없습니다: userId=" + userId));
    }

    @GetMapping("/users/{userId}/date/{date}")
    public ResponseEntity<PredictionDto> byDate(
            @PathVariable Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return repository.findByUserIdAndTargetDate(userId, date)
                .map(PredictionDto::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundException("해당 날짜의 예측이 없습니다: " + date));
    }
}
