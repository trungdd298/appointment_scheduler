package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.ResourceUnavailability;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceUnavailabilityRepository extends JpaRepository<ResourceUnavailability, Long> {

    /** Idempotency check for events replayed by the external system. */
    Optional<ResourceUnavailability> findByExternalEventId(String externalEventId);
}
