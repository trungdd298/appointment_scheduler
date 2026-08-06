package com.appointment_scheduler.web;

import com.appointment_scheduler.service.exception.HoldExpiredException;
import com.appointment_scheduler.service.exception.HoldTokenMismatchException;
import com.appointment_scheduler.service.exception.ResourceNotFoundException;
import com.appointment_scheduler.service.exception.SlotNoLongerAvailableException;
import com.appointment_scheduler.service.exception.TechnicianNotQualifiedException;
import com.appointment_scheduler.service.exception.ValidationException;
import com.appointment_scheduler.web.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps service-layer exceptions to the documented (status, error code)
 * pairs from .claude/docs/openapi.yaml's ErrorResponse schema. Every error
 * code the schema declares is handled here, even where a given endpoint's
 * OpenAPI doc only calls out a subset of them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(HoldTokenMismatchException.class)
    public ResponseEntity<ErrorResponse> handleHoldTokenMismatch(HoldTokenMismatchException e) {
        return error(HttpStatus.NOT_FOUND, "HOLD_TOKEN_MISMATCH", e.getMessage());
    }

    @ExceptionHandler(SlotNoLongerAvailableException.class)
    public ResponseEntity<ErrorResponse> handleSlotNoLongerAvailable(SlotNoLongerAvailableException e) {
        return error(HttpStatus.CONFLICT, "SLOT_NO_LONGER_AVAILABLE", e.getMessage());
    }

    @ExceptionHandler(HoldExpiredException.class)
    public ResponseEntity<ErrorResponse> handleHoldExpired(HoldExpiredException e) {
        return error(HttpStatus.GONE, "HOLD_EXPIRED", e.getMessage());
    }

    @ExceptionHandler(TechnicianNotQualifiedException.class)
    public ResponseEntity<ErrorResponse> handleTechnicianNotQualified(TechnicianNotQualifiedException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "TECHNICIAN_NOT_QUALIFIED", e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", e.getMessage());
    }

    /** Bean-validation failures on @Valid @RequestBody DTOs (e.g. HoldRequest). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        String message =
                e.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** Bean-validation failures on @RequestParam/@PathVariable (e.g. windowHours out of range). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(ConstraintViolationException e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    /** A required @RequestParam (e.g. vehicleId) is missing entirely. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    /** Malformed/unparseable JSON request body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed request body");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
