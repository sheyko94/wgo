package com.example.wgo.event;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventSummaryCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    @Value("${wgo.summarization.cache-ttl:PT24H}")
    private Duration ttl;

    public EventSummaryResponse read(Event event) {
        if (event.getSummaryPayload() == null) return null;
        // Versioned keys prevent an older read or writer from replacing a newer summary.
        String key = "wgo:event-summary:v1:" + event.getId() + ":" + event.getSummaryRevision();
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return json.readValue(cached, EventSummaryResponse.class);
            redis.opsForValue().set(key, event.getSummaryPayload(), ttl);
        } catch (RuntimeException exception) {
            log.debug("Summary cache unavailable; using PostgreSQL for event {}", event.getId());
        }
        return json.readValue(event.getSummaryPayload(), EventSummaryResponse.class);
    }
}
