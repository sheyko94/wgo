package com.example.wgo.configuration;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.example.wgo.event.ClaudeEventSummarizer;
import com.example.wgo.event.EventSummarizer;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class SummarizationConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "wgo.summarization.enabled", havingValue = "true")
    AnthropicClient summaryAnthropicClient(@Value("${wgo.summarization.api-key}") String apiKey) {
        if (apiKey.isBlank())
            throw new IllegalArgumentException("ANTHROPIC_API_KEY is required when summarization is enabled");
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(0)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "wgo.summarization.enabled", havingValue = "true")
    EventSummarizer claudeEventSummarizer(
            @Qualifier("summaryAnthropicClient") AnthropicClient client,
            ObjectMapper json,
            @Value("${wgo.summarization.model}") String model) {
        return new ClaudeEventSummarizer(client, json, model);
    }

    @Bean
    @ConditionalOnProperty(name = "wgo.summarization.enabled", havingValue = "false", matchIfMissing = true)
    EventSummarizer disabledEventSummarizer() {
        return observations -> {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event summarization is disabled");
        };
    }
}
