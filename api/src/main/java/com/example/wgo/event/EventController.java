package com.example.wgo.event;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {

    private final EventService events;
    private final EventSummaryWorkflow summaries;
    private final com.example.wgo.observation.ObservationService observations;

    @PostMapping("/{id}/observations")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public com.example.wgo.observation.CreateObservationResponse addObservation(
            @PathVariable UUID id,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
                    com.example.wgo.observation.CreateObservationRequest request) {
        return observations.createForEvent(request, id);
    }

    @PostMapping("/{id}/summary")
    public EventResponse summarize(@PathVariable UUID id) {
        summaries.summarize(id);
        return events.findByIds(List.of(id)).getFirst();
    }

    @GetMapping
    public List<EventResponse> list() {
        return events.findAll();
    }

    @GetMapping("/search")
    public List<EventResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) List<EventCategory> categories,
            @RequestParam(required = false) List<EventStatus> statuses) {
        return events.search(q.trim(), categories, statuses);
    }
}
