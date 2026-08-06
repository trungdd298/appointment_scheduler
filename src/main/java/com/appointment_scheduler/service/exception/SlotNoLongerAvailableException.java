package com.appointment_scheduler.service.exception;

/** Maps to the documented {@code SLOT_NO_LONGER_AVAILABLE} API error code (HTTP 409). */
public class SlotNoLongerAvailableException extends RuntimeException {

    public SlotNoLongerAvailableException(String message) {
        super(message);
    }
}
