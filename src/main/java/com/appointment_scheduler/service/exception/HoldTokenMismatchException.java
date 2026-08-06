package com.appointment_scheduler.service.exception;

/** Maps to the documented {@code HOLD_TOKEN_MISMATCH} API error code (HTTP 404). */
public class HoldTokenMismatchException extends RuntimeException {

    public HoldTokenMismatchException(String message) {
        super(message);
    }
}
