package com.example.wgo.configuration;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("wgo.search")
public record OpenSearchProperties(
        @NotNull URI endpoint,
        @NotBlank String indexName,
        @NotNull @Min(1) Integer dimensions,
        @NotNull @Min(1) Integer topK,
        @NotNull @Min(1) Long maxDistanceMeters,
        @NotNull Duration timeWindow,
        @NotNull @DecimalMin("-1.0") @DecimalMax("1.0") Double minimumSimilarity) {}
