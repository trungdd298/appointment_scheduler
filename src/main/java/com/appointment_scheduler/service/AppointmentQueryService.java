package com.appointment_scheduler.service;

import com.appointment_scheduler.domain.Appointment;
import com.appointment_scheduler.domain.AppointmentStatus;
import com.appointment_scheduler.repository.AppointmentRepository;
import com.appointment_scheduler.service.exception.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only queries backing GET /appointments/{id} and GET /appointments. */
@Service
@RequiredArgsConstructor
public class AppointmentQueryService {

    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public Appointment get(Long appointmentId) {
        return appointmentRepository
                .findByIdWithAssociations(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment " + appointmentId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Appointment> list(AppointmentStatus status, Long dealershipId, String vin) {
        return appointmentRepository.findByFilters(status, dealershipId, vin);
    }
}
