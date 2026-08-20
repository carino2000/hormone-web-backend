package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.service.DailyFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-features")
@RequiredArgsConstructor
public class DailyFeatureController {

    private final DailyFeatureService dailyFeatureService;
}
