package com.example.wgo.search;

import java.util.UUID;

public record EventCandidate(UUID eventId, double similarity) {}
