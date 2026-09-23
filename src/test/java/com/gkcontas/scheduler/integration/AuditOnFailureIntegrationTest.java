package com.gkcontas.scheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.gkcontas.scheduler.job.ReconciliationChunkProcessor;
import com.gkcontas.scheduler.job.ReconciliationJob;
import com.gkcontas.scheduler.model.JobExecutionLog;
import com.gkcontas.scheduler.model.JobStatus;
import com.gkcontas.scheduler.model.TriggerSource;
import com.gkcontas.scheduler.repository.JobExecutionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The audit has to survive the failure it is reporting.
 *
 * <p>If {@code JobExecutionRecorder} shared the routine's transaction, the rollback
 * caused by the exception would take the FAILED row with it — and the only evidence that
 * a nightly routine has been broken for three weeks would be the damage it did not
 * prevent. {@code REQUIRES_NEW} is what keeps the record.
 */
class AuditOnFailureIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    private ReconciliationChunkProcessor chunkProcessor;

    @Autowired
    private ReconciliationJob reconciliationJob;

    @Autowired
    private JobExecutionLogRepository executionLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
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
    void shouldKeepTheFailureRecordEvenThoughTheRoutineRolledBack() {
        given(chunkProcessor.reconcileNextChunk(anyInt()))
                .willThrow(new IllegalStateException("ledger service unavailable"));

        assertThatThrownBy(() -> reconciliationJob.run(TriggerSource.SCHEDULE))
                .isInstanceOf(IllegalStateException.class);

        JobExecutionLog execution = executionLogRepository
                .findByJobNameOrderByStartedAtDesc(ReconciliationJob.JOB_NAME, PageRequest.of(0, 1))
                .getFirst();

        assertThat(execution.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(execution.getErrorMessage()).contains("ledger service unavailable");
        assertThat(execution.getFinishedAt()).isNotNull();
        assertThat(execution.getProcessedCount()).isZero();
    }

    @Test
    void shouldLetTheExceptionPropagateSoTheSchedulerErrorHandlerSeesIt() {
        given(chunkProcessor.reconcileNextChunk(anyInt()))
                .willThrow(new IllegalStateException("boom"));

        // Swallowing the exception here would make a broken routine look healthy to
        // everything upstream, including the scheduler's error handler and any alerting
        // hooked to it.
        assertThatThrownBy(() -> reconciliationJob.run(TriggerSource.SCHEDULE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }
}
