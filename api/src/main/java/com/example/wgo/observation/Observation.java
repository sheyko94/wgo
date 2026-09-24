package com.example.wgo.observation;

import com.example.wgo.location.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity(name = "Observation")
@Table(name = "observations")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Observation {
    public enum ProcessingStatus {
        PENDING,
        PROCESSING,
        PROCESSED,
        FAILED
    }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "requested_event_id")
    private UUID requestedEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private ProcessingStatus processingStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Builder
    public Observation(
            UUID id,
            String text,
            GeoPoint location,
            Instant observedAt,
            UUID eventId,
            UUID requestedEventId,
            ProcessingStatus processingStatus,
            Instant createdAt,
            Instant processedAt) {
        this.id = id;
        this.text = text;
        this.latitude = location.latitude();
        this.longitude = location.longitude();
        this.observedAt = observedAt;
        this.eventId = eventId;
        this.requestedEventId = requestedEventId;
        this.processingStatus = processingStatus;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public GeoPoint location() {
        return new GeoPoint(latitude, longitude);
    }

    public UUID getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void assignToEvent(UUID eventId) {
        this.eventId = eventId;
    }

    public void markProcessing() {
        if (processingStatus == ProcessingStatus.PROCESSING) {
            return;
        }
        requireStatus(ProcessingStatus.PENDING, ProcessingStatus.FAILED);
        this.processingStatus = ProcessingStatus.PROCESSING;
        this.processedAt = null;
    }

    public void markProcessed() {
        if (processingStatus == ProcessingStatus.PROCESSED) {
            return;
        }
        requireStatus(ProcessingStatus.PROCESSING);
        this.processingStatus = ProcessingStatus.PROCESSED;
        this.processedAt = Instant.now();
    }

    public void markFailed() {
        if (processingStatus == ProcessingStatus.FAILED) {
            return;
        }
        requireStatus(ProcessingStatus.PENDING, ProcessingStatus.PROCESSING);
        this.processingStatus = ProcessingStatus.FAILED;
        this.processedAt = null;
    }

    private void requireStatus(ProcessingStatus... allowedStatuses) {
        for (ProcessingStatus allowedStatus : allowedStatuses) {
            if (processingStatus == allowedStatus) {
                return;
            }
        }
        throw new IllegalStateException("Cannot transition observation from " + processingStatus);
    }
}
