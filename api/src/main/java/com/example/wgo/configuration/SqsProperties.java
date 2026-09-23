package com.example.wgo.configuration;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wgo.sqs")
public record SqsProperties(
        URI endpoint, String region, String accessKey, String secretKey, String observationQueueName) {}
