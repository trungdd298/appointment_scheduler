package com.appointment_scheduler.service;

import com.appointment_scheduler.repository.AppointmentRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safety net for NEEDS_ATTENTION appointments: if ops hasn't resolved one
 * (via ResourceUnavailabilityService#reassignTechnician) by its
 * escalation_deadline, auto-cancel it so the customer is never left
 * showing up to no one available. See .claude/docs/reconciliation.md.
 */
@Component
@RequiredArgsConstructor
public class EscalationSweeper {

    private final AppointmentRepository appointmentRepository;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void cancelUnresolvedEscalations() {
        appointmentRepository.cancelUnresolvedEscalations(OffsetDateTime.now());
    }
}
