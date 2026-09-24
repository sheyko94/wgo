package com.example.wgo.event;

import com.example.wgo.search.EventProjector;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventSummaryWorkflow {
    private final EventRepository events;
    private final EventSummaryStore store;
    private final EventSummarizer summarizer;
    private final EventSummaryCache cache;
    private final EventProjector projector;

    @Value("${wgo.summarization.enabled:false}")
    private boolean enabled;

    @Value("${wgo.events.active-window:PT24H}")
    private Duration activeWindow;

    public EventSummaryResponse summarize(UUID id) {
        if (enabled) refresh(id, true);
        var event = events.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        var result = cache.read(event);
        if (result == null)
            throw new ResponseStatusException(
                    enabled ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE,
                    enabled
                            ? "Summary is being prepared or waiting for its next attempt"
                            : "Event summarization is disabled");
        return result;
    }

    private void refresh(UUID id, boolean manual) {
        var claim = store.claim(id, manual);
        if (claim != null) {
            try {
                var result = summarizer.summarize(claim.reports());
                store.complete(claim, result);
            } catch (RuntimeException exception) {
                store.failed(claim);
                throw exception;
            }
            // Cache/index failures cannot roll back the saved AI result or trigger regeneration.
            events.findById(id).ifPresent(cache::read);
        }
        try {
            projector.project(id);
        } catch (RuntimeException exception) {
            log.warn("Event {} projection pending retry", id);
        }
    }

    @Scheduled(
            fixedDelayString = "${wgo.summarization.poll-delay-ms:60000}",
            initialDelayString = "${wgo.summarization.poll-delay-ms:60000}")
    public void refreshActiveEvents() {
        if (!enabled) return;
        Instant now = Instant.now();
        for (UUID id : events.findSummaryDue(now.minus(activeWindow), now, PageRequest.of(0, 20))) {
            try {
                refresh(id, false);
            } catch (RuntimeException exception) {
                log.warn(
                        "Event {} summary refresh deferred: {}",
                        id,
                        exception.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(
            fixedDelayString = "${wgo.summarization.projection-poll-delay-ms:10000}",
            initialDelayString = "${wgo.summarization.projection-poll-delay-ms:10000}")
    public void retryProjections() {
        for (UUID id : events.findProjectionPending(PageRequest.of(0, 20))) {
            try {
                projector.project(id);
            } catch (RuntimeException exception) {
                log.warn("Event {} projection still pending", id);
            }
        }
    }
}
