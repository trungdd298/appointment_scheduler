package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.ServiceTypeDurationOverride;
import com.appointment_scheduler.domain.ServiceTypeDurationOverrideId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTypeDurationOverrideRepository
        extends JpaRepository<ServiceTypeDurationOverride, ServiceTypeDurationOverrideId> {}
