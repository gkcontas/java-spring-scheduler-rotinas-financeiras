package com.gkcontas.scheduler.dto;

import java.time.Instant;

public record JobSummaryResponse(
        String name,
        String cronExpression,
        String description,
        boolean enabled,
        boolean scheduled,
        Instant nextExecution) {
}
