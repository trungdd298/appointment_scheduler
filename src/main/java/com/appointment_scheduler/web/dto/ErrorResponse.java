package com.appointment_scheduler.web.dto;

/** {@code error} values match .claude/docs/openapi.yaml's ErrorResponse schema exactly. */
public record ErrorResponse(String error, String message) {}
