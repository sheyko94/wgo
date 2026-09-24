package com.example.wgo.observation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationRepository extends JpaRepository<Observation, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Observation o where o.id = :id")
    java.util.Optional<Observation> findLockedById(UUID id);

    List<Observation> findAllByEventIdIn(Collection<UUID> eventIds);
}
