package com.appointment_scheduler.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.appointment_scheduler.repository.CustomerRefRepository;
import com.appointment_scheduler.repository.DealershipRepository;
import com.appointment_scheduler.repository.ServiceBayRepository;
import com.appointment_scheduler.repository.ServiceTypeDurationOverrideRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.repository.TechnicianRepository;
import com.appointment_scheduler.repository.TechnicianServiceTypeRepository;
import com.appointment_scheduler.repository.VehicleRefRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Activating the "dev" profile runs DevDataSeeder automatically at context
 * startup (Spring Boot invokes CommandLineRunner beans after refresh) — this
 * test checks the result and then calls it again directly to prove the
 * empty-check makes re-running safe (e.g. restarting bootRun locally).
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class DevDataSeederIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private DevDataSeeder seeder;
    @Autowired private DealershipRepository dealershipRepository;
    @Autowired private ServiceBayRepository serviceBayRepository;
    @Autowired private TechnicianRepository technicianRepository;
    @Autowired private TechnicianServiceTypeRepository technicianServiceTypeRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private ServiceTypeDurationOverrideRepository serviceTypeDurationOverrideRepository;
    @Autowired private VehicleRefRepository vehicleRefRepository;
    @Autowired private CustomerRefRepository customerRefRepository;

    @Test
    void seedsExpectedDataAndIsIdempotent() {
        assertThat(dealershipRepository.count()).isEqualTo(1);
        assertThat(serviceBayRepository.count()).isEqualTo(3);
        assertThat(technicianRepository.count()).isEqualTo(3);
        assertThat(technicianServiceTypeRepository.count()).isEqualTo(7);
        assertThat(serviceTypeRepository.count()).isEqualTo(4);
        assertThat(serviceTypeDurationOverrideRepository.count()).isEqualTo(2);
        assertThat(vehicleRefRepository.count()).isEqualTo(3);
        assertThat(customerRefRepository.count()).isEqualTo(2);

        seeder.run(); // re-running (e.g. restarting bootRun) must not duplicate rows

        assertThat(dealershipRepository.count()).isEqualTo(1);
        assertThat(serviceBayRepository.count()).isEqualTo(3);
        assertThat(vehicleRefRepository.count()).isEqualTo(3);
    }
}
