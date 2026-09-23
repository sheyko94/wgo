package com.example.wgo.observation;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateObservationResponse(@NotNull UUID observationId) {}
