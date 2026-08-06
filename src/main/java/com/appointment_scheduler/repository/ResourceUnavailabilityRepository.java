package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.ResourceUnavailability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceUnavailabilityRepository extends JpaRepository<ResourceUnavailability, Long> {}
