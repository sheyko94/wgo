package com.example.wgo.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SqsProperties.class)
public class SqsConfiguration {

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(SqsProperties properties) {
        SqsClientBuilder builder =
                SqsClient.builder().region(Region.of(properties.region())).credentialsProvider(credentials(properties));
        if (properties.endpoint() != null) builder.endpointOverride(properties.endpoint());
        return builder.build();
    }

    private AwsCredentialsProvider credentials(SqsProperties properties) {
        if (properties.accessKey() == null
                || properties.accessKey().isBlank()
                || properties.secretKey() == null
                || properties.secretKey().isBlank()) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
