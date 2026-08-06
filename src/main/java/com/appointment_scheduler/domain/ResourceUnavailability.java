package com.appointment_scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single table for any reason a bay or technician becomes unusable,
 * regardless of origin. See .claude/docs/reconciliation.md.
 *
 * <p>The generated {@code during} range column is intentionally not mapped
 * here — overlap queries use it directly via native SQL, not through a
 * Java property.
 */
@Entity
@Table(name = "resource_unavailability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceUnavailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    /** Null = open-ended (e.g. terminated employment). */
    @Column(name = "end_time")
    private OffsetDateTime endTime;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnavailabilitySource source;

    /** Idempotency key for events arriving from external systems. */
    @Column(name = "external_event_id", unique = true)
    private String externalEventId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
