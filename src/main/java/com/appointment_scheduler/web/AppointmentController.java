package com.appointment_scheduler.web;

import com.appointment_scheduler.domain.Appointment;
import com.appointment_scheduler.domain.AppointmentStatus;
import com.appointment_scheduler.service.AppointmentQueryService;
import com.appointment_scheduler.service.BookingService;
import com.appointment_scheduler.web.dto.AppointmentResponse;
import com.appointment_scheduler.web.dto.ConfirmRequest;
import com.appointment_scheduler.web.dto.HoldRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Hold/Confirm/Get/Cancel/List — see .claude/docs/booking-flow.md. */
@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final BookingService bookingService;
    private final AppointmentQueryService appointmentQueryService;

    @PostMapping("/hold")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse hold(@Valid @RequestBody HoldRequest request) {
        Appointment appointment = bookingService.hold(request.toCommand());
        return AppointmentResponse.from(appointment);
    }

    @PostMapping("/{holdId}/confirm")
    public AppointmentResponse confirm(@PathVariable Long holdId, @Valid @RequestBody ConfirmRequest request) {
        Appointment appointment = bookingService.confirm(holdId, request.holdToken());
        return AppointmentResponse.from(appointment);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable Long id) {
        return AppointmentResponse.from(appointmentQueryService.get(id));
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id) {
        return AppointmentResponse.from(bookingService.cancel(id));
    }

    @GetMapping
    public List<AppointmentResponse> list(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Long dealershipId,
            @RequestParam(required = false) String vin) {
        return appointmentQueryService.list(status, dealershipId, vin).stream()
                .map(AppointmentResponse::from)
                .toList();
    }
}
