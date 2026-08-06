package com.appointment_scheduler.web;

import com.appointment_scheduler.service.AvailabilityService;
import com.appointment_scheduler.service.dto.AvailabilitySlot;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /dealerships/{id}/availability — see .claude/docs/booking-flow.md#browse-then-commit. */
@RestController
@RequestMapping("/dealerships/{dealershipId}/availability")
@RequiredArgsConstructor
@Validated
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    public List<AvailabilitySlot> getAvailability(
            @PathVariable Long dealershipId,
            @RequestParam Long vehicleId,
            @RequestParam Long serviceTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime near,
            @RequestParam(defaultValue = "2") @Min(1) @Max(12) int windowHours) {
        return availabilityService.findAvailability(dealershipId, vehicleId, serviceTypeId, near, windowHours);
    }
}
