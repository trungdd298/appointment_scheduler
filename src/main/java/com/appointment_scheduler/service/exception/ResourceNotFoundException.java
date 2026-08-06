package com.appointment_scheduler.service.exception;

/** Maps to the documented {@code NOT_FOUND} API error code. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
