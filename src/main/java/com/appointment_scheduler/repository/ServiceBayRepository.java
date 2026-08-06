package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.ServiceBay;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceBayRepository extends JpaRepository<ServiceBay, Long> {}
