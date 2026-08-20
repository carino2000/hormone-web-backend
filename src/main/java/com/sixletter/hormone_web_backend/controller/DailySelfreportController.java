package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.service.DailySelfreportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-selfreports")
@RequiredArgsConstructor
public class DailySelfreportController {

    private final DailySelfreportService dailySelfreportService;
}
