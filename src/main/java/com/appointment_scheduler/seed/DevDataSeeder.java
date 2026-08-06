package com.appointment_scheduler.seed;

import com.appointment_scheduler.domain.CustomerRef;
import com.appointment_scheduler.domain.Dealership;
import com.appointment_scheduler.domain.ServiceBay;
import com.appointment_scheduler.domain.ServiceType;
import com.appointment_scheduler.domain.ServiceTypeDurationOverride;
import com.appointment_scheduler.domain.ServiceTypeDurationOverrideId;
import com.appointment_scheduler.domain.Technician;
import com.appointment_scheduler.domain.TechnicianServiceType;
import com.appointment_scheduler.domain.TechnicianServiceTypeId;
import com.appointment_scheduler.domain.VehicleCategory;
import com.appointment_scheduler.domain.VehicleRef;
import com.appointment_scheduler.repository.CustomerRefRepository;
import com.appointment_scheduler.repository.DealershipRepository;
import com.appointment_scheduler.repository.ServiceBayRepository;
import com.appointment_scheduler.repository.ServiceTypeDurationOverrideRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.repository.TechnicianRepository;
import com.appointment_scheduler.repository.TechnicianServiceTypeRepository;
import com.appointment_scheduler.repository.VehicleRefRepository;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sample data for local exploration/demo — enough to exercise the full
 * hold→confirm flow via curl/Postman without hand-inserting rows. Only
 * active under the "dev" profile (`--spring.profiles.active=dev`), and
 * idempotent (skips entirely if a dealership already exists) so restarting
 * bootRun doesn't duplicate rows or hit the vehicle_ref.vin uniqueness
 * constraint.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements CommandLineRunner {

    private final DealershipRepository dealershipRepository;
    private final ServiceBayRepository serviceBayRepository;
    private final TechnicianRepository technicianRepository;
    private final TechnicianServiceTypeRepository technicianServiceTypeRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceTypeDurationOverrideRepository serviceTypeDurationOverrideRepository;
    private final VehicleRefRepository vehicleRefRepository;
    private final CustomerRefRepository customerRefRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (dealershipRepository.count() > 0) {
            log.info("Seed data already present, skipping DevDataSeeder");
            return;
        }

        Dealership dealership =
                dealershipRepository.save(
                        Dealership.builder()
                                .name("Keyloop Downtown")
                                .opensAt(LocalTime.of(8, 0))
                                .closesAt(LocalTime.of(18, 0))
                                .daysOpen(new Short[] {1, 2, 3, 4, 5, 6}) // Mon-Sat, closed Sunday
                                .build());

        ServiceBay bay1 = serviceBayRepository.save(bay(dealership, "Bay 1"));
        serviceBayRepository.save(bay(dealership, "Bay 2"));
        serviceBayRepository.save(bay(dealership, "Bay 3"));

        ServiceType oilChange = serviceTypeRepository.save(serviceType("Oil Change", 30));
        ServiceType tireRotation = serviceTypeRepository.save(serviceType("Tire Rotation", 45));
        ServiceType brakeInspection = serviceTypeRepository.save(serviceType("Brake Inspection", 60));
        ServiceType transmissionRepair = serviceTypeRepository.save(serviceType("Transmission Repair", 180));

        // Bigger vehicles take longer for the same nominal service.
        override(oilChange, VehicleCategory.TRUCK, 45);
        override(brakeInspection, VehicleCategory.TRUCK, 90);

        Technician alex = technicianRepository.save(technician(dealership, "Alex Rivera"));
        qualify(alex, oilChange);
        qualify(alex, tireRotation);
        qualify(alex, brakeInspection);

        Technician sam = technicianRepository.save(technician(dealership, "Sam Chen"));
        qualify(sam, oilChange);
        qualify(sam, tireRotation);

        Technician jordan = technicianRepository.save(technician(dealership, "Jordan Lee"));
        qualify(jordan, brakeInspection);
        qualify(jordan, transmissionRepair);

        vehicleRefRepository.save(
                VehicleRef.builder()
                        .vin("1HGCM82633A004352")
                        .make("Honda")
                        .model("Accord")
                        .vehicleCategory(VehicleCategory.SEDAN)
                        .build());
        vehicleRefRepository.save(
                VehicleRef.builder()
                        .vin("JH4KA7660PC008269")
                        .make("Acura")
                        .model("Legend")
                        .vehicleCategory(VehicleCategory.SUV)
                        .build());
        vehicleRefRepository.save(
                VehicleRef.builder()
                        .vin("1FTFW1ET5BFC10312")
                        .make("Ford")
                        .model("F-150")
                        .vehicleCategory(VehicleCategory.TRUCK)
                        .build());

        customerRefRepository.save(CustomerRef.builder().name("Jamie Rivera").phone("555-0100").build());
        customerRefRepository.save(CustomerRef.builder().name("Taylor Morgan").phone("555-0101").build());

        log.info(
                "Seeded dealership {} with 3 bays (incl. {}), 3 technicians, 4 service types, 3 vehicles, 2 customers",
                dealership.getId(),
                bay1.getLabel());
    }

    private ServiceBay bay(Dealership dealership, String label) {
        return ServiceBay.builder().dealership(dealership).label(label).active(true).build();
    }

    private ServiceType serviceType(String name, int durationMinutes) {
        return ServiceType.builder().name(name).durationMinutes(durationMinutes).build();
    }

    private Technician technician(Dealership dealership, String displayName) {
        return Technician.builder().dealership(dealership).displayName(displayName).active(true).build();
    }

    private void qualify(Technician technician, ServiceType serviceType) {
        technicianServiceTypeRepository.save(
                TechnicianServiceType.builder()
                        .id(new TechnicianServiceTypeId(technician.getId(), serviceType.getId()))
                        .technician(technician)
                        .serviceType(serviceType)
                        .build());
    }

    private void override(ServiceType serviceType, VehicleCategory category, int durationMinutes) {
        serviceTypeDurationOverrideRepository.save(
                ServiceTypeDurationOverride.builder()
                        .id(new ServiceTypeDurationOverrideId(serviceType.getId(), category))
                        .serviceType(serviceType)
                        .durationMinutes(durationMinutes)
                        .build());
    }
}
