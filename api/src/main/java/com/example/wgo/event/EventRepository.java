package com.example.wgo.event;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findLockedById(UUID id);

    @Query(
            "select e.id from Event e where e.status = com.example.wgo.event.EventStatus.ACTIVE and e.lastObservedAt >= :activeSince and e.observationRevision > e.summaryRevision and e.summaryNextAttemptAt <= :now order by e.summaryNextAttemptAt, e.id")
    List<UUID> findSummaryDue(Instant activeSince, Instant now, Pageable page);

    @Query("select e.id from Event e where e.projectionPending = true order by e.updatedAt, e.id")
    List<UUID> findProjectionPending(Pageable page);

    @org.springframework.data.jpa.repository.Modifying
    @Query(
            "update Event e set e.status = :status, e.projectionPending = true where e.status <> :status and ((:status = com.example.wgo.event.EventStatus.INACTIVE and e.lastObservedAt < :activeSince) or (:status = com.example.wgo.event.EventStatus.ACTIVE and e.lastObservedAt >= :activeSince))")
    int reconcileStatus(EventStatus status, Instant activeSince);
}
