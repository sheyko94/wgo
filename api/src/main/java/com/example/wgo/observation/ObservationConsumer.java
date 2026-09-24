package com.example.wgo.observation;

import com.example.wgo.configuration.SqsProperties;
import com.example.wgo.matching.MatchResult;
import com.example.wgo.matching.ObservationEventMatcher;
import com.example.wgo.messaging.SqsMessageReceiver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

@Component
@RequiredArgsConstructor
@Slf4j
public class ObservationConsumer {

    private final SqsClient sqs;
    private final SqsProperties properties;
    private final SqsMessageReceiver messages;
    private final ObservationService observations;
    private final ObservationEventMatcher matcher;
    private final com.example.wgo.search.EventProjector projector;

    @Scheduled(fixedDelayString = "${wgo.sqs.poll-delay-ms:1000}", initialDelayString = "${wgo.sqs.initial-delay-ms:0}")
    public void poll() {
        try {
            SqsMessageReceiver.ReceivedMessages<ObservationMessage> received =
                    messages.receive(properties.observationQueueName(), ObservationMessage.class);
            received.messages().forEach(message -> process(received.queueUrl(), message));
        } catch (Exception exception) {
            log.warn("Unable to poll observation-processing queue", exception);
        }
    }

    private void process(String queueUrl, SqsMessageReceiver.ReceivedMessage<ObservationMessage> received) {
        Message message = received.message();
        UUID observationId = received.payload().observationId();
        try {
            log.info("Processing observation id={}", observationId);
            observations.markProcessing(observationId);
            MatchResult result = matcher.match(observationId);
            log.info(
                    "Matched observation id={} to event id={} created={}",
                    observationId,
                    result.eventId(),
                    result.created());
            projector.project(result.eventId());
            observations.markProcessed(observationId);
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
            log.info("Acknowledged observation id={}", observationId);
        } catch (Exception exception) {
            if (observationId != null) {
                try {
                    observations.markFailed(observationId);
                } catch (Exception stateException) {
                    log.warn("Unable to mark observation {} as failed", observationId, stateException);
                }
            }
            log.warn("Observation processing failed; message will be retried", exception);
        }
    }
}
