package com.appointment_scheduler.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.appointment_scheduler.domain.Appointment;
import com.appointment_scheduler.domain.AppointmentStatus;
import com.appointment_scheduler.domain.CustomerRef;
import com.appointment_scheduler.domain.Dealership;
import com.appointment_scheduler.domain.ServiceBay;
import com.appointment_scheduler.domain.ServiceType;
import com.appointment_scheduler.domain.Technician;
import com.appointment_scheduler.domain.VehicleCategory;
import com.appointment_scheduler.domain.VehicleRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the mappings most likely to be wrong: the {@code smallint[]}
 * array column, the native Postgres enum type, and the DB-generated
 * hold_token/created_at columns. These are checked against a real
 * Postgres container (not H2) since the schema is Postgres-specific
 * (arrays, native enums, EXCLUDE constraints).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class EntityMappingIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @PersistenceContext private EntityManager entityManager;

    @Autowired private DealershipRepository dealershipRepository;
    @Autowired private VehicleRefRepository vehicleRefRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceBayRepository serviceBayRepository;
    @Autowired private TechnicianRepository technicianRepository;
    @Autowired private CustomerRefRepository customerRefRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Test
    void dealershipDaysOpenArrayRoundTrips() {
        Dealership dealership =
                Dealership.builder()
                        .name("Keyloop Downtown")
                        .opensAt(LocalTime.of(8, 0))
                        .closesAt(LocalTime.of(18, 0))
                        .daysOpen(new Short[] {1, 2, 3, 4, 5})
                        .build();
        Long id = dealershipRepository.saveAndFlush(dealership).getId();
        entityManager.clear();

        Dealership reloaded = dealershipRepository.findById(id).orElseThrow();
        assertThat(reloaded.getDaysOpen())
                .containsExactly((short) 1, (short) 2, (short) 3, (short) 4, (short) 5);
    }

    @Test
    void vehicleCategoryNativeEnumRoundTrips() {
        VehicleRef vehicle =
                VehicleRef.builder()
                        .vin("1HGCM82633A004352")
                        .make("Honda")
                        .model("Accord")
                        .vehicleCategory(VehicleCategory.SEDAN)
                        .build();
        Long id = vehicleRefRepository.saveAndFlush(vehicle).getId();
        entityManager.clear();

        VehicleRef reloaded = vehicleRefRepository.findById(id).orElseThrow();
        assertThat(reloaded.getVehicleCategory()).isEqualTo(VehicleCategory.SEDAN);
    }

    @Test
    void vehicleCategoryIsNullableForUnsyncedVehicles() {
        VehicleRef vehicle = VehicleRef.builder().vin("2T1BURHE0JC014906").build();
        Long id = vehicleRefRepository.saveAndFlush(vehicle).getId();
        entityManager.clear();

        VehicleRef reloaded = vehicleRefRepository.findById(id).orElseThrow();
        assertThat(reloaded.getVehicleCategory()).isNull();
    }

    @Test
    void appointmentGraphAndDbGeneratedColumnsRoundTrip() {
        Dealership dealership =
                dealershipRepository.saveAndFlush(
                        Dealership.builder()
                                .name("Keyloop Uptown")
                                .opensAt(LocalTime.of(8, 0))
                                .closesAt(LocalTime.of(18, 0))
                                .daysOpen(new Short[] {1, 2, 3, 4, 5})
                                .build());
        ServiceBay bay =
                serviceBayRepository.saveAndFlush(
                        ServiceBay.builder().dealership(dealership).label("Bay 1").active(true).build());
        Technician technician =
                technicianRepository.saveAndFlush(
                        Technician.builder()
                                .dealership(dealership)
                                .displayName("Alex")
                                .active(true)
                                .build());
        ServiceType serviceType =
                serviceTypeRepository.saveAndFlush(
                        ServiceType.builder().name("Oil Change").durationMinutes(30).build());
        VehicleRef vehicle =
                vehicleRefRepository.saveAndFlush(
                        VehicleRef.builder()
                                .vin("JH4KA7660PC008269")
                                .vehicleCategory(VehicleCategory.SUV)
                                .build());
        CustomerRef customer =
                customerRefRepository.saveAndFlush(
                        CustomerRef.builder().name("Jamie Rivera").phone("555-0100").build());

        OffsetDateTime start = OffsetDateTime.now().withNano(0);
        Appointment appointment =
                Appointment.builder()
                        .dealership(dealership)
                        .bay(bay)
                        .technician(technician)
                        .vehicle(vehicle)
                        .customer(customer)
                        .serviceType(serviceType)
                        .startTime(start)
                        .endTime(start.plusMinutes(30))
                        .status(AppointmentStatus.HELD)
                        .expiresAt(start.plusMinutes(5))
                        .build();
        Long id = appointmentRepository.saveAndFlush(appointment).getId();
        entityManager.clear();

        Appointment reloaded = appointmentRepository.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.HELD);
        assertThat(reloaded.getHoldToken()).isNotNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }
}
