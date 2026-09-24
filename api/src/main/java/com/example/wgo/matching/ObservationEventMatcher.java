package com.example.wgo.matching;

import com.example.wgo.event.Event;
import com.example.wgo.event.EventCategorizer;
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
    private final EventCategorizer categorizer;

    @org.springframework.beans.factory.annotation.Value("${wgo.events.active-window:PT24H}")
    private java.time.Duration activeWindow;

    @Transactional
    public MatchResult match(UUID observationId) {
        Observation observation = observations
                .findLockedById(observationId)
                .orElseThrow(() -> new IllegalArgumentException("Observation not found: " + observationId));
        if (observation.eventId() != null) {
            log.debug("Observation id={} is already assigned to event id={}", observationId, observation.eventId());
            return new MatchResult(observation.id(), observation.eventId(), false);
        }

        Event event = observation.requestedEventId() == null
                ? findExistingEvent(observation)
                : events.findLockedById(observation.requestedEventId())
                        .orElseThrow(() -> new IllegalStateException("Requested event no longer exists"));
        boolean created = event == null;
        if (created) {
            event = Event.builder()
                    .id(UUID.randomUUID())
                    .title(observation.text())
                    .category(categorizer.categorize(observation.text()))
                    .location(observation.location())
                    .startedAt(observation.observedAt())
                    .lastObservedAt(observation.observedAt())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
        } else {
            event.extendTimeRange(observation.observedAt());
            // if (Objects.isNull(event.getCategory())) {
            event.setCategory(categorizer.categorize(observation.text()));
            // }
        }
        event.updateActivityStatus(Instant.now().minus(activeWindow));
        event.setObservationRevision(event.getObservationRevision() + 1);
        event.setProjectionPending(true);
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
                .map(events::findLockedById)
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
