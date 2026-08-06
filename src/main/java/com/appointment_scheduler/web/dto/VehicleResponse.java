package com.appointment_scheduler.web.dto;

import com.appointment_scheduler.domain.VehicleCategory;
import com.appointment_scheduler.domain.VehicleRef;

public record VehicleResponse(Long id, String vin, String make, String model, VehicleCategory vehicleCategory) {

    public static VehicleResponse from(VehicleRef vehicle) {
        return new VehicleResponse(
                vehicle.getId(), vehicle.getVin(), vehicle.getMake(), vehicle.getModel(), vehicle.getVehicleCategory());
    }
}
