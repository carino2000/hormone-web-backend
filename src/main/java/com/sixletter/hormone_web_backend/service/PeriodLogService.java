package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.repository.PeriodLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PeriodLogService {

    private final PeriodLogRepository periodLogRepository;
}
