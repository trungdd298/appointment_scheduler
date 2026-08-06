package com.appointment_scheduler.web;

import com.appointment_scheduler.repository.ResourceUnavailabilityRepository;
import com.appointment_scheduler.service.ResourceUnavailabilityService;
import com.appointment_scheduler.web.dto.ResourceUnavailabilityRequest;
import com.appointment_scheduler.web.dto.ResourceUnavailabilityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mocked webhook from the Staff/Facility system — see
 * .claude/docs/architecture.md (events are simulated, no real broker for
 * this assessment).
 */
@RestController
@RequestMapping("/internal/resource-unavailability")
@RequiredArgsConstructor
public class IntegrationController {

    private final ResourceUnavailabilityService resourceUnavailabilityService;
    private final ResourceUnavailabilityRepository resourceUnavailabilityRepository;

    @PostMapping
    public ResponseEntity<ResourceUnavailabilityResponse> record(
            @Valid @RequestBody ResourceUnavailabilityRequest request) {
        // Checked before calling the service (which independently re-checks
        // for the actual idempotent behavior) purely to pick 200 vs 201 —
        // see .claude/docs/reconciliation.md.
        boolean alreadyProcessed =
                resourceUnavailabilityRepository.findByExternalEventId(request.externalEventId()).isPresent();
        var saved = resourceUnavailabilityService.recordUnavailability(request.toCommand());
        HttpStatus status = alreadyProcessed ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ResourceUnavailabilityResponse.from(saved));
    }
}
