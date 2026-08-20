package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.repository.PeriodPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PeriodPredictionService {

    private final PeriodPredictionRepository periodPredictionRepository;
}
