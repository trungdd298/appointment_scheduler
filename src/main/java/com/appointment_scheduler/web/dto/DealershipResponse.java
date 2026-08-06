package com.appointment_scheduler.web.dto;

import com.appointment_scheduler.domain.Dealership;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

public record DealershipResponse(
        Long id, String name, LocalTime opensAt, LocalTime closesAt, List<Short> daysOpen) {

    public static DealershipResponse from(Dealership dealership) {
        return new DealershipResponse(
                dealership.getId(),
                dealership.getName(),
                dealership.getOpensAt(),
                dealership.getClosesAt(),
                Arrays.asList(dealership.getDaysOpen()));
    }
}
