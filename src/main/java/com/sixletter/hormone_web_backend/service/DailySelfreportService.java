package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.repository.DailySelfreportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailySelfreportService {

    private final DailySelfreportRepository dailySelfreportRepository;
}
