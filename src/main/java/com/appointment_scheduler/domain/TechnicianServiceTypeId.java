package com.appointment_scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TechnicianServiceTypeId implements Serializable {

    @Column(name = "technician_id")
    private Long technicianId;

    @Column(name = "service_type_id")
    private Long serviceTypeId;
}
