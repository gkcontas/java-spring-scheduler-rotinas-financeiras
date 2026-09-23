package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.dto.RateVsDelayReport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Makes the difference between fixedRate and fixedDelay measurable instead of quotable.
 *
 * <p>Both tasks take {@value #TASK_DURATION_MILLIS} ms and are configured with the same
 * {@value #INTERVAL_MILLIS} ms interval — deliberately shorter than the work itself,
 * which is where the two stop behaving alike:
 *
 * <ul>
 *   <li><b>fixedRate</b> counts from the previous <em>start</em>. The next run is already
 *       overdue when the current one ends, so runs come back to back and the gap between
 *       starts collapses to the task duration (~300 ms). The interval is not honoured;
 *       it is merely a floor. Note it does <em>not</em> run two copies at once —
 *       {@code scheduleAtFixedRate} never overlaps a task with itself.</li>
 *   <li><b>fixedDelay</b> counts from the previous <em>end</em>. The gap between starts
 *       is duration plus interval (~400 ms), and the pause is always respected.</li>
 * </ul>
 *
 * <p>Both stop recording after {@value #SAMPLE_LIMIT} samples so the demo does not keep
 * burning scheduler threads for the life of the process.
 */
@Component
public class RateVsDelayDemo {

    static final long TASK_DURATION_MILLIS = 300;
    static final long INTERVAL_MILLIS = 100;
    static final int SAMPLE_LIMIT = 5;

    private final List<Long> fixedRateStarts = new CopyOnWriteArrayList<>();
    private final List<Long> fixedDelayStarts = new CopyOnWriteArrayList<>();

    @Scheduled(fixedRate = INTERVAL_MILLIS, initialDelay = 500)
    void fixedRateTask() throws InterruptedException {
        if (fixedRateStarts.size() >= SAMPLE_LIMIT) {
            return;
        }
        fixedRateStarts.add(System.currentTimeMillis());
        Thread.sleep(TASK_DURATION_MILLIS);
    }

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = 500)
    void fixedDelayTask() throws InterruptedException {
        if (fixedDelayStarts.size() >= SAMPLE_LIMIT) {
            return;
        }
        fixedDelayStarts.add(System.currentTimeMillis());
        Thread.sleep(TASK_DURATION_MILLIS);
    }

    public RateVsDelayReport report() {
        return new RateVsDelayReport(
                TASK_DURATION_MILLIS,
                INTERVAL_MILLIS,
                gapsBetweenStarts(fixedRateStarts),
                gapsBetweenStarts(fixedDelayStarts),
                "fixedRate counts from the previous start, so with a task longer than the interval "
                        + "the runs end up back to back (gap ~= task duration). fixedDelay counts from "
                        + "the previous end, so the gap is task duration + interval.");
    }

    public boolean hasEnoughSamples() {
        return fixedRateStarts.size() >= SAMPLE_LIMIT && fixedDelayStarts.size() >= SAMPLE_LIMIT;
    }

    private static List<Long> gapsBetweenStarts(List<Long> starts) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < starts.size(); i++) {
            gaps.add(starts.get(i) - starts.get(i - 1));
        }
        return gaps;
    }
}
