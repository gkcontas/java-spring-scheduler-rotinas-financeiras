package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.config.SchedulingProperties;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Schedules routines programmatically so their cron can change while the service runs.
 *
 * <p>A {@code @Scheduled("0 0 3 * * *")} annotation is a compile-time constant: moving
 * the nightly run to 04:00 means a code change and a deploy. Holding the
 * {@link ScheduledFuture} of each task makes it possible to cancel and re-register it
 * with a new trigger instead.
 */
@Component
public class DynamicJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(DynamicJobScheduler.class);

    private final Map<String, ScheduledFuture<?>> scheduledByJobName = new ConcurrentHashMap<>();
    private final TaskScheduler taskScheduler;
    private final JobRegistry jobRegistry;
    private final SchedulingProperties schedulingProperties;

    public DynamicJobScheduler(TaskScheduler taskScheduler,
                               JobRegistry jobRegistry,
                               SchedulingProperties schedulingProperties) {
        this.taskScheduler = taskScheduler;
        this.jobRegistry = jobRegistry;
        this.schedulingProperties = schedulingProperties;
    }

    public synchronized void schedule(String jobName, String cronExpression) {
        cancel(jobName);

        // The zone is explicit. A CronTrigger without one uses the JVM default, which in
        // a container is typically UTC — so "0 0 3 * * *" fires at midnight local time
        // and nobody notices until a report comes out on the wrong day.
        CronTrigger trigger = new CronTrigger(cronExpression, schedulingProperties.zoneId());
        ScheduledFuture<?> future = taskScheduler.schedule(jobRegistry.scheduledTaskFor(jobName), trigger);

        if (future != null) {
            scheduledByJobName.put(jobName, future);
        }
        log.info("Routine '{}' scheduled with cron '{}' ({})",
                jobName, cronExpression, schedulingProperties.zone());
    }

    public synchronized void cancel(String jobName) {
        ScheduledFuture<?> future = scheduledByJobName.remove(jobName);
        if (future != null) {
            // false: do not interrupt a run already in progress. Interrupting would kill
            // it mid-chunk, which is exactly what the graceful shutdown settings avoid.
            future.cancel(false);
            log.info("Routine '{}' unscheduled", jobName);
        }
    }

    public void runNow(String jobName) {
        // Handed to the scheduler pool rather than executed inline, so the HTTP thread
        // that asked for it is not held for the whole run. Scheduling it for "now" is the
        // way to do that: TaskScheduler does not extend TaskExecutor, so there is no
        // execute(Runnable) to call.
        taskScheduler.schedule(jobRegistry.manualTaskFor(jobName), Instant.now());
    }

    public boolean isScheduled(String jobName) {
        ScheduledFuture<?> future = scheduledByJobName.get(jobName);
        return future != null && !future.isCancelled();
    }
}
