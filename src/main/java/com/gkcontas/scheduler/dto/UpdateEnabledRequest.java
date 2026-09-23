package com.gkcontas.scheduler.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateEnabledRequest(
        @NotNull(message = "must not be null") Boolean enabled) {
}
