package com.appointment_scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.appointment_scheduler.domain.Appointment;
import com.appointment_scheduler.domain.AppointmentStatus;
import com.appointment_scheduler.domain.CustomerRef;
import com.appointment_scheduler.domain.Dealership;
import com.appointment_scheduler.domain.ServiceBay;
import com.appointment_scheduler.domain.ServiceType;
import com.appointment_scheduler.domain.Technician;
import com.appointment_scheduler.domain.TechnicianServiceType;
import com.appointment_scheduler.domain.TechnicianServiceTypeId;
import com.appointment_scheduler.domain.VehicleRef;
import com.appointment_scheduler.repository.AppointmentRepository;
import com.appointment_scheduler.repository.CustomerRefRepository;
import com.appointment_scheduler.repository.DealershipRepository;
import com.appointment_scheduler.repository.ServiceBayRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.repository.TechnicianRepository;
import com.appointment_scheduler.repository.TechnicianServiceTypeRepository;
import com.appointment_scheduler.repository.VehicleRefRepository;
import com.appointment_scheduler.service.dto.HoldCommand;
import com.appointment_scheduler.service.exception.HoldExpiredException;
import com.appointment_scheduler.service.exception.HoldTokenMismatchException;
import com.appointment_scheduler.service.exception.SlotNoLongerAvailableException;
import com.appointment_scheduler.service.exception.TechnicianNotQualifiedException;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Not class-level @Transactional: BookingService.hold() commits each
 * candidate-bay attempt in its own REQUIRES_NEW transaction, which would
 * not see this test's seed data if it stayed uncommitted inside an outer
 * test transaction. Each test seeds its own dealership/bay/technician
 * instead, so tests never share rows and don't need explicit cleanup.
 */
