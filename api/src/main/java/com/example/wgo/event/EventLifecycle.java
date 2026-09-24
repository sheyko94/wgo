package com.example.wgo.event;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventLifecycle {
    private final EventRepository events;

    @Value("${wgo.events.active-window:PT24H}")
    private Duration activeWindow;

    @Transactional
    @Scheduled(fixedDelayString = "${wgo.events.lifecycle-poll-delay-ms:60000}")
    public void reconcile() {
        Instant cutoff = Instant.now().minus(activeWindow);
        events.reconcileStatus(EventStatus.INACTIVE, cutoff);
        events.reconcileStatus(EventStatus.ACTIVE, cutoff);
    }
}
