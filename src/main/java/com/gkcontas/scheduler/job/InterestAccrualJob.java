package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.config.SchedulingProperties;
import com.gkcontas.scheduler.model.TriggerSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InterestAccrualJob {

    public static final String JOB_NAME = "interest-accrual";

    private static final int CHUNK_SIZE = 200;

    private final JobRunner jobRunner;
    private final InterestAccrualChunkProcessor chunkProcessor;
    private final SchedulingProperties schedulingProperties;
    private final BigDecimal dailyRate;

    public InterestAccrualJob(JobRunner jobRunner,
                              InterestAccrualChunkProcessor chunkProcessor,
                              SchedulingProperties schedulingProperties,
                              @Value("${app.interest.daily-rate}") BigDecimal dailyRate) {
        this.jobRunner = jobRunner;
        this.chunkProcessor = chunkProcessor;
        this.schedulingProperties = schedulingProperties;
        this.dailyRate = dailyRate;
    }

    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "PT10M")
    public void run(TriggerSource triggeredBy) {
        jobRunner.run(JOB_NAME, triggeredBy, this::accrueInterest);
    }

    private int accrueInterest() {
        // The business day comes from the configured zone, not from the JVM default.
        // In a UTC container, LocalDate.now() rolls over at 21:00 local time, so a job
        // running late at night would stamp invoices with tomorrow's date.
        LocalDate today = LocalDate.now(schedulingProperties.zoneId());

        int total = 0;
        int processedInChunk;
        do {
            processedInChunk = chunkProcessor.applyInterestToNextChunk(today, dailyRate, CHUNK_SIZE);
            total += processedInChunk;
        } while (processedInChunk > 0);
        return total;
    }
}
