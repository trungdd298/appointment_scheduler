package com.appointment_scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.appointment_scheduler.domain.Appointment;
import com.appointment_scheduler.domain.AppointmentStatus;
import com.appointment_scheduler.domain.CustomerRef;
import com.appointment_scheduler.domain.Dealership;
import com.appointment_scheduler.domain.ResourceType;
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
import com.appointment_scheduler.service.dto.RecordUnavailabilityCommand;
import com.appointment_scheduler.service.exception.TechnicianNotQualifiedException;
import com.appointment_scheduler.service.exception.ValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Class-level @Transactional is safe here (unlike BookingServiceIT):
 * ResourceUnavailabilityService's methods don't use REQUIRES_NEW, so
 * everything in a test stays in one transaction and rolls back cleanly.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ResourceUnavailabilityServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @PersistenceContext private EntityManager entityManager;

    @Autowired private ResourceUnavailabilityService resourceUnavailabilityService;
    @Autowired private ResourceUnavailabilityRepository resourceUnavailabilityRepository;
    @Autowired private EscalationSweeper escalationSweeper;
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
                                .name("Keyloop Test Dealership")
                                .opensAt(LocalTime.of(8, 0))
                                .closesAt(LocalTime.of(18, 0))
                                .daysOpen(new Short[] {1, 2, 3, 4, 5, 6, 7})
                                .build());
        bay = serviceBayRepository.saveAndFlush(ServiceBay.builder().dealership(dealership).label("Bay 1").active(true).build());
        technician =
                technicianRepository.saveAndFlush(
                        Technician.builder().dealership(dealership).displayName("Alex").active(true).build());
        serviceType =
                serviceTypeRepository.saveAndFlush(
                        ServiceType.builder().name("Oil Change").durationMinutes(30).build());
        technicianServiceTypeRepository.saveAndFlush(
                TechnicianServiceType.builder()
                        .id(new TechnicianServiceTypeId(technician.getId(), serviceType.getId()))
                        .technician(technician)
                        .serviceType(serviceType)
                        .build());
        vehicle = vehicleRefRepository.saveAndFlush(VehicleRef.builder().vin("VIN-RECON-" + UUID.randomUUID()).build());
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

    private Appointment confirmedAppointment(ServiceBay bay, Technician technician, OffsetDateTime start) {
        return appointmentRepository.saveAndFlush(
                Appointment.builder()
                        .dealership(dealership)
                        .bay(bay)
                        .technician(technician)
                        .vehicle(vehicle)
                        .customer(customer)
                        .serviceType(serviceType)
                        .startTime(start)
                        .endTime(start.plusMinutes(30))
                        .status(AppointmentStatus.CONFIRMED)
                        .build());
    }

    @Test
    void bayUnavailabilityReassignsToReplacementBaySilently() {
        ServiceBay secondBay =
                serviceBayRepository.saveAndFlush(
                        ServiceBay.builder().dealership(dealership).label("Bay 2").active(true).build());
        OffsetDateTime start = tomorrowAt(10, 0);
        Appointment appointment = confirmedAppointment(bay, technician, start);

        resourceUnavailabilityService.recordUnavailability(
                new RecordUnavailabilityCommand(
                        ResourceType.BAY,
                        bay.getId(),
                        start,
                        start.plusHours(2),
                        "Lift broken",
                        UnavailabilitySource.FACILITY_SYSTEM,
                        "evt-" + UUID.randomUUID()));

        Appointment reloaded = appointmentRepository.findByIdWithAssociations(appointment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(reloaded.getBay().getId()).isEqualTo(secondBay.getId());
    }

    @Test
    void bayUnavailabilityWithNoReplacementMarksNeedsAttention() {
        OffsetDateTime start = tomorrowAt(10, 0);
        Appointment appointment = confirmedAppointment(bay, technician, start); // only bay in the dealership

        resourceUnavailabilityService.recordUnavailability(
                new RecordUnavailabilityCommand(
                        ResourceType.BAY,
                        bay.getId(),
                        start,
                        start.plusHours(2),
                        "Lift broken",
                        UnavailabilitySource.FACILITY_SYSTEM,
                        "evt-" + UUID.randomUUID()));

        Appointment reloaded = appointmentRepository.findByIdWithAssociations(appointment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.NEEDS_ATTENTION);
        assertThat(reloaded.getNeedsAttentionReason()).contains("No replacement bay");
        assertThat(reloaded.getEscalationDeadline()).isEqualTo(start);
    }

    @Test
    void technicianUnavailabilityMarksNeedsAttentionNeverAutoReassigned() {
        OffsetDateTime start = tomorrowAt(10, 0);
        Appointment appointment = confirmedAppointment(bay, technician, start);

        resourceUnavailabilityService.recordUnavailability(
                new RecordUnavailabilityCommand(
                        ResourceType.TECHNICIAN,
                        technician.getId(),
                        start,
                        start.plusHours(2),
                        "Called in sick",
                        UnavailabilitySource.TECHNICIAN_SELF,
                        "evt-" + UUID.randomUUID()));

        Appointment reloaded = appointmentRepository.findByIdWithAssociations(appointment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.NEEDS_ATTENTION);
        assertThat(reloaded.getTechnician().getId()).isEqualTo(technician.getId()); // never silently swapped
        assertThat(reloaded.getNeedsAttentionReason()).contains("Technician");
        assertThat(reloaded.getEscalationDeadline()).isEqualTo(start);
    }

    @Test
    void recordUnavailabilityIsIdempotentViaExternalEventId() {
        OffsetDateTime start = tomorrowAt(10, 0);
        confirmedAppointment(bay, technician, start);
        String externalEventId = "evt-" + UUID.randomUUID();
        RecordUnavailabilityCommand command =
                new RecordUnavailabilityCommand(
                        ResourceType.TECHNICIAN,
                        technician.getId(),
                        start,
                        start.plusHours(2),
                        "Called in sick",
                        UnavailabilitySource.TECHNICIAN_SELF,
                        externalEventId);

        var first = resourceUnavailabilityService.recordUnavailability(command);
        var second = resourceUnavailabilityService.recordUnavailability(command);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(resourceUnavailabilityRepository.findAll()).hasSize(1);
    }

    @Test
    void reassignTechnicianTransitionsBackToConfirmed() {
        OffsetDateTime start = tomorrowAt(10, 0);
        Appointment appointment = confirmedAppointment(bay, technician, start);
        resourceUnavailabilityService.recordUnavailability(
                new RecordUnavailabilityCommand(
                        ResourceType.TECHNICIAN,
                        technician.getId(),
                        start,
                        start.plusHours(2),
                        "Called in sick",
                        UnavailabilitySource.TECHNICIAN_SELF,
                        "evt-" + UUID.randomUUID()));
        Technician replacement =
                technicianRepository.saveAndFlush(
                        Technician.builder().dealership(dealership).displayName("Sam").active(true).build());
        technicianServiceTypeRepository.saveAndFlush(
                TechnicianServiceType.builder()
                        .id(new TechnicianServiceTypeId(replacement.getId(), serviceType.getId()))
                        .technician(replacement)
                        .serviceType(serviceType)
                        .build());

        Appointment reassigned = resourceUnavailabilityService.reassignTechnician(appointment.getId(), replacement.getId());

        assertThat(reassigned.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(reassigned.getTechnician().getId()).isEqualTo(replacement.getId());
        assertThat(reassigned.getNeedsAttentionReason()).isNull();
        assertThat(reassigned.getEscalationDeadline()).isNull();
    }

    @Test
    void reassignTechnicianFailsWhenReplacementNotQualified() {
        OffsetDateTime start = tomorrowAt(10, 0);
        Appointment appointment = confirmedAppointment(bay, technician, start);
        resourceUnavailabilityService.recordUnavailability(
                new RecordUnavailabilityCommand(
                        ResourceType.TECHNICIAN,
                        technician.getId(),
                        start,
                        start.plusHours(2),
                        "Called in sick",
                        UnavailabilitySource.TECHNICIAN_SELF,
                        "evt-" + UUID.randomUUID()));
        Technician unqualified =
                technicianRepository.saveAndFlush(
                        Technician.builder().dealership(dealership).displayName("Sam").active(true).build());

        assertThatThrownBy(
                        () -> resourceUnavailabilityService.reassignTechnician(appointment.getId(), unqualified.getId()))
                .isInstanceOf(TechnicianNotQualifiedException.class);
    }

    @Test
    void reassignTechnicianFailsWhenAppointmentIsNotAwaitingReassignment() {
        OffsetDateTime start = tomorrowAt(10, 0);
        Appointment appointment = confirmedAppointment(bay, technician, start); // still CONFIRMED, never escalated

        assertThatThrownBy(() -> resourceUnavailabilityService.reassignTechnician(appointment.getId(), technician.getId()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void escalationSweepCancelsUnresolvedAppointmentsPastDeadline() {
        OffsetDateTime start = tomorrowAt(10, 0);
        Appointment appointment = confirmedAppointment(bay, technician, start);
        appointment.setStatus(AppointmentStatus.NEEDS_ATTENTION);
        appointment.setEscalationDeadline(OffsetDateTime.now().minusMinutes(1));
        appointmentRepository.saveAndFlush(appointment);

        escalationSweeper.cancelUnresolvedEscalations();
        // The bulk update above bypasses the persistence context, so the
        // already-loaded `appointment` instance would otherwise be read
        // back stale from the first-level cache within this same session.
        entityManager.clear();

        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
    }
}
