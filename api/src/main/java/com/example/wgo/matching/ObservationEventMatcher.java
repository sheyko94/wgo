package com.example.wgo.matching;

import com.example.wgo.event.Event;
import com.example.wgo.event.EventRepository;
import com.example.wgo.observation.Observation;
import com.example.wgo.observation.ObservationRepository;
import com.example.wgo.search.EventCandidate;
import com.example.wgo.search.EventIndex;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObservationEventMatcher {

    private final ObservationRepository observations;
    private final EventRepository events;
    private final EmbeddingService embeddings;
    private final EventIndex index;

    @Transactional
    public MatchResult match(UUID observationId) {
        Observation observation = observations
                .findById(observationId)
                .orElseThrow(() -> new IllegalArgumentException("Observation not found: " + observationId));
        if (observation.eventId() != null) {
            log.debug("Observation id={} is already assigned to event id={}", observationId, observation.eventId());
            return new MatchResult(observation.id(), observation.eventId(), false);
        }

        Event event = findExistingEvent(observation);
        boolean created = event == null;
        if (created) {
            event = Event.builder()
                    .id(UUID.randomUUID())
                    .title(observation.text())
                    .location(observation.location())
                    .startedAt(observation.observedAt())
                    .lastObservedAt(observation.observedAt())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
        } else {
            event.extendTimeRange(observation.observedAt());
        }
        events.save(event);
        observation.assignToEvent(event.getId());
        observations.save(observation);
        log.info("Assigned observation id={} to event id={} created={}", observation.id(), event.getId(), created);
        return new MatchResult(observation.id(), event.getId(), created);
    }

    private Event findExistingEvent(Observation observation) {
        var candidates = index
                .findCandidates(observation.location(), observation.observedAt(), embeddings.embed(observation.text()))
                .stream()
                .map(EventCandidate::eventId)
                .map(events::findById)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElse(null);
        log.debug(
                "Candidate lookup completed for observation id={} matchedEvent={}",
                observation.id(),
                candidates == null ? null : candidates.getId());
        return candidates;
    }
}
