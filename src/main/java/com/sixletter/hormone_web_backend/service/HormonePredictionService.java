package com.sixletter.hormone_web_backend.service;

import com.sixletter.hormone_web_backend.repository.HormonePredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HormonePredictionService {

    private final HormonePredictionRepository hormonePredictionRepository;
}
