package com.appointment_scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dealership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dealership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    /** ISO 8601 weekday numbers, Monday = 1 .. Sunday = 7. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_open", nullable = false, columnDefinition = "smallint[]")
    private Short[] daysOpen;
}
