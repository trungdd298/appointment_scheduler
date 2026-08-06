package com.appointment_scheduler.repository;

import com.appointment_scheduler.domain.Technician;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    /**
     * Active technicians at the dealership qualified for the service type —
     * "qualified" = a technician_service_type row exists. See
     * .claude/docs/assumptions.md.
     */
    @Query(
            """
            SELECT t FROM Technician t
            WHERE t.dealership.id = :dealershipId AND t.active = true
              AND EXISTS (
                SELECT 1 FROM TechnicianServiceType tst
                WHERE tst.technician = t AND tst.serviceType.id = :serviceTypeId
              )
            """)
    List<Technician> findQualifiedActive(
            @Param("dealershipId") Long dealershipId, @Param("serviceTypeId") Long serviceTypeId);
}
