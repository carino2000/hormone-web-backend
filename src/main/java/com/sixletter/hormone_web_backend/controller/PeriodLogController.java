package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.service.PeriodLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/period-logs")
@RequiredArgsConstructor
public class PeriodLogController {

    private final PeriodLogService periodLogService;
}
