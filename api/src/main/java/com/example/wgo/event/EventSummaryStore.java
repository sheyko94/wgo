package com.example.wgo.event;

import com.example.wgo.observation.Observation;
import com.example.wgo.observation.ObservationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class EventSummaryStore {
    private final EventRepository events;
    private final ObservationRepository observations;
    private final ObjectMapper json;

    @Value("${wgo.events.active-window:PT24H}")
    private Duration activeWindow;

    @Value("${wgo.summarization.refresh-interval:PT15M}")
    private Duration refreshInterval;

    public record Claim(UUID eventId, UUID token, long revision, List<Observation> reports) {}

    @Transactional
    public Claim claim(UUID id, boolean manual) {
        Event event = events.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        Instant now = Instant.now();
        event.updateActivityStatus(now.minus(activeWindow));
        boolean active = event.getStatus() == EventStatus.ACTIVE;
        if (event.getObservationRevision() <= event.getSummaryRevision()
                || (!active && (!manual || event.getSummaryPayload() != null))
                || event.getSummaryNextAttemptAt().isAfter(now)) return null;
        var reports = observations.findAllByEventIdIn(Set.of(id)).stream()
                .sorted(Comparator.comparing(Observation::observedAt).thenComparing(Observation::id))
                .toList();
        if (reports.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event has no linked observations");
        UUID token = UUID.randomUUID();
        event.setSummaryClaim(token);
        // Durable lease survives process crashes; provider timeout is 60 seconds.
        event.setSummaryNextAttemptAt(now.plus(Duration.ofMinutes(5)));
        return new Claim(id, token, event.getObservationRevision(), reports);
    }

    @Transactional
    public void complete(Claim claim, EventSummaryResponse result) {
        Event event = events.findLockedById(claim.eventId()).orElseThrow();
        if (!claim.token().equals(event.getSummaryClaim())) return;
        event.setTitle(result.title());
        event.setSummary(result.summary());
        event.setSummaryPayload(json.writeValueAsString(result));
        event.setSummaryGeneratedAt(result.generatedAt());
        event.setSummaryRevision(claim.revision());
        event.setSummaryClaim(null);
        event.setSummaryNextAttemptAt(Instant.now().plus(refreshInterval));
        event.setProjectionPending(true);
        event.setUpdatedAt(Instant.now());
        // Reports arriving during generation have a higher revision and remain due.
    }

    @Transactional
    public void failed(Claim claim) {
        events.findLockedById(claim.eventId()).ifPresent(event -> {
            if (claim.token().equals(event.getSummaryClaim())) {
                event.setSummaryClaim(null);
                event.setSummaryNextAttemptAt(Instant.now().plus(refreshInterval));
            }
        });
    }
}
