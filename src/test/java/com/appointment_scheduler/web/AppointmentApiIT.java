package com.appointment_scheduler.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.appointment_scheduler.repository.ServiceBayRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.repository.TechnicianRepository;
import com.appointment_scheduler.repository.TechnicianServiceTypeRepository;
import com.appointment_scheduler.repository.VehicleRefRepository;
import com.appointment_scheduler.service.ResourceUnavailabilityService;
import com.appointment_scheduler.service.dto.RecordUnavailabilityCommand;
import com.appointment_scheduler.web.dto.ConfirmRequest;
import com.appointment_scheduler.web.dto.HoldRequest;
import com.appointment_scheduler.web.dto.ReassignTechnicianRequest;
import com.appointment_scheduler.web.dto.ResourceUnavailabilityRequest;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Full HTTP-layer verification. Not class-level @Transactional — see
 * BookingServiceIT: BookingService.hold() commits candidate attempts in
 * their own REQUIRES_NEW transactions, which wouldn't see this test's seed
 * data if it stayed uncommitted in an outer test transaction. Each test
 * seeds its own dealership, so nothing needs cleanup between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AppointmentApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DealershipRepository dealershipRepository;
    @Autowired private ServiceBayRepository serviceBayRepository;
    @Autowired private TechnicianRepository technicianRepository;
    @Autowired private TechnicianServiceTypeRepository technicianServiceTypeRepository;
    @Autowired private ServiceTypeRepository serviceTypeRepository;
    @Autowired private VehicleRefRepository vehicleRefRepository;
    @Autowired private CustomerRefRepository customerRefRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ResourceUnavailabilityService resourceUnavailabilityService;

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
                                .name("Metro API Test Dealership " + UUID.randomUUID())
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
        vehicle = vehicleRefRepository.saveAndFlush(VehicleRef.builder().vin("VIN-API-" + UUID.randomUUID()).build());
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

    @Test
    void availabilityReturnsFeasibleSlot() throws Exception {
        OffsetDateTime near = tomorrowAt(10, 0);

        mockMvc
                .perform(
                        get("/dealerships/{id}/availability", dealership.getId())
                                .param("vehicleId", vehicle.getId().toString())
                                .param("serviceTypeId", serviceType.getId().toString())
                                .param("near", near.toString())
                                .param("windowHours", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.technicianId == %d)]", technician.getId()).exists());
    }

    @Test
    void availabilityMissingRequiredParamReturns400ValidationError() throws Exception {
        mockMvc
                .perform(get("/dealerships/{id}/availability", dealership.getId()).param("vehicleId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void availabilityUnknownDealershipReturns404() throws Exception {
        mockMvc
                .perform(
                        get("/dealerships/{id}/availability", 999_999_999L)
                                .param("vehicleId", vehicle.getId().toString())
                                .param("serviceTypeId", serviceType.getId().toString())
                                .param("near", tomorrowAt(10, 0).toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void holdCreatesAppointmentWithHoldToken() throws Exception {
        HoldRequest request =
                new HoldRequest(
                        dealership.getId(), vehicle.getId(), customer.getId(), serviceType.getId(), tomorrowAt(10, 0), technician.getId());

        mockMvc
                .perform(
                        post("/appointments/hold")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.holdToken").exists())
                .andExpect(jsonPath("$.bayId").value(bay.getId()))
                .andExpect(jsonPath("$.technicianId").value(technician.getId()));
    }

    @Test
    void holdWithUnqualifiedTechnicianReturns422() throws Exception {
        ServiceType otherServiceType =
                serviceTypeRepository.saveAndFlush(
                        ServiceType.builder().name("Transmission Repair").durationMinutes(120).build());
        HoldRequest request =
                new HoldRequest(
                        dealership.getId(),
                        vehicle.getId(),
                        customer.getId(),
                        otherServiceType.getId(),
                        tomorrowAt(10, 0),
                        technician.getId());

        mockMvc
                .perform(
                        post("/appointments/hold")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("TECHNICIAN_NOT_QUALIFIED"));
    }

    @Test
    void holdMissingFieldReturns400ValidationError() throws Exception {
        String malformedJson =
                "{\"dealershipId\":" + dealership.getId() + ",\"vehicleId\":" + vehicle.getId() + "}";

        mockMvc
                .perform(post("/appointments/hold").contentType(MediaType.APPLICATION_JSON).content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    private Appointment holdViaApi(OffsetDateTime start) throws Exception {
        HoldRequest request =
                new HoldRequest(dealership.getId(), vehicle.getId(), customer.getId(), serviceType.getId(), start, technician.getId());
        String responseBody =
                mockMvc
                        .perform(
                                post("/appointments/hold")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Long id = objectMapper.readTree(responseBody).get("id").asLong();
        return appointmentRepository.findByIdWithAssociations(id).orElseThrow();
    }

    @Test
    void confirmTransitionsToConfirmedAndHidesHoldToken() throws Exception {
        Appointment held = holdViaApi(tomorrowAt(10, 0));

        mockMvc
                .perform(
                        post("/appointments/{id}/confirm", held.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ConfirmRequest(held.getHoldToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.holdToken").doesNotExist());
    }

    @Test
    void confirmWithWrongTokenReturns404HoldTokenMismatch() throws Exception {
        Appointment held = holdViaApi(tomorrowAt(10, 0));

        mockMvc
                .perform(
                        post("/appointments/{id}/confirm", held.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ConfirmRequest(UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("HOLD_TOKEN_MISMATCH"));
    }

    @Test
    void getReturnsAppointmentAndUnknownIdReturns404() throws Exception {
        Appointment held = holdViaApi(tomorrowAt(10, 0));

        mockMvc
                .perform(get("/appointments/{id}", held.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vin").value(vehicle.getVin()))
                .andExpect(jsonPath("$.serviceTypeName").value("Oil Change"));

        mockMvc
                .perform(get("/appointments/{id}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void cancelTransitionsToCancelled() throws Exception {
        Appointment held = holdViaApi(tomorrowAt(10, 0));

        mockMvc
                .perform(post("/appointments/{id}/cancel", held.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void listFiltersByDealershipAndVin() throws Exception {
        holdViaApi(tomorrowAt(10, 0));

        mockMvc
                .perform(get("/appointments").param("dealershipId", dealership.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dealershipId").value(dealership.getId()));

        mockMvc
                .perform(get("/appointments").param("vin", vehicle.getVin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vin").value(vehicle.getVin()));
    }

    @Test
    void reassignTechnicianResolvesNeedsAttention() throws Exception {
        OffsetDateTime start = tomorrowAt(10, 0);
        Appointment appointment =
                appointmentRepository.saveAndFlush(
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

        mockMvc
                .perform(
                        post("/appointments/{id}/reassign-technician", appointment.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ReassignTechnicianRequest(replacement.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.technicianId").value(replacement.getId()));
    }

    @Test
    void resourceUnavailabilityWebhookIsIdempotent() throws Exception {
        String externalEventId = "evt-" + UUID.randomUUID();
        ResourceUnavailabilityRequest request =
                new ResourceUnavailabilityRequest(
                        ResourceType.TECHNICIAN,
                        technician.getId(),
                        tomorrowAt(10, 0),
                        tomorrowAt(12, 0),
                        "Called in sick",
                        UnavailabilitySource.TECHNICIAN_SELF,
                        externalEventId);
        String body = objectMapper.writeValueAsString(request);

        mockMvc
                .perform(post("/internal/resource-unavailability").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalEventId").value(externalEventId));

        mockMvc
                .perform(post("/internal/resource-unavailability").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalEventId").value(externalEventId));
    }
}
