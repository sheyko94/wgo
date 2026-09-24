package com.example.wgo.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventSummaryResponse(
        String title,
        String summary,
        List<UUID> observationIds,
        String model,
        String promptVersion,
        Instant generatedAt,
        long latencyMs,
        long inputTokens,
        long outputTokens) {}
