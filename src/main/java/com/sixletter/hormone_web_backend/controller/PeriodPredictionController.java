package com.sixletter.hormone_web_backend.controller;

import com.sixletter.hormone_web_backend.service.PeriodPredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/period-predictions")
@RequiredArgsConstructor
public class PeriodPredictionController {

    private final PeriodPredictionService periodPredictionService;
}
