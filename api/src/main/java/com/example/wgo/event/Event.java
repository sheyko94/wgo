package com.example.wgo.event;

import com.example.wgo.location.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "Event")
@Table(name = "events")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Event {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public Event(
            UUID id,
            String title,
            GeoPoint location,
            Instant startedAt,
            Instant lastObservedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.latitude = location.latitude();
        this.longitude = location.longitude();
        this.startedAt = startedAt;
        this.lastObservedAt = lastObservedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public GeoPoint location() {
        return new GeoPoint(latitude, longitude);
    }

    public void extendTimeRange(Instant observedAt) {
        if (observedAt.isBefore(this.startedAt)) this.startedAt = observedAt;
        if (observedAt.isAfter(this.lastObservedAt)) this.lastObservedAt = observedAt;
        this.updatedAt = Instant.now();
    }
}
