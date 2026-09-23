package com.example.wgo.observation;

import com.example.wgo.location.GeoPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateObservationRequest(
        @NotBlank String text, @NotNull @Valid GeoPoint location, @NotNull Instant observedAt) {}
