package com.appointment_scheduler.web;

import com.appointment_scheduler.service.ResourceUnavailabilityService;
import com.appointment_scheduler.web.dto.AppointmentResponse;
import com.appointment_scheduler.web.dto.ReassignTechnicianRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ops resolving NEEDS_ATTENTION — see .claude/docs/reconciliation.md. */
@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class OpsController {

    private final ResourceUnavailabilityService resourceUnavailabilityService;

    @PostMapping("/{id}/reassign-technician")
    public AppointmentResponse reassignTechnician(
            @PathVariable Long id, @Valid @RequestBody ReassignTechnicianRequest request) {
        return AppointmentResponse.from(
                resourceUnavailabilityService.reassignTechnician(id, request.technicianId()));
    }
}