@SpringBootTest
@Testcontainers
class BookingServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private BookingService bookingService;
    @Autowired private DealershipRepository dealershipRepository;
    @Autowired private ServiceBayRepository serviceBayRepository;
    @Autowired private TechnicianRepository technicianRepository;
    @Autowired private TechnicianServiceTypeRepository technicianServiceTypeRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private VehicleRefRepository vehicleRefRepository;
    @Autowired private CustomerRefRepository customerRefRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    private Dealership dealership;
    private ServiceBay bay;
    private Technician technician;
    private ServiceType serviceType;
    private VehicleRef vehicle;
    private CustomerRef customer;

    @BeforeEach
    void seedBaseline() {
        dealership =
                dealershipRepository.saveAndFlush(
                        Dealership.builder()
                                .name("Keyloop Test Dealership " + UUID.randomUUID())
                                .opensAt(LocalTime.of(8, 0))
                                .closesAt(LocalTime.of(18, 0))
                                .daysOpen(new Short[] {1, 2, 3, 4, 5, 6, 7})
                                .build());
        bay =
                serviceBayRepository.saveAndFlush(
                        ServiceBay.builder().dealership(dealership).label("Bay 1").active(true).build());
        technician =
                technicianRepository.saveAndFlush(
                        Technician.builder()
                                .dealership(dealership)
                                .displayName("Alex")
                                .active(true)
                                .build());
        serviceType =
                serviceTypeRepository.saveAndFlush(
                        ServiceType.builder().name("Oil Change").durationMinutes(30).build());
        technicianServiceTypeRepository.saveAndFlush(
                TechnicianServiceType.builder()
                        .id(new TechnicianServiceTypeId(technician.getId(), serviceType.getId()))
                        .technician(technician)
                        .serviceType(serviceType)
                        .build());
        vehicle =
                vehicleRefRepository.saveAndFlush(
                        VehicleRef.builder().vin("VIN" + UUID.randomUUID()).build());
        customer = customerRefRepository.saveAndFlush(CustomerRef.builder().name("Jamie Rivera").build());
    }

    private OffsetDateTime tomorrowAt(int hour, int minute) {
        // Explicit UTC, not the JVM default zone: BusinessHours normalizes to
        // UTC before comparing wall-clock times, so tests must too.
        return OffsetDateTime.now(java.time.ZoneOffset.UTC)
                .plusDays(1)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);
    }

    private HoldCommand holdCommand(OffsetDateTime start) {
        return new HoldCommand(
                dealership.getId(), vehicle.getId(), customer.getId(), serviceType.getId(), start, technician.getId());
    }

    @Test
    void holdSucceedsWhenBayAndTechnicianAreFree() {
        Appointment appointment = bookingService.hold(holdCommand(tomorrowAt(10, 0)));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.HELD);
        assertThat(appointment.getHoldToken()).isNotNull();
        assertThat(appointment.getExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(appointment.getBay().getId()).isEqualTo(bay.getId());
        assertThat(appointment.getTechnician().getId()).isEqualTo(technician.getId());
    }

    @Test
    void confirmTransitionsHeldToConfirmed() {
        Appointment held = bookingService.hold(holdCommand(tomorrowAt(10, 0)));

        Appointment confirmed = bookingService.confirm(held.getId(), held.getHoldToken());

        assertThat(confirmed.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(confirmed.getExpiresAt()).isNull();
    }

    @Test
    void confirmIsIdempotentWithSameToken() {
        Appointment held = bookingService.hold(holdCommand(tomorrowAt(10, 0)));

        Appointment first = bookingService.confirm(held.getId(), held.getHoldToken());
        Appointment second = bookingService.confirm(held.getId(), held.getHoldToken());

        assertThat(first.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void confirmFailsWithWrongToken() {
        Appointment held = bookingService.hold(holdCommand(tomorrowAt(10, 0)));

        assertThatThrownBy(() -> bookingService.confirm(held.getId(), UUID.randomUUID()))
                .isInstanceOf(HoldTokenMismatchException.class);
    }

    @Test
    void confirmFailsAndExpiresWhenHoldTtlHasPassed() {
        Appointment held = bookingService.hold(holdCommand(tomorrowAt(10, 0)));
        forceExpiry(held.getId());

        assertThatThrownBy(() -> bookingService.confirm(held.getId(), held.getHoldToken()))
                .isInstanceOf(HoldExpiredException.class);
        assertThat(appointmentRepository.findById(held.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.EXPIRED);
    }

    private void forceExpiry(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        appointment.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        appointmentRepository.saveAndFlush(appointment);
    }

    @Test
    void holdFailsWhenTechnicianNotQualifiedForServiceType() {
        ServiceType otherServiceType =
                serviceTypeRepository.saveAndFlush(
                        ServiceType.builder().name("Transmission Repair").durationMinutes(120).build());

        assertThatThrownBy(
                        () ->
                                bookingService.hold(
                                        new HoldCommand(
                                                dealership.getId(),
                                                vehicle.getId(),
                                                customer.getId(),
                                                otherServiceType.getId(),
                                                tomorrowAt(10, 0),
                                                technician.getId())))
                .isInstanceOf(TechnicianNotQualifiedException.class);
    }

    @Test
    void holdFailsWhenTechnicianSlotAlreadyTaken() {
        OffsetDateTime start = tomorrowAt(10, 0);
        bookingService.hold(holdCommand(start)); // occupies the only technician + bay

        assertThatThrownBy(() -> bookingService.hold(holdCommand(start)))
                .isInstanceOf(SlotNoLongerAvailableException.class);
    }

    @Test
    void holdRetriesNextCandidateBayWhenFirstIsTaken() {
        ServiceBay secondBay =
                serviceBayRepository.saveAndFlush(
                        ServiceBay.builder().dealership(dealership).label("Bay 2").active(true).build());
        Technician secondTechnician =
                technicianRepository.saveAndFlush(
                        Technician.builder()
                                .dealership(dealership)
                                .displayName("Sam")
                                .active(true)
                                .build());
        technicianServiceTypeRepository.saveAndFlush(
                TechnicianServiceType.builder()
                        .id(new TechnicianServiceTypeId(secondTechnician.getId(), serviceType.getId()))
                        .technician(secondTechnician)
                        .serviceType(serviceType)
                        .build());
        OffsetDateTime start = tomorrowAt(10, 0);
        // Occupies `bay` (the lower id, so the first candidate) via a different technician,
        // so the second hold below only conflicts on the bay, not the technician.
        bookingService.hold(
                new HoldCommand(
                        dealership.getId(),
                        vehicle.getId(),
                        customer.getId(),
                        serviceType.getId(),
                        start,
                        secondTechnician.getId()));

        Appointment appointment = bookingService.hold(holdCommand(start));

        assertThat(appointment.getBay().getId()).isEqualTo(secondBay.getId());
    }
}
