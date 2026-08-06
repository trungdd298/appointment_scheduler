package com.appointment_scheduler.web.dto;

import jakarta.validation.constraints.NotNull;

public record ReassignTechnicianRequest(@NotNull Long technicianId) {}
