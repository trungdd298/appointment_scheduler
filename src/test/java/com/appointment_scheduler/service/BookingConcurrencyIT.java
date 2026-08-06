package com.appointment_scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.appointment_scheduler.service.exception.SlotNoLongerAvailableException;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The core deliverable: proves double-booking is prevented by the database
 * itself, not by application-level locking — BookingService does no
 * pre-insert locking at all (see BookingService's class Javadoc and
 * .claude/docs/architecture.md#concurrency-correctness-defense-in-depth).
 * With only one bay in the dealership, every concurrent hold races for the
 * exact same (bay_id, during) row; the no_overlap_per_bay EXCLUDE
 * constraint is the only thing that can be arbitrating the outcome.
 */
@SpringBootTest
@Testcontainers
class BookingConcurrencyIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    private static final int CONCURRENT_REQUESTS = 20;

    @Autowired private BookingService bookingService;
    @Autowired private DealershipRepository dealershipRepository;
    @Autowired private ServiceBayRepository serviceBayRepository;
    @Autowired private TechnicianRepository technicianRepository;
    @Autowired private TechnicianServiceTypeRepository technicianServiceTypeRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private VehicleRefRepository vehicleRefRepository;
    @Autowired private CustomerRefRepository customerRefRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Test
    void exactlyOneConcurrentHoldWinsForTheSameSlot() throws Exception {
        Dealership dealership =
                dealershipRepository.saveAndFlush(
                        Dealership.builder()
                                .name("Keyloop Concurrency Test Dealership")
                                .opensAt(LocalTime.of(8, 0))
                                .closesAt(LocalTime.of(18, 0))
                                .daysOpen(new Short[] {1, 2, 3, 4, 5, 6, 7})
                                .build());
        serviceBayRepository.saveAndFlush(
                ServiceBay.builder().dealership(dealership).label("Only Bay").active(true).build());
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
        technicianServiceTypeRepository.saveAndFlush(
                TechnicianServiceType.builder()
                        .id(new TechnicianServiceTypeId(technician.getId(), serviceType.getId()))
                        .technician(technician)
                        .serviceType(serviceType)
                        .build());
        VehicleRef vehicle = vehicleRefRepository.saveAndFlush(VehicleRef.builder().vin("VIN-CONCURRENCY").build());
        CustomerRef customer = customerRefRepository.saveAndFlush(CustomerRef.builder().name("Jamie Rivera").build());

        // Explicit UTC, not the JVM default zone: BusinessHours normalizes to
        // UTC before comparing wall-clock times, so tests must too.
        OffsetDateTime start =
                OffsetDateTime.now(java.time.ZoneOffset.UTC)
                        .plusDays(1)
                        .withHour(10)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);
        HoldCommand command =
                new HoldCommand(dealership.getId(), vehicle.getId(), customer.getId(), serviceType.getId(), start, technician.getId());

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch allThreadsReady = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Boolean> attemptHold =
                () -> {
                    allThreadsReady.countDown();
                    go.await();
                    try {
                        bookingService.hold(command);
                        return true;
                    } catch (SlotNoLongerAvailableException e) {
                        return false;
                    }
                };

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            futures.add(executor.submit(attemptHold));
        }
        allThreadsReady.await(); // maximize actual concurrent overlap
        go.countDown();

        long successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }
        executor.shutdown();

        assertThat(successCount)
                .as("exactly one of %d concurrent hold requests for the same slot should win", CONCURRENT_REQUESTS)
                .isEqualTo(1);

        long occupyingRows =
                appointmentRepository.findAll().stream()
                        .filter(a -> a.getTechnician().getId().equals(technician.getId()))
                        .filter(
                                a ->
                                        a.getStatus() == AppointmentStatus.HELD
                                                || a.getStatus() == AppointmentStatus.CONFIRMED)
                        .count();
        assertThat(occupyingRows).isEqualTo(1);
    }
}
