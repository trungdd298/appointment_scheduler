package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.Appointment;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * True iff at least one active bay at the dealership has no overlapping
     * HELD/CONFIRMED appointment and no overlapping resource_unavailability
     * for [start, end). Uses {@code during} directly via native SQL — see
     * .claude/docs/data-model.md#jpa-mapping-notes for why it isn't mapped
     * as a Java property.
     */
    @Query(
            value =
                    """
                    SELECT EXISTS (
                      SELECT 1 FROM service_bay sb
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
                    )
                    """,
            nativeQuery = true)
    boolean existsFreeBay(
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
}
