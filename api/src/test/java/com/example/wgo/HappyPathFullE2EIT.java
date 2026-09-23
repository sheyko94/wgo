package com.example.wgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wgo.configuration.SqsProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "wgo.sqs.initial-delay-ms=0")
@AutoConfigureMockMvc
class HappyPathFullE2EIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17@sha256:f4c66b820c6f974249089d3d16d86a3698eae11e8746eb6644b2271031e91232")
                    .asCompatibleSubstituteFor("postgres"));
    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse(
                            "localstack/localstack:4.14.0@sha256:3ebc37595918b8accb852f8048fef2aff047d465167edd655528065b07bc364a")
                    .asCompatibleSubstituteFor("localstack/localstack"))
            .withServices(LocalStackContainer.Service.SQS);
    private static final GenericContainer<?> OPENSEARCH = new GenericContainer<>(DockerImageName.parse(
                            "opensearchproject/opensearch:3.8.0@sha256:fafe3fc3587088674669235575aa166228c48bdb940294a8cdbbc1da75236a40")
                    .asCompatibleSubstituteFor("opensearchproject/opensearch"))
            .withEnv("discovery.type", "single-node")
            .withEnv("bootstrap.memory_lock", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/_cluster/health")
                    .forPort(9200)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        POSTGRES.start();
        LOCALSTACK.start();
        OPENSEARCH.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("wgo.sqs.endpoint", () -> LOCALSTACK
                .getEndpointOverride(LocalStackContainer.Service.SQS)
                .toString());
        registry.add("wgo.sqs.access-key", LOCALSTACK::getAccessKey);
        registry.add("wgo.sqs.secret-key", LOCALSTACK::getSecretKey);
        registry.add(
                "wgo.search.endpoint", () -> "http://" + OPENSEARCH.getHost() + ":" + OPENSEARCH.getMappedPort(9200));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    SqsClient sqs;

    @Autowired
    SqsProperties sqsProperties;

    private String queueUrl;

    @BeforeEach
    void cleanQueue() {
        queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                        .queueName(sqsProperties.observationQueueName())
                        .build())
                .queueUrl();
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
    }

    @Test
    void postsProcessesProjectsAndListsEvents() throws Exception {
        for (String resource : List.of("fire-rotterdam-1.json", "fire-rotterdam-2.json", "storm-amsterdam.json")) {
            mvc.perform(post("/v1/observations")
                            .contentType("application/json")
                            .content(read("e2e/observations/" + resource)))
                    .andExpect(status().isAccepted());
        }

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    JsonNode events = json.readTree(mvc.perform(get("/v1/events"))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString());
                    assertThat(events.isArray()).isTrue();
                    assertThat(events.size()).isEqualTo(2);
                    List<String> titles = new ArrayList<>();
                    for (JsonNode event : events) titles.add(event.path("title").asText());
                    assertThat(titles)
                            .contains(
                                    "Large fire near Rotterdam Centraal", "Severe storm flooding streets in Amsterdam");
                    assertThat(sqs.receiveMessage(request ->
                                            request.queueUrl(queueUrl).maxNumberOfMessages(10))
                                    .messages())
                            .isEmpty();
                });
    }

    private String read(String path) throws IOException {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
