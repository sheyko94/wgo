package com.example.wgo.search;

import com.example.wgo.configuration.BedrockProperties;
import com.example.wgo.configuration.EmbeddingProperties;
import com.example.wgo.configuration.OpenSearchProperties;
import com.example.wgo.event.Event;
import com.example.wgo.location.GeoPoint;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.update_aliases.Action;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventIndex {

    private final OpenSearchProperties properties;
    private final OpenSearchClient client;
    private final EmbeddingProperties embeddingProperties;
    private final BedrockProperties bedrockProperties;

    public void index(Event event, List<Double> embedding) {
        log.debug("Indexing event id={} into index alias={}", event.getId(), properties.indexName());
        validateEmbedding(embedding);
        ensureIndex();
        Map<String, Object> document = new HashMap<>();
        document.put("title", event.getTitle());
        document.put("location", Map.of("lat", event.getLatitude(), "lon", event.getLongitude()));
        document.put("startedAt", event.getStartedAt().toString());
        document.put("lastObservedAt", event.getLastObservedAt().toString());
        document.put("embedding", embedding);
        try {
            client.index(request -> request.index(properties.indexName())
                    .id(event.getId().toString())
                    .refresh(Refresh.WaitFor)
                    .document(document));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not index event in OpenSearch", exception);
        }
        log.info("Indexed event id={} for index={}", event.getId(), properties.indexName());
    }

    public List<EventCandidate> findCandidates(GeoPoint location, Instant observedAt, List<Double> embedding) {
        validateEmbedding(embedding);
        ensureIndex();
        Instant from = observedAt.minus(properties.timeWindow());
        Instant to = observedAt.plus(properties.timeWindow());
        try {
            SearchResponse<JsonData> response = client.search(
                    search -> search.index(properties.indexName())
                            .size(properties.topK())
                            .minScore(properties.minimumSimilarity() + 1.0)
                            .query(candidateQuery(location, from, to, embedding)),
                    JsonData.class);
            List<EventCandidate> candidates = response.hits().hits().stream()
                    .map(hit -> new EventCandidate(UUID.fromString(hit.id()), hit.score() - 1.0))
                    .toList();
            log.debug("OpenSearch candidate lookup returned {} candidates", candidates.size());
            return candidates;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not search OpenSearch", exception);
        }
    }

    private Query candidateQuery(GeoPoint location, Instant from, Instant to, List<Double> embedding) {
        Query geoDistance = new Query.Builder()
                .geoDistance(distance -> distance.field("location")
                        .distance(properties.maxDistanceMeters() + "m")
                        .location(point -> point.latlon(
                                latlon -> latlon.lat(location.latitude()).lon(location.longitude()))))
                .build();
        Query startedBefore = new Query.Builder()
                .range(range -> range.field("startedAt").lte(JsonData.of(to.toString())))
                .build();
        Query observedAfter = new Query.Builder()
                .range(range -> range.field("lastObservedAt").gte(JsonData.of(from.toString())))
                .build();
        Query filtered = new Query.Builder()
                .bool(bool -> bool.filter(List.of(geoDistance, startedBefore, observedAfter)))
                .build();
        return new Query.Builder()
                .scriptScore(script -> script.query(filtered)
                        .script(scoring -> scoring.inline(
                                inline -> inline.source("cosineSimilarity(params.query_vector, doc['embedding']) + 1.0")
                                        .params("query_vector", JsonData.of(embedding)))))
                .build();
    }

    private void ensureIndex() {
        try {
            if (client.indices()
                    .existsAlias(request -> request.name(properties.indexName()))
                    .value()) {
                return;
            }
            String physicalIndex = physicalIndexName();
            if (client.indices()
                    .exists(request -> request.index(properties.indexName()))
                    .value()) {
                log.warn(
                        "OpenSearch index={} is a legacy concrete index; alias migration is required",
                        properties.indexName());
                return;
            }
            if (!client.indices()
                    .exists(request -> request.index(physicalIndex))
                    .value()) {
                client.indices().create(request -> request.index(physicalIndex).mappings(mapping -> mapping.properties(
                                "title", property -> property.text(text -> text))
                        .properties("location", property -> property.geoPoint(point -> point))
                        .properties("startedAt", property -> property.date(date -> date))
                        .properties("lastObservedAt", property -> property.date(date -> date))
                        .properties(
                                "embedding",
                                property -> property.knnVector(vector -> vector.dimension(properties.dimensions())
                                        .spaceType("cosinesimil")))));
            }
            client.indices()
                    .updateAliases(
                            request -> request.actions(Action.of(action -> action.add(add -> add.index(physicalIndex)
                                    .alias(properties.indexName())
                                    .isWriteIndex(true)))));
            log.info("Created OpenSearch index={} and alias={}", physicalIndex, properties.indexName());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create OpenSearch index", exception);
        }
    }

    private String physicalIndexName() {
        String model = embeddingProperties.provider().equalsIgnoreCase("bedrock")
                ? bedrockProperties.inferenceModel()
                : embeddingProperties.provider();
        String safeModel = model.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return properties.indexName() + "-" + safeModel + "-" + properties.dimensions();
    }

    private void validateEmbedding(List<Double> embedding) {
        if (embedding == null
                || embedding.size() != properties.dimensions()
                || embedding.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("Embedding must contain " + properties.dimensions() + " finite values");
        }
    }
}
