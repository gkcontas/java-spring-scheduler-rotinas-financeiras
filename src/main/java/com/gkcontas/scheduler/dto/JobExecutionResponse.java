package com.gkcontas.scheduler.dto;

import com.gkcontas.scheduler.model.JobExecutionLog;
import java.time.Duration;
import java.time.Instant;

public record JobExecutionResponse(
        Long id,
        String jobName,
        Instant startedAt,
        Instant finishedAt,
        Long durationMillis,
        String status,
        int processedCount,
        String errorMessage,
        String triggeredBy) {

    public static JobExecutionResponse from(JobExecutionLog execution) {
        Long durationMillis = execution.getFinishedAt() == null
                ? null
                : Duration.between(execution.getStartedAt(), execution.getFinishedAt()).toMillis();

        return new JobExecutionResponse(
                execution.getId(),
                execution.getJobName(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                durationMillis,
                execution.getStatus().name(),
                execution.getProcessedCount(),
                execution.getErrorMessage(),
                execution.getTriggeredBy().name());
    }
}
