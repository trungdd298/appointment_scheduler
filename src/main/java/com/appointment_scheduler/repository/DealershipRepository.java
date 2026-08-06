package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.Dealership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealershipRepository extends JpaRepository<Dealership, Long> {}
