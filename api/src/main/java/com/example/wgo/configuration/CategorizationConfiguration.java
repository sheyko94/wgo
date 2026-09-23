package com.example.wgo.configuration;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.example.wgo.event.ClaudeEventCategorizer;
import com.example.wgo.event.EventCategorizer;
import com.example.wgo.event.EventCategory;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class CategorizationConfiguration {
    @Value("${wgo.categorization.enabled:false}")
    private boolean enabled;

    @Value("${wgo.categorization.model:}")
    private String model;

    @Value("${wgo.categorization.api-key:}")
    private String apiKey;

    @PostConstruct
    void logConfiguration() {
        log.info(
                "Claude categorization configuration: enabled={}, model={}, apiKeyConfigured={}",
                enabled,
                model,
                !apiKey.isBlank());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "wgo.categorization.enabled", havingValue = "true")
    AnthropicClient anthropicClient(@Value("${wgo.categorization.api-key}") String apiKey) {
        if (apiKey.isBlank())
            throw new IllegalArgumentException("ANTHROPIC_API_KEY is required when categorization is enabled");
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(20))
                .maxRetries(0)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "wgo.categorization.enabled", havingValue = "true")
    EventCategorizer claudeEventCategorizer(
            AnthropicClient client, ObjectMapper json, @Value("${wgo.categorization.model}") String model) {
        return new ClaudeEventCategorizer(client, json, model);
    }

    @Bean
    @ConditionalOnProperty(name = "wgo.categorization.enabled", havingValue = "false", matchIfMissing = true)
    EventCategorizer disabledEventCategorizer() {
        return report -> EventCategory.OTHER;
    }
}
