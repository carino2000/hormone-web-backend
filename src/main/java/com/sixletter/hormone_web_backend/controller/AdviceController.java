package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.config.AnthropicProperties;
import com.sixletter.hormone_web_backend.dto.AdviceDto;
import com.sixletter.hormone_web_backend.service.AdviceService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "오늘의 조언" API.
 *
 * <p>생성은 POST 다. 외부 API 를 호출하고 DB 에 쓰므로 부작용이 있고,
 * GET 으로 두면 프록시가 캐싱할 수 있다.
 */
@RestController
@RequestMapping("/api/advice")
@RequiredArgsConstructor
public class AdviceController {

    private final AdviceService adviceService;
    private final AnthropicProperties anthropicProperties;

    /**
     * 기능이 켜져 있는지. 프론트가 토글을 비활성화할지 판단하는 데 쓴다.
     * <b>키 자체는 절대 내려주지 않는다.</b>
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "enabled", anthropicProperties.isEnabled(),
                "model", anthropicProperties.getModel(),
                "historyDays", anthropicProperties.getHistoryDays()));
    }

    /** 지난 조언 목록 (최신순). */
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<AdviceDto>> list(@PathVariable Long userId) {
        return ResponseEntity.ok(adviceService.list(userId).stream().map(AdviceDto::from).toList());
    }

    /**
     * 오늘 날짜의 조언 생성. <b>이미 성공한 날은 재호출하지 않고 저장된 걸 준다.</b>
     *
     * @param force true 면 저장된 게 있어도 다시 생성 ("다시 받기")
     */
    @PostMapping("/users/{userId}")
    public ResponseEntity<AdviceDto> generate(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(AdviceDto.from(adviceService.generate(userId, force)));
    }
}
