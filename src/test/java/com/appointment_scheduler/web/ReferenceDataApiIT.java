package com.appointment_scheduler.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.appointment_scheduler.domain.CustomerRef;
import com.appointment_scheduler.domain.Dealership;
import com.appointment_scheduler.domain.ServiceType;
import com.appointment_scheduler.domain.VehicleCategory;
import com.appointment_scheduler.domain.VehicleRef;
import com.appointment_scheduler.repository.CustomerRefRepository;
import com.appointment_scheduler.repository.DealershipRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.repository.VehicleRefRepository;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Purely read-only endpoints, no REQUIRES_NEW anywhere in the call path —
 * unlike AppointmentApiIT, class-level @Transactional (auto-rollback) is
 * safe here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ReferenceDataApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private MockMvc mockMvc;
    @Autowired private DealershipRepository dealershipRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private VehicleRefRepository vehicleRefRepository;
    @Autowired private CustomerRefRepository customerRefRepository;

    @BeforeEach
    void seed() {
        dealershipRepository.saveAndFlush(
                Dealership.builder()
                        .name("Metro Reference Test Dealership")
                        .opensAt(LocalTime.of(8, 0))
                        .closesAt(LocalTime.of(18, 0))
                        .daysOpen(new Short[] {1, 2, 3, 4, 5})
                        .build());
        serviceTypeRepository.saveAndFlush(ServiceType.builder().name("Oil Change").durationMinutes(30).build());
        vehicleRefRepository.saveAndFlush(
                VehicleRef.builder()
                        .vin("VIN-REF-0001")
                        .make("Honda")
                        .model("Accord")
                        .vehicleCategory(VehicleCategory.SEDAN)
                        .build());
        customerRefRepository.saveAndFlush(CustomerRef.builder().name("Jamie Rivera").phone("555-0100").build());
    }

    @Test
    void listDealershipsReturnsSeededRow() throws Exception {
        mockMvc
                .perform(get("/dealerships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Metro Reference Test Dealership')]").exists());
    }

    @Test
    void listServiceTypesReturnsSeededRow() throws Exception {
        mockMvc
                .perform(get("/service-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Oil Change')].durationMinutes").value(30));
    }

    @Test
    void listVehiclesReturnsSeededRow() throws Exception {
        mockMvc
                .perform(get("/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.vin == 'VIN-REF-0001')].vehicleCategory").value("SEDAN"));
    }

    @Test
    void listCustomersReturnsSeededRow() throws Exception {
        mockMvc
                .perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Jamie Rivera')].phone").value("555-0100"));
    }
}
