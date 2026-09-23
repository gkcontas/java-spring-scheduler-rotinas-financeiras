package com.gkcontas.scheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.scheduler.job.ReconciliationJob;
import com.gkcontas.scheduler.model.JobExecutionLog;
import com.gkcontas.scheduler.model.JobStatus;
import com.gkcontas.scheduler.model.TriggerSource;
import com.gkcontas.scheduler.repository.JobExecutionLogRepository;
import com.gkcontas.scheduler.repository.TransactionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

class ReconciliationJobIntegrationTest extends IntegrationTestBase {

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
        // Tests share one database, so each one restores the state it depends on rather
        // than relying on whatever the previous test happened to leave behind.
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
    void shouldReconcileEveryPendingTransactionAcrossChunks() {
        long pendingBefore = transactionRepository.countPending();
        assertThat(pendingBefore).isPositive();

        reconciliationJob.run(TriggerSource.MANUAL);

        assertThat(transactionRepository.countPending()).isZero();

        JobExecutionLog execution = latestExecution();
        assertThat(execution.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(execution.getProcessedCount()).isEqualTo((int) pendingBefore);
        assertThat(execution.getTriggeredBy()).isEqualTo(TriggerSource.MANUAL);
        assertThat(execution.getFinishedAt()).isNotNull();
    }

    @Test
    void shouldBeIdempotentWhenRunTwice() {
        reconciliationJob.run(TriggerSource.MANUAL);
        long pendingAfterFirstRun = transactionRepository.countPending();

        reconciliationJob.run(TriggerSource.MANUAL);

        assertThat(pendingAfterFirstRun).isZero();
        // The second run finds nothing to do. That is the whole reason reconciled_at
        // exists: a retry after a partial failure resumes instead of reprocessing.
        assertThat(latestExecution().getProcessedCount()).isZero();
        assertThat(executionLogRepository.countByJobName(ReconciliationJob.JOB_NAME)).isEqualTo(2);
    }

    @Test
    void shouldRecordOneExecutionPerRun() {
        reconciliationJob.run(TriggerSource.SCHEDULE);
        reconciliationJob.run(TriggerSource.MANUAL);

        List<JobExecutionLog> executions = executionLogRepository
                .findByJobNameOrderByStartedAtDesc(ReconciliationJob.JOB_NAME, PageRequest.of(0, 10));

        assertThat(executions).hasSize(2);
        assertThat(executions).allMatch(execution -> execution.getStatus() == JobStatus.SUCCESS);
        assertThat(executions).extracting(JobExecutionLog::getTriggeredBy)
                .containsExactlyInAnyOrder(TriggerSource.SCHEDULE, TriggerSource.MANUAL);
    }

    private JobExecutionLog latestExecution() {
        return executionLogRepository
                .findByJobNameOrderByStartedAtDesc(ReconciliationJob.JOB_NAME, PageRequest.of(0, 1))
                .getFirst();
    }
}
