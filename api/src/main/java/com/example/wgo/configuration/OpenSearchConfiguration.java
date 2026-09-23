package com.example.wgo.configuration;

import java.net.URI;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenSearchProperties.class)
public class OpenSearchConfiguration {

    @Bean
    OpenSearchClient openSearchClient(OpenSearchProperties properties) {
        URI endpoint = properties.endpoint();
        HttpHost host = new HttpHost(endpoint.getScheme(), endpoint.getHost(), endpoint.getPort());
        return new OpenSearchClient(
                ApacheHttpClient5TransportBuilder.builder(host).build());
    }
}
