package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.dto.DemoAdvanceDto;
import com.sixletter.hormone_web_backend.dto.DemoStateDto;
import com.sixletter.hormone_web_backend.dto.DemoTimelineDto;
import com.sixletter.hormone_web_backend.dto.PredictionJobDto;
import com.sixletter.hormone_web_backend.service.DemoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시연 제어 API. 프론트 시뮬레이터 바가 호출한다.
 *
 * <p>{@code advance} 는 <b>예측 결과를 반환하지 않는다</b> — 비동기라 아직 없다.
 * 결과는 WebSocket {@code /topic/prediction/{userId}} 로 간다.
 * 웹소켓이 막혔을 때의 폴백은 {@code GET /api/predictions/users/{id}/latest}.
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;

    @GetMapping("/users/{userId}/state")
    public ResponseEntity<DemoStateDto> state(@PathVariable Long userId) {
        return ResponseEntity.ok(demoService.getState(userId));
    }

    /** 하루 넘기기. 202 Accepted + 진행 상태. */
    @PostMapping("/users/{userId}/advance")
    public ResponseEntity<DemoAdvanceDto> advance(@PathVariable Long userId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(demoService.advance(userId));
    }

    /** Day 0 으로 초기화. 해당 사용자의 수집 데이터와 예측만 지운다(시드는 보존). */
    @PostMapping("/users/{userId}/reset")
    public ResponseEntity<DemoStateDto> reset(@PathVariable Long userId) {
        return ResponseEntity.ok(demoService.reset(userId));
    }

    /** 새로고침 복구용 전체 스냅샷. 프론트는 이거 하나로 화면을 재구성한다. */
    @GetMapping("/users/{userId}/timeline")
    public ResponseEntity<DemoTimelineDto> timeline(@PathVariable Long userId) {
        return ResponseEntity.ok(demoService.getTimeline(userId));
    }

    /**
     * 예측 요청 이력. "기록" 탭이 날짜별 상태/지연/피처개수를 붙이는 데 쓴다.
     *
     * <p>파이썬 연동 후 1차 진단 창구다 — 요청이 나갔는지, 몇 ms 걸렸는지,
     * 실패 사유가 뭔지, 피처를 44개 다 보냈는지가 여기서 보인다.
     */
    @GetMapping("/users/{userId}/jobs")
    public ResponseEntity<List<PredictionJobDto>> jobs(@PathVariable Long userId) {
        return ResponseEntity.ok(demoService.getJobs(userId));
    }
}
