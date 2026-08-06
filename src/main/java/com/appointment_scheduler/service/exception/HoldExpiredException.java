package com.appointment_scheduler.service.exception;

/** Maps to the documented {@code HOLD_EXPIRED} API error code (HTTP 410). */
public class HoldExpiredException extends RuntimeException {

    public HoldExpiredException(String message) {
        super(message);
    }
}
