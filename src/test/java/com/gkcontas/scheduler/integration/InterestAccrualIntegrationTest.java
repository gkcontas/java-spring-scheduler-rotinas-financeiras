package com.gkcontas.scheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.scheduler.job.InterestAccrualJob;
import com.gkcontas.scheduler.model.TriggerSource;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class InterestAccrualIntegrationTest extends IntegrationTestBase {

    @Autowired
    private InterestAccrualJob interestAccrualJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("UPDATE invoices SET interest_amount = 0, interest_applied_on = NULL");
        jdbcTemplate.update("DELETE FROM job_execution_log WHERE job_name = ?", InterestAccrualJob.JOB_NAME);
        // Expire the lock instead of deleting the row. JdbcTemplateLockProvider keeps an
        // in-JVM registry of the lock names it has already INSERTed, so that later
        // acquisitions only need an UPDATE. Deleting the row behind its back leaves that
        // registry stale: the UPDATE then matches nothing, the INSERT is skipped, and every
        // subsequent acquisition is reported as "already locked" — the routine silently
        // stops running.
        jdbcTemplate.update("UPDATE shedlock SET lock_until = now() WHERE name = ?", InterestAccrualJob.JOB_NAME);
    }

    @Test
    void shouldChargeInterestOnlyOncePerDayNoMatterHowManyTimesItRuns() {
        interestAccrualJob.run(TriggerSource.MANUAL);

        BigDecimal totalAfterFirstRun = totalInterest();
        long chargedAfterFirstRun = countWithInterest();
        assertThat(totalAfterFirstRun).isGreaterThan(BigDecimal.ZERO);
        assertThat(chargedAfterFirstRun).isPositive();

        interestAccrualJob.run(TriggerSource.MANUAL);
        interestAccrualJob.run(TriggerSource.MANUAL);

        // Two extra runs, not a cent more. Charging interest twice is the kind of bug
        // that reaches a customer's statement before it reaches a bug tracker.
        assertThat(totalInterest()).isEqualByComparingTo(totalAfterFirstRun);
        assertThat(countWithInterest()).isEqualTo(chargedAfterFirstRun);
    }

    @Test
    void shouldOnlyTouchOverdueOpenInvoices() {
        interestAccrualJob.run(TriggerSource.MANUAL);

        Long paidWithInterest = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE status <> 'OPEN' AND interest_amount > 0", Long.class);
        Long notYetDueWithInterest = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE due_date >= CURRENT_DATE AND interest_amount > 0", Long.class);

        assertThat(paidWithInterest).isZero();
        assertThat(notYetDueWithInterest).isZero();
    }

    private BigDecimal totalInterest() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(interest_amount), 0) FROM invoices", BigDecimal.class);
    }

    private long countWithInterest() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE interest_amount > 0", Long.class);
        return count == null ? 0 : count;
    }
}
