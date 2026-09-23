package com.example.wgo.event;

import com.example.wgo.observation.Observation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ObservationMapper {

    ObservationResponse toResponse(Observation observation);
}
