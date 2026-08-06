package com.appointment_scheduler.service.exception;

/** Maps to the documented {@code TECHNICIAN_NOT_QUALIFIED} API error code (HTTP 422). */
public class TechnicianNotQualifiedException extends RuntimeException {

    public TechnicianNotQualifiedException(String message) {
        super(message);
    }
}
