package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.repository.DailyFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailyFeatureService {

    private final DailyFeatureRepository dailyFeatureRepository;
}
