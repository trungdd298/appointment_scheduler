package com.appointment_scheduler.service.exception;

/** Maps to the documented {@code VALIDATION_ERROR} API error code (HTTP 422/400). */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
