package com.appointment_scheduler.web.dto;

import com.appointment_scheduler.domain.CustomerRef;

public record CustomerResponse(Long id, String name, String phone) {

    public static CustomerResponse from(CustomerRef customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone());
    }
}
