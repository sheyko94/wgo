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
    private final ObservationRepository observations;
    private final EventMapper eventMapper;
    private final ObservationMapper observationMapper;

    public List<EventResponse> findAll() {
        List<Event> eventList = events.findAll().stream()
                .sorted(Comparator.comparing(Event::getStartedAt).reversed())
                .toList();
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
