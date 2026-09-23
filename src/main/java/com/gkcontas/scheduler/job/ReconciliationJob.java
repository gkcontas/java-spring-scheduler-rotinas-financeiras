package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.model.TriggerSource;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reconciles pending transactions, in chunks, under a distributed lock.
 *
 * <p>Scheduled dynamically from {@code scheduled_job_config} rather than through a
 * {@code @Scheduled} annotation, so the cron can be changed at runtime.
 */
@Component
public class ReconciliationJob {

    public static final String JOB_NAME = "reconciliation";

    private final JobRunner jobRunner;
    private final ReconciliationChunkProcessor chunkProcessor;
    private final int chunkSize;

    public ReconciliationJob(JobRunner jobRunner,
                             ReconciliationChunkProcessor chunkProcessor,
                             @Value("${app.reconciliation.chunk-size}") int chunkSize) {
        this.jobRunner = jobRunner;
        this.chunkProcessor = chunkProcessor;
        this.chunkSize = chunkSize;
    }

    /**
     * Returns void on purpose: when ShedLock cannot take the lock it simply does not
     * invoke the method, and a void signature leaves no ambiguity about what a caller
     * gets back in that case. What the run actually did is in the audit log.
     */
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "PT10M")
    public void run(TriggerSource triggeredBy) {
        jobRunner.run(JOB_NAME, triggeredBy, this::reconcileEverythingPending);
    }

    private int reconcileEverythingPending() {
        int total = 0;
        int processedInChunk;
        do {
            processedInChunk = chunkProcessor.reconcileNextChunk(chunkSize);
            total += processedInChunk;
        } while (processedInChunk > 0);
        return total;
    }
}
