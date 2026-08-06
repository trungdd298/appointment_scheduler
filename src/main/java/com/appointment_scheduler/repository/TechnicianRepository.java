package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.Technician;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {}
