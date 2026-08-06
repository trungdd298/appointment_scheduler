package com.appointment_scheduler.web.dto;

import com.appointment_scheduler.domain.ServiceType;

public record ServiceTypeResponse(Long id, String name, Integer durationMinutes) {

    public static ServiceTypeResponse from(ServiceType serviceType) {
        return new ServiceTypeResponse(serviceType.getId(), serviceType.getName(), serviceType.getDurationMinutes());
    }
}
