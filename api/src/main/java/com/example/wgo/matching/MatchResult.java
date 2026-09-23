package com.example.wgo.matching;

import java.util.UUID;

public record MatchResult(UUID observationId, UUID eventId, boolean created) {}
