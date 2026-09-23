package com.example.wgo.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("wgo.embedding")
public record EmbeddingProperties(@NotBlank String provider, @Min(1) int dimensions) {}
