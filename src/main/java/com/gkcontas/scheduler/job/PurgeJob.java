package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.model.TriggerSource;
import com.gkcontas.scheduler.repository.JobExecutionLogRepository;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the audit table from growing forever.
 *
 * <p>The only routine here that uses a plain {@code @Scheduled} annotation, and it uses
 * <strong>fixedDelay</strong> rather than fixedRate on purpose. How long a purge takes
 * depends on how much there is to delete; fixedDelay counts from the end of the previous
 * run, so there is always a real pause between runs no matter how long one takes.
 * fixedRate counts from the <em>start</em>, so a run that overshoots the interval is
 * followed immediately by the next one, with no breathing room at all.
 */
@Component
public class PurgeJob {

    public static final String JOB_NAME = "execution-log-purge";

    private final JobRunner jobRunner;
    private final JobExecutionLogRepository repository;
    private final Duration retention;

    public PurgeJob(JobRunner jobRunner,
                    JobExecutionLogRepository repository,
                    @Value("${app.purge.retention}") Duration retention) {
        this.jobRunner = jobRunner;
        this.repository = repository;
        this.retention = retention;
    }

    @Scheduled(initialDelayString = "${app.purge.initial-delay}", fixedDelayString = "${app.purge.fixed-delay}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "PT5M")
    public void run() {
        jobRunner.run(JOB_NAME, TriggerSource.SCHEDULE,
                () -> repository.deleteOlderThan(Instant.now().minus(retention)));
    }
}
