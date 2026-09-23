package com.example.wgo.event;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "observations", source = "observations")
    EventResponse toResponse(Event event, java.util.List<ObservationResponse> observations);
}
