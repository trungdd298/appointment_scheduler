package com.appointment_scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Core entity, 100% owned by the Scheduler. See
 * .claude/docs/booking-flow.md for the Hold&rarr;Confirm state machine and
 * .claude/docs/architecture.md for the EXCLUDE-constraint concurrency
 * guarantee this entity relies on.
 *
 * <p>The generated {@code during} range column is intentionally not mapped
 * here — the overlap-prevention constraints live in the database and
 * candidate-search queries use {@code during} directly via native SQL.
 */
@Entity
@Table(name = "appointment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dealership_id", nullable = false)
    private Dealership dealership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bay_id", nullable = false)
    private ServiceBay bay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private Technician technician;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleRef vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerRef customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    /** Set only when status = HELD. */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    /** Idempotency key for confirm — DB-generated, never written by the app. */
    @Column(name = "hold_token", insertable = false, updatable = false)
    private UUID holdToken;

    @Column(name = "needs_attention_reason")
    private String needsAttentionReason;

    /** Set when transitioning to NEEDS_ATTENTION. */
    @Column(name = "escalation_deadline")
    private OffsetDateTime escalationDeadline;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Maintained by the trg_appointment_updated_at DB trigger. */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
