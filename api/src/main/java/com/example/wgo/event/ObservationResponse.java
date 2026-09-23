package com.example.wgo.event;

import com.example.wgo.observation.Observation;
import java.time.Instant;
import java.util.UUID;

public record ObservationResponse(
        UUID id,
        String text,
        double latitude,
        double longitude,
        Instant observedAt,
        Observation.ProcessingStatus processingStatus) {}
