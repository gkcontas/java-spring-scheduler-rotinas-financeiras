package com.gkcontas.scheduler.dto;

import java.util.List;

/**
 * The gaps are the evidence. With a task longer than the interval, fixedRate gaps settle
 * at roughly the task duration and fixedDelay gaps at duration plus interval.
 */
public record RateVsDelayReport(
        long taskDurationMillis,
        long configuredIntervalMillis,
        List<Long> fixedRateGapsMillis,
        List<Long> fixedDelayGapsMillis,
        String explanation) {
}
