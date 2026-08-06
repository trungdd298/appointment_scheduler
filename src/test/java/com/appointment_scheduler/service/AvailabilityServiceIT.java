package com.appointment_scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.appointment_scheduler.domain.Appointment;
import com.appointment_scheduler.domain.AppointmentStatus;
import com.appointment_scheduler.domain.CustomerRef;
import com.appointment_scheduler.domain.Dealership;
import com.appointment_scheduler.domain.ResourceType;
import com.appointment_scheduler.domain.ResourceUnavailability;
import com.appointment_scheduler.domain.ServiceBay;
import com.appointment_scheduler.domain.ServiceType;
import com.appointment_scheduler.domain.Technician;
import com.appointment_scheduler.domain.TechnicianServiceType;
import com.appointment_scheduler.domain.TechnicianServiceTypeId;
import com.appointment_scheduler.domain.UnavailabilitySource;
import com.appointment_scheduler.domain.VehicleRef;
import com.appointment_scheduler.repository.AppointmentRepository;
import com.appointment_scheduler.repository.CustomerRefRepository;
import com.appointment_scheduler.repository.DealershipRepository;
import com.appointment_scheduler.repository.ResourceUnavailabilityRepository;
import com.appointment_scheduler.repository.ServiceBayRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.repository.TechnicianRepository;
import com.appointment_scheduler.repository.TechnicianServiceTypeRepository;
import com.appointment_scheduler.repository.VehicleRefRepository;
import com.appointment_scheduler.service.dto.AvailabilitySlot;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Full-stack test of GET /availability's logic against real Postgres —
 * exercises the native tstzrange overlap queries in AppointmentRepository,
 * not just AvailabilityService's Java logic.
 */
@SpringBootTest
@Testcontainers
@Transactional
class AvailabilityServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private AvailabilityService availabilityService;
    @Autowired private DealershipRepository dealershipRepository;
    @Autowired private ServiceBayRepository serviceBayRepository;
    @Autowired private TechnicianRepository technicianRepository;
    @Autowired private TechnicianServiceTypeRepository technicianServiceTypeRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private VehicleRefRepository vehicleRefRepository;
    @Autowired private CustomerRefRepository customerRefRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ResourceUnavailabilityRepository resourceUnavailabilityRepository;

    private Dealership dealership;
    private ServiceBay bay;
    private Technician technician;
    private ServiceType serviceType;
    private VehicleRef vehicle;

    @BeforeEach
    void seedBaseline() {
        // Open every day of the week so test assertions don't depend on
        // which weekday "tomorrow" happens to land on.
        dealership =
                dealershipRepository.saveAndFlush(
                        Dealership.builder()
                                .name("Keyloop Test Dealership")
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
        vehicle = vehicleRefRepository.saveAndFlush(VehicleRef.builder().vin("TESTVIN0000000001").build());
    }

    private OffsetDateTime tomorrowAt(int hour, int minute) {
        return OffsetDateTime.now()
                .plusDays(1)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);
    }

    @Test
    void returnsSlotWhenBayAndQualifiedTechnicianAreFree() {
        OffsetDateTime near = tomorrowAt(10, 0);

        List<AvailabilitySlot> slots =
                availabilityService.findAvailability(
                        dealership.getId(), vehicle.getId(), serviceType.getId(), near, 1);

        assertThat(slots).contains(new AvailabilitySlot(near, technician.getId(), technician.getDisplayName()));
    }

    @Test
    void excludesCandidatesThatWouldRunPastClosingTime() {
        // Duration is 30 min, closesAt 18:00: 17:30 fits exactly, 17:45 would end at 18:15.
        OffsetDateTime near = tomorrowAt(17, 45);

        List<AvailabilitySlot> slots =
                availabilityService.findAvailability(
                        dealership.getId(), vehicle.getId(), serviceType.getId(), near, 1);

        assertThat(slots).noneMatch(slot -> slot.startTime().equals(tomorrowAt(17, 45)));
        assertThat(slots).anyMatch(slot -> slot.startTime().equals(tomorrowAt(17, 30)));
    }

    @Test
    void excludesSlotsWhereTheOnlyBayIsAlreadyBooked() {
        OffsetDateTime near = tomorrowAt(10, 0);
        CustomerRef customer =
                customerRefRepository.saveAndFlush(CustomerRef.builder().name("Jamie Rivera").build());
        appointmentRepository.saveAndFlush(
                Appointment.builder()
                        .dealership(dealership)
                        .bay(bay)
                        .technician(technician)
                        .vehicle(vehicle)
                        .customer(customer)
                        .serviceType(serviceType)
                        .startTime(near)
                        .endTime(near.plusMinutes(30))
                        .status(AppointmentStatus.CONFIRMED)
                        .build());

        List<AvailabilitySlot> slots =
                availabilityService.findAvailability(
                        dealership.getId(), vehicle.getId(), serviceType.getId(), near, 1);

        assertThat(slots).noneMatch(slot -> slot.startTime().equals(near));
        // Adjacent half-open-interval slots are unaffected (no overlap with [10:00, 10:30)).
        assertThat(slots).anyMatch(slot -> slot.startTime().equals(tomorrowAt(9, 30)));
        assertThat(slots).anyMatch(slot -> slot.startTime().equals(tomorrowAt(10, 30)));
    }

    @Test
    void excludesTechnicianDuringReportedUnavailability() {
        OffsetDateTime near = tomorrowAt(10, 0);
        resourceUnavailabilityRepository.saveAndFlush(
                ResourceUnavailability.builder()
                        .resourceType(ResourceType.TECHNICIAN)
                        .resourceId(technician.getId())
                        .startTime(near.minusMinutes(15))
                        .endTime(near.plusMinutes(45))
                        .source(UnavailabilitySource.TECHNICIAN_SELF)
                        .build());

        List<AvailabilitySlot> slots =
                availabilityService.findAvailability(
                        dealership.getId(), vehicle.getId(), serviceType.getId(), near, 1);

        assertThat(slots).noneMatch(slot -> slot.startTime().equals(near));
    }

    @Test
    void neverReturnsATechnicianNotQualifiedForTheServiceType() {
        Technician unqualified =
                technicianRepository.saveAndFlush(
                        Technician.builder()
                                .dealership(dealership)
                                .displayName("Unqualified Sam")
                                .active(true)
                                .build());
        OffsetDateTime near = tomorrowAt(10, 0);

        List<AvailabilitySlot> slots =
                availabilityService.findAvailability(
                        dealership.getId(), vehicle.getId(), serviceType.getId(), near, 1);

        assertThat(slots).noneMatch(slot -> slot.technicianId().equals(unqualified.getId()));
    }
}
