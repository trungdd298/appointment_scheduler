package com.appointment_scheduler.service;

import com.appointment_scheduler.domain.ServiceType;
import com.appointment_scheduler.domain.ServiceTypeDurationOverrideId;
import com.appointment_scheduler.domain.VehicleCategory;
import com.appointment_scheduler.repository.ServiceTypeDurationOverrideRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.service.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Resolves effective appointment duration. See
 * .claude/docs/booking-flow.md#duration-resolution: an override for
 * (serviceTypeId, vehicleCategory) wins if present, otherwise falls back to
 * the service type's base duration. The client never supplies an arbitrary
 * duration.
 */
@Service
@RequiredArgsConstructor
public class DurationResolver {

    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceTypeDurationOverrideRepository overrideRepository;

    public int resolveDurationMinutes(Long serviceTypeId, VehicleCategory vehicleCategory) {
        if (vehicleCategory != null) {
            var overrideId = new ServiceTypeDurationOverrideId(serviceTypeId, vehicleCategory);
            var override = overrideRepository.findById(overrideId);
            if (override.isPresent()) {
                return override.get().getDurationMinutes();
            }
        }

        ServiceType serviceType =
                serviceTypeRepository
                        .findById(serviceTypeId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Service type " + serviceTypeId + " not found"));
        return serviceType.getDurationMinutes();
    }
}
