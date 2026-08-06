package com.appointment_scheduler.domain;

import jakarta.persistence.Column;
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

@Entity
@Table(name = "service_type_duration_override")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceTypeDurationOverride {

    @EmbeddedId
    private ServiceTypeDurationOverrideId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("serviceTypeId")
    @JoinColumn(name = "service_type_id")
    private ServiceType serviceType;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;
}
