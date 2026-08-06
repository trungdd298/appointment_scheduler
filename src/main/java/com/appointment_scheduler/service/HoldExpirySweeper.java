package com.appointment_scheduler.service;

import com.appointment_scheduler.repository.AppointmentRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A HELD row past its TTL still occupies the slot as far as the EXCLUDE
 * constraints are concerned (they apply to status IN ('HELD','CONFIRMED')
 * regardless of expires_at) — this sweep is what actually releases it for
 * other bookers. See .claude/docs/booking-flow.md.
 */
@Component
@RequiredArgsConstructor
public class HoldExpirySweeper {

    private final AppointmentRepository appointmentRepository;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expireStaleHolds() {
        appointmentRepository.expireStaleHolds(OffsetDateTime.now());
    }
}
