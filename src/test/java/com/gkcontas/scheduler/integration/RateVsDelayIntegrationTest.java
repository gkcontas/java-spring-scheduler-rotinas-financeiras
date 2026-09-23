package com.gkcontas.scheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.gkcontas.scheduler.dto.RateVsDelayReport;
import com.gkcontas.scheduler.job.RateVsDelayDemo;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Turns "fixedRate counts from the start, fixedDelay from the end" into a measurement.
 *
 * <p>Both tasks take 300 ms with a 100 ms interval. Because the work is longer than the
 * interval, fixedRate is permanently overdue and its runs come back to back, so the gap
 * between starts collapses to the task duration. fixedDelay always waits its 100 ms
 * after the previous run ends, so its gap is duration plus interval.
 */
class RateVsDelayIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RateVsDelayDemo rateVsDelayDemo;

    @Test
    void fixedRateShouldRunBackToBackWhileFixedDelayKeepsItsPause() {
        await().atMost(Duration.ofSeconds(30)).until(rateVsDelayDemo::hasEnoughSamples);

        RateVsDelayReport report = rateVsDelayDemo.report();
        double averageRateGap = average(report.fixedRateGapsMillis());
        double averageDelayGap = average(report.fixedDelayGapsMillis());

        // The invariant that always holds, whatever the machine's timing noise.
        assertThat(averageDelayGap)
                .as("fixedDelay must wait longer between starts than fixedRate")
                .isGreaterThan(averageRateGap);

        // The configured interval is only a floor for fixedRate: the task itself sets
        // the pace, so the gap tracks the duration and not the 100 ms asked for.
        assertThat(averageRateGap)
                .as("fixedRate gap should track the task duration, not the configured interval")
                .isBetween(230.0, 400.0);

        assertThat(averageDelayGap)
                .as("fixedDelay gap should be task duration plus the configured interval")
                .isBetween(330.0, 520.0);
    }

    private static double average(List<Long> gaps) {
        assertThat(gaps).isNotEmpty();
        return gaps.stream().mapToLong(Long::longValue).average().orElseThrow();
    }
}
