package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.TechnicianServiceType;
import com.appointment_scheduler.domain.TechnicianServiceTypeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicianServiceTypeRepository
        extends JpaRepository<TechnicianServiceType, TechnicianServiceTypeId> {}
