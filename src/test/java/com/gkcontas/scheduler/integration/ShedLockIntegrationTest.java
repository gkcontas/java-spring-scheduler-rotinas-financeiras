package com.gkcontas.scheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.scheduler.job.ReconciliationJob;
import com.gkcontas.scheduler.model.TriggerSource;
import com.gkcontas.scheduler.repository.JobExecutionLogRepository;
import com.gkcontas.scheduler.repository.TransactionRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The reason ShedLock is in this project.
 *
 * <p>Several callers ask for the same routine at the same moment, which is exactly what
 * happens when the service runs with more than one replica: every instance fires the same
 * cron in the same second. Only one of them may actually do the work — otherwise interest
 * is charged once per replica.
 */
class ShedLockIntegrationTest extends IntegrationTestBase {

    private static final int CONCURRENT_CALLERS = 4;

    @Autowired
    private ReconciliationJob reconciliationJob;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JobExecutionLogRepository executionLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        // Restoring the full backlog matters here: the routine has to take long enough
        // for the other callers to find the lock still held.
        jdbcTemplate.update("UPDATE transactions SET reconciled_at = NULL");
        jdbcTemplate.update("DELETE FROM job_execution_log WHERE job_name = ?", ReconciliationJob.JOB_NAME);
        // Expire the lock instead of deleting the row. JdbcTemplateLockProvider keeps an
        // in-JVM registry of the lock names it has already INSERTed, so that later
        // acquisitions only need an UPDATE. Deleting the row behind its back leaves that
        // registry stale: the UPDATE then matches nothing, the INSERT is skipped, and every
        // subsequent acquisition is reported as "already locked" — the routine silently
        // stops running.
        jdbcTemplate.update("UPDATE shedlock SET lock_until = now() WHERE name = ?", ReconciliationJob.JOB_NAME);
    }

    @Test
    void shouldRunOnlyOnceWhenSeveralCallersFireTheRoutineTogether() throws InterruptedException {
        long pendingBefore = transactionRepository.countPending();
        assertThat(pendingBefore).isPositive();

        CountDownLatch readyToStart = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(CONCURRENT_CALLERS);
        AtomicInteger failures = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CALLERS)) {
            for (int caller = 0; caller < CONCURRENT_CALLERS; caller++) {
                executor.submit(() -> {
                    try {
                        readyToStart.await();
                        reconciliationJob.run(TriggerSource.SCHEDULE);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException e) {
                        failures.incrementAndGet();
                    } finally {
                        finished.countDown();
                    }
                });
            }

            readyToStart.countDown(); // release all callers at once
            assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failures).hasValue(0);
        // Callers that cannot take the lock are skipped silently, so they never reach the
        // audit recorder — one execution row is the proof that only one did the work.
        assertThat(executionLogRepository.countByJobName(ReconciliationJob.JOB_NAME))
                .as("%d callers fired the routine together; only one may have run it", CONCURRENT_CALLERS)
                .isEqualTo(1);
        assertThat(transactionRepository.countPending()).isZero();
    }

    @Test
    void shouldReleaseTheLockSoLaterRunsStillHappen() {
        reconciliationJob.run(TriggerSource.SCHEDULE);
        // Without lockAtLeastFor the lock is released as soon as the run ends, so a
        // subsequent run is free to start immediately.
        reconciliationJob.run(TriggerSource.SCHEDULE);

        assertThat(executionLogRepository.countByJobName(ReconciliationJob.JOB_NAME)).isEqualTo(2);
    }
}
