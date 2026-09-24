package com.example.wgo.event;

import com.example.wgo.observation.Observation;
import com.example.wgo.observation.ObservationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository events;
    private final com.example.wgo.matching.EmbeddingService embeddings;
    private final com.example.wgo.search.EventIndex index;
    private final ObservationRepository observations;
    private final EventMapper eventMapper;
    private final ObservationMapper observationMapper;

    public List<EventResponse> search(String query, List<EventCategory> categories, List<EventStatus> statuses) {
        List<EventCategory> selectedCategories = categories == null ? List.of(EventCategory.values()) : categories;
        List<EventStatus> selectedStatuses = statuses == null ? List.of(EventStatus.values()) : statuses;
        if (selectedCategories.isEmpty() || selectedStatuses.isEmpty()) return List.of();
        var eligible =
                events.findAllByCategoryInAndStatusInOrderByStartedAtDescIdAsc(selectedCategories, selectedStatuses);
        if (query.isBlank() || eligible.isEmpty()) return toResponses(eligible);
        var eventById = eligible.stream().collect(Collectors.toMap(Event::getId, event -> event));
        var ranked = index.findSemanticCandidates(
                embeddings.embed(query), eligible.stream().map(Event::getId).toList());
        return toResponses(ranked.stream()
                .map(candidate -> eventById.get(candidate.eventId()))
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    public List<EventResponse> findAll() {
        return toResponses(events.findAll().stream()
                .sorted(Comparator.comparing(Event::getStartedAt).reversed())
                .toList());
    }

    public List<EventResponse> findByIds(List<UUID> ids) {
        Map<UUID, Event> eventById =
                events.findAllById(ids).stream().collect(Collectors.toMap(Event::getId, event -> event));
        return ids.stream().filter(eventById::containsKey).map(eventById::get).toList().stream()
                .map(event -> toResponses(List.of(event)).getFirst())
                .toList();
    }

    private List<EventResponse> toResponses(List<Event> eventList) {
        Set<UUID> eventIds = eventList.stream().map(Event::getId).collect(Collectors.toSet());
        Map<UUID, List<ObservationResponse>> observationsByEvent = eventIds.isEmpty()
                ? Map.of()
                : observations.findAllByEventIdIn(eventIds).stream()
                        .collect(Collectors.groupingBy(
                                Observation::eventId,
                                Collectors.mapping(observationMapper::toResponse, Collectors.toList())));
        return eventList.stream()
                .map(event -> eventMapper.toResponse(event, observationsByEvent.getOrDefault(event.getId(), List.of())))
                .toList();
    }
}
