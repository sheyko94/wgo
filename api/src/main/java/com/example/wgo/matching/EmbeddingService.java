package com.example.wgo.matching;

import com.example.wgo.configuration.BedrockProperties;
import com.example.wgo.configuration.EmbeddingProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.DoubleStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import tools.jackson.databind.ObjectMapper;

@Service
public class EmbeddingService {

    private final EmbeddingProperties properties;
    private final BedrockProperties bedrockProperties;
    private final ObjectProvider<BedrockRuntimeClient> bedrockClient;
    private final int dimensions;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Double> fixedVector;

    public EmbeddingService(
            EmbeddingProperties properties,
            BedrockProperties bedrockProperties,
            ObjectProvider<BedrockRuntimeClient> bedrockClient) {
        this.properties = properties;
        this.bedrockProperties = bedrockProperties;
        this.bedrockClient = bedrockClient;
        this.dimensions = properties.dimensions();
        this.fixedVector =
                DoubleStream.generate(() -> 1.0).limit(dimensions).boxed().toList();
    }

    public int dimensions() {
        return dimensions;
    }

    public List<Double> embed(String text) {
        List<Double> vector = properties.provider().equalsIgnoreCase("bedrock") ? embedWithBedrock(text) : fixedVector;
        if (vector == null || vector.size() != dimensions) {
            throw new IllegalStateException("Embedding provider must return " + dimensions + " dimensions");
        }
        if (vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalStateException("Embedding components must be finite numbers");
        }
        return List.copyOf(vector);
    }

    private List<Double> embedWithBedrock(String text) {
        try {
            byte[] request = objectMapper.writeValueAsBytes(new TitanRequest(text));
            InvokeModelResponse response = bedrockClient
                    .getObject()
                    .invokeModel(InvokeModelRequest.builder()
                            .modelId(bedrockProperties.inferenceModel())
                            .contentType("application/json")
                            .accept("application/json")
                            .body(SdkBytes.fromByteArray(request))
                            .build());
            TitanResponse body =
                    objectMapper.readValue(response.body().asString(StandardCharsets.UTF_8), TitanResponse.class);
            if (body.embedding() == null || body.embedding().isEmpty()) {
                throw new IllegalStateException("Bedrock returned an empty embedding");
            }
            return body.embedding();
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException illegalState) {
                throw illegalState;
            }
            throw new IllegalStateException("Bedrock embedding request failed", exception);
        }
    }

    public record TitanRequest(String inputText) {}

    public record TitanResponse(List<Double> embedding) {}
}
