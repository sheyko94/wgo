package com.example.wgo.event;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class EventMapper {
    @org.springframework.beans.factory.annotation.Autowired
    protected EventSummaryCache summaryCache;

    @Mapping(target = "observations", source = "observations")
    @Mapping(target = "generatedSummary", expression = "java(summaryCache.read(event))")
    @Mapping(target = "summaryStale", expression = "java(event.getSummaryRevision() < event.getObservationRevision())")
    public abstract EventResponse toResponse(Event event, java.util.List<ObservationResponse> observations);
}
