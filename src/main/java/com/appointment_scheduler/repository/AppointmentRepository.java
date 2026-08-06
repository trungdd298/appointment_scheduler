package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {}
