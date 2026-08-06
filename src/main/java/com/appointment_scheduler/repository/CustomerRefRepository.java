package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.CustomerRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRefRepository extends JpaRepository<CustomerRef, Long> {}
