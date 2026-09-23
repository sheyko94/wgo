package com.example.wgo.observation;

import com.example.wgo.observation.Observation.ProcessingStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class ObservationService {

    private final ObservationRepository observations;
    private final ObservationPublisher publisher;
    private final TransactionTemplate transaction;

    public ObservationService(
            ObservationRepository observations,
            ObservationPublisher publisher,
            PlatformTransactionManager transactionManager) {
        this.observations = observations;
        this.publisher = publisher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CreateObservationResponse create(CreateObservationRequest request) {
        Observation observation = Observation.builder()
                .id(UUID.randomUUID())
                .text(request.text())
                .location(request.location())
                .observedAt(request.observedAt())
                .processingStatus(ProcessingStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        transaction.executeWithoutResult(status -> observations.saveAndFlush(observation));
        log.info("Persisted observation id={} status={}", observation.id(), observation.processingStatus());
        // Commit before publishing so a consumer can immediately read the observation.
        // v0.1 accepts the commit/publish gap; there is no transactional outbox.
        publisher.publish(observation.id());
        log.debug("Published observation id={} for processing", observation.id());
        return new CreateObservationResponse(observation.id());
    }

    @Transactional
    public void markProcessing(UUID observationId) {
        Observation observation = findForUpdate(observationId);
        observation.markProcessing();
        observations.saveAndFlush(observation);
        log.debug("Observation id={} marked PROCESSING", observationId);
    }

    @Transactional
    public void markProcessed(UUID observationId) {
        Observation observation = findForUpdate(observationId);
        observation.markProcessed();
        observations.saveAndFlush(observation);
        log.info("Observation id={} marked PROCESSED", observationId);
    }

    @Transactional
    public void markFailed(UUID observationId) {
        Observation observation = findForUpdate(observationId);
        observation.markFailed();
        observations.saveAndFlush(observation);
        log.warn("Observation id={} marked FAILED", observationId);
    }

    private Observation findForUpdate(UUID observationId) {
        return observations
                .findById(observationId)
                .orElseThrow(() -> new IllegalArgumentException("Observation not found: " + observationId));
    }
}
