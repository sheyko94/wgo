package com.example.wgo.observation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/observations")
@RequiredArgsConstructor
@Slf4j
public class ObservationController {

    private final ObservationService observations;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateObservationResponse create(@Valid @RequestBody CreateObservationRequest request) {
        CreateObservationResponse response = observations.create(request);
        log.info("Accepted observation id={}", response.observationId());
        return response;
    }
}
