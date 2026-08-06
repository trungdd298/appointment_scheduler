package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {}
