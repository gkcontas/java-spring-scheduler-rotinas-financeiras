package com.gkcontas.scheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gkcontas.scheduler.job.DynamicJobScheduler;
import com.gkcontas.scheduler.job.ReconciliationJob;
import com.gkcontas.scheduler.repository.ScheduledJobConfigRepository;
import com.gkcontas.scheduler.repository.TransactionRepository;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class JobAdminIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScheduledJobConfigRepository configRepository;

    @Autowired
    private DynamicJobScheduler dynamicJobScheduler;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("UPDATE transactions SET reconciled_at = NULL");
        jdbcTemplate.update("DELETE FROM job_execution_log WHERE job_name = ?", ReconciliationJob.JOB_NAME);
        // Expire the lock instead of deleting the row. JdbcTemplateLockProvider keeps an
        // in-JVM registry of the lock names it has already INSERTed, so that later
        // acquisitions only need an UPDATE. Deleting the row behind its back leaves that
        // registry stale: the UPDATE then matches nothing, the INSERT is skipped, and every
        // subsequent acquisition is reported as "already locked" — the routine silently
        // stops running.
        jdbcTemplate.update("UPDATE shedlock SET lock_until = now() WHERE name = ?", ReconciliationJob.JOB_NAME);
        jdbcTemplate.update("UPDATE scheduled_job_config SET cron_expression = '0 */5 * * * *', enabled = TRUE "
                + "WHERE job_name = ?", ReconciliationJob.JOB_NAME);
    }

    @AfterEach
    void unschedule() {
        // Leaves no live trigger behind that could fire during a later test.
        dynamicJobScheduler.cancel(ReconciliationJob.JOB_NAME);
    }

    @Test
    void shouldListConfiguredJobs() throws Exception {
        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'reconciliation')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'interest-accrual')]").exists());
    }

    @Test
    void shouldChangeTheCronAtRuntimeAndScheduleItImmediately() throws Exception {
        mockMvc.perform(put("/jobs/{name}/schedule", ReconciliationJob.JOB_NAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cronExpression\": \"0 0 4 * * *\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cronExpression").value("0 0 4 * * *"))
                .andExpect(jsonPath("$.scheduled").value(true))
                .andExpect(jsonPath("$.nextExecution").isNotEmpty());

        // Persisted, so it survives a restart — the change is not only in memory.
        assertThat(configRepository.findByJobName(ReconciliationJob.JOB_NAME).orElseThrow()
                .getCronExpression()).isEqualTo("0 0 4 * * *");
        assertThat(dynamicJobScheduler.isScheduled(ReconciliationJob.JOB_NAME)).isTrue();
    }

    @Test
    void shouldRejectAnInvalidCronWithoutTouchingTheStoredSchedule() throws Exception {
        String originalCron = configRepository.findByJobName(ReconciliationJob.JOB_NAME)
                .orElseThrow().getCronExpression();

        mockMvc.perform(put("/jobs/{name}/schedule", ReconciliationJob.JOB_NAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cronExpression\": \"not a cron\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid cron expression"));

        // Validation happens before the write: a broken expression must never reach the
        // table, or the routine dies at the next restart instead of at the request.
        assertThat(configRepository.findByJobName(ReconciliationJob.JOB_NAME).orElseThrow()
                .getCronExpression()).isEqualTo(originalCron);
    }

    @Test
    void shouldDisableAndReEnableARoutine() throws Exception {
        mockMvc.perform(put("/jobs/{name}/enabled", ReconciliationJob.JOB_NAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.scheduled").value(false))
                .andExpect(jsonPath("$.nextExecution").doesNotExist());

        mockMvc.perform(put("/jobs/{name}/enabled", ReconciliationJob.JOB_NAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.scheduled").value(true));
    }

    @Test
    void shouldAcceptAManualTriggerAndRecordItAsManual() throws Exception {
        mockMvc.perform(post("/jobs/{name}/trigger", ReconciliationJob.JOB_NAME))
                .andExpect(status().isAccepted());

        // 202, not 200: the routine was handed to the scheduler pool, so the work has
        // not happened yet when the response is written.
        await().atMost(Duration.ofSeconds(60))
                .until(() -> transactionRepository.countPending() == 0);

        mockMvc.perform(get("/jobs/{name}/executions", ReconciliationJob.JOB_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].triggeredBy").value("MANUAL"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void shouldReturnNotFoundForAnUnknownJob() throws Exception {
        mockMvc.perform(post("/jobs/{name}/trigger", "no-such-job"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Unknown job"));
    }

    @Test
    void shouldReportValidationErrorsInEnglishRegardlessOfTheHostLocale() throws Exception {
        // Bean Validation resolves default messages from the JVM locale, so without an
        // explicit message this same call answers in Portuguese on this machine.
        mockMvc.perform(put("/jobs/{name}/schedule", ReconciliationJob.JOB_NAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cronExpression\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("cronExpression must not be blank"));
    }

    @Test
    void shouldRejectAnOutOfRangeExecutionLimit() throws Exception {
        mockMvc.perform(get("/jobs/{name}/executions", ReconciliationJob.JOB_NAME).param("limit", "9999"))
                .andExpect(status().isBadRequest());
    }
}
