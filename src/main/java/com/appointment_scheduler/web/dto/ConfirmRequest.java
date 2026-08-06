package com.appointment_scheduler.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConfirmRequest(@NotNull UUID holdToken) {}
