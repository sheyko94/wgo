package com.example.wgo.observation;

import com.example.wgo.configuration.SqsProperties;
import com.example.wgo.messaging.SqsMessageSender;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ObservationPublisher {

    private final SqsProperties properties;
    private final SqsMessageSender messages;

    public void publish(UUID observationId) {
        log.debug("Publishing observation id={} to queue={}", observationId, properties.observationQueueName());
        messages.send(properties.observationQueueName(), new ObservationMessage(observationId));
        log.info("Published observation id={} to queue={}", observationId, properties.observationQueueName());
    }
}
