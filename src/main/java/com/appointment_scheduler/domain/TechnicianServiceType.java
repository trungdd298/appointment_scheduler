package com.appointment_scheduler.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Qualification join table — a technician is "qualified" for a service type
 * iff a row exists here. See .claude/docs/data-model.md.
 */
@Entity
@Table(name = "technician_service_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicianServiceType {

    @EmbeddedId
    private TechnicianServiceTypeId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("technicianId")
    @JoinColumn(name = "technician_id")
    private Technician technician;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("serviceTypeId")
    @JoinColumn(name = "service_type_id")
    private ServiceType serviceType;
}
