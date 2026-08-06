package com.appointment_scheduler.web;

import com.appointment_scheduler.repository.CustomerRefRepository;
import com.appointment_scheduler.repository.DealershipRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.repository.VehicleRefRepository;
import com.appointment_scheduler.web.dto.CustomerResponse;
import com.appointment_scheduler.web.dto.DealershipResponse;
import com.appointment_scheduler.web.dto.ServiceTypeResponse;
import com.appointment_scheduler.web.dto.VehicleResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only listing endpoints for the picker/dropdown data the booking flow
 * needs (dealership, service type, vehicle, customer) — not part of the
 * original openapi.yaml scope, added so the frontend has something to
 * populate its forms from instead of requiring raw IDs. See
 * .claude/docs/booking-flow.md.
 */
@RestController
@RequiredArgsConstructor
public class ReferenceDataController {

    private final DealershipRepository dealershipRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final VehicleRefRepository vehicleRefRepository;
    private final CustomerRefRepository customerRefRepository;

    @GetMapping("/dealerships")
    public List<DealershipResponse> listDealerships() {
        return dealershipRepository.findAll(Sort.by("name")).stream().map(DealershipResponse::from).toList();
    }

    @GetMapping("/service-types")
    public List<ServiceTypeResponse> listServiceTypes() {
        return serviceTypeRepository.findAll(Sort.by("name")).stream().map(ServiceTypeResponse::from).toList();
    }

    @GetMapping("/vehicles")
    public List<VehicleResponse> listVehicles() {
        return vehicleRefRepository.findAll(Sort.by("vin")).stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/customers")
    public List<CustomerResponse> listCustomers() {
        return customerRefRepository.findAll(Sort.by("name")).stream().map(CustomerResponse::from).toList();
    }
}
