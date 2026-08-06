package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.Appointment;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Fetches an appointment with every association eagerly loaded, so the
     * returned entity is safe to read (bay/technician/vehicle/customer/
     * serviceType included) after the originating transaction closes —
     * service methods must never return a plain findById() result to a
     * caller that might read its lazy associations post-transaction.
     */
    @Query(
            "SELECT a FROM Appointment a "
                    + "JOIN FETCH a.dealership JOIN FETCH a.bay JOIN FETCH a.technician "
                    + "JOIN FETCH a.vehicle JOIN FETCH a.customer JOIN FETCH a.serviceType "
                    + "WHERE a.id = :id")
    Optional<Appointment> findByIdWithAssociations(@Param("id") Long id);

    /**
     * IDs of active bays at the dealership with no overlapping
     * HELD/CONFIRMED appointment and no overlapping resource_unavailability
     * for [start, end), ordered for deterministic candidate selection.
     * Uses {@code during} directly via native SQL — see
     * .claude/docs/data-model.md#jpa-mapping-notes for why it isn't mapped
     * as a Java property. Used both to check "is any bay free" (availability
     * search) and to enumerate retry candidates (booking — see
     * BookingService).
     */
    @Query(
            value =
                    """
                    SELECT sb.id FROM service_bay sb
                    WHERE sb.dealership_id = :dealershipId AND sb.is_active = true
                      AND NOT EXISTS (
                        SELECT 1 FROM appointment a
                        WHERE a.bay_id = sb.id AND a.status IN ('HELD','CONFIRMED')
                          AND a.during && tstzrange(:start, :end, '[)')
                      )
                      AND NOT EXISTS (
                        SELECT 1 FROM resource_unavailability ru
                        WHERE ru.resource_type = 'BAY' AND ru.resource_id = sb.id
                          AND ru.during && tstzrange(:start, :end, '[)')
                      )
                    ORDER BY sb.id
                    """,
            nativeQuery = true)
    List<Long> findFreeBayIds(
            @Param("dealershipId") Long dealershipId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /**
     * True iff the given technician has no overlapping HELD/CONFIRMED
     * appointment and no overlapping resource_unavailability for
     * [start, end). Does not check qualification — see
     * TechnicianRepository#findQualifiedActive.
     */
    @Query(
            value =
                    """
                    SELECT (
                      NOT EXISTS (
                        SELECT 1 FROM appointment a
                        WHERE a.technician_id = :technicianId AND a.status IN ('HELD','CONFIRMED')
                          AND a.during && tstzrange(:start, :end, '[)')
                      )
                      AND NOT EXISTS (
                        SELECT 1 FROM resource_unavailability ru
                        WHERE ru.resource_type = 'TECHNICIAN' AND ru.resource_id = :technicianId
                          AND ru.during && tstzrange(:start, :end, '[)')
                      )
                    )
                    """,
            nativeQuery = true)
    boolean isTechnicianFree(
            @Param("technicianId") Long technicianId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /**
     * Flips stale HELD rows (past their TTL) to EXPIRED so the EXCLUDE
     * constraints release the slot for other bookers. A bulk update
     * translates to a plain SQL UPDATE, so the trg_appointment_updated_at
     * trigger still fires normally. See BookingService's hold-expiry sweep.
     */
    @Modifying
    @Query(
            "UPDATE Appointment a SET a.status = com.appointment_scheduler.domain.AppointmentStatus.EXPIRED "
                    + "WHERE a.status = com.appointment_scheduler.domain.AppointmentStatus.HELD "
                    + "AND a.expiresAt < :now")
    int expireStaleHolds(@Param("now") OffsetDateTime now);
}
