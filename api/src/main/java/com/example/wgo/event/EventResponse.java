package com.example.wgo.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String title,
        EventCategory category,
        double latitude,
        double longitude,
        Instant startedAt,
        Instant lastObservedAt,
        List<ObservationResponse> observations) {}
