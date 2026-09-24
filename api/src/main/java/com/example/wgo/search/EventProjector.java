package com.example.wgo.search;

import com.example.wgo.event.EventRepository;
import com.example.wgo.matching.EmbeddingService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventProjector {
    private final EventRepository events;
    private final EventIndex index;
    private final EmbeddingService embeddings;

    @Transactional
    public void project(UUID id) {
        var event = events.findLockedById(id).orElseThrow();
        if (!event.isProjectionPending()) return;
        String text = event.getTitle() + (event.getSummary() == null ? "" : "\n" + event.getSummary());
        index.index(event, embeddings.embed(text));
        event.setProjectionPending(false);
    }
}
