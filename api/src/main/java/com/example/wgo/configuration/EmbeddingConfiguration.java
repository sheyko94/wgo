package com.example.wgo.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({EmbeddingProperties.class, BedrockProperties.class})
public class EmbeddingConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "wgo.embedding.provider", havingValue = "bedrock")
    BedrockRuntimeClient bedrockRuntimeClient(BedrockProperties properties) {
        return BedrockRuntimeClient.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .build();
    }
}
