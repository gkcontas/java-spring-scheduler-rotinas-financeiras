package com.gkcontas.scheduler.service;

import com.gkcontas.scheduler.config.SchedulingProperties;
import com.gkcontas.scheduler.dto.JobExecutionResponse;
import com.gkcontas.scheduler.dto.JobSummaryResponse;
import com.gkcontas.scheduler.exception.InvalidCronExpressionException;
import com.gkcontas.scheduler.exception.UnknownJobException;
import com.gkcontas.scheduler.job.DynamicJobScheduler;
import com.gkcontas.scheduler.job.JobRegistry;
import com.gkcontas.scheduler.model.ScheduledJobConfig;
import com.gkcontas.scheduler.repository.JobExecutionLogRepository;
import com.gkcontas.scheduler.repository.ScheduledJobConfigRepository;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobAdminService {

    private final ScheduledJobConfigRepository configRepository;
    private final JobExecutionLogRepository executionLogRepository;
    private final DynamicJobScheduler dynamicJobScheduler;
    private final JobRegistry jobRegistry;
    private final SchedulingProperties schedulingProperties;

    public JobAdminService(ScheduledJobConfigRepository configRepository,
                           JobExecutionLogRepository executionLogRepository,
                           DynamicJobScheduler dynamicJobScheduler,
                           JobRegistry jobRegistry,
                           SchedulingProperties schedulingProperties) {
        this.configRepository = configRepository;
        this.executionLogRepository = executionLogRepository;
        this.dynamicJobScheduler = dynamicJobScheduler;
        this.jobRegistry = jobRegistry;
        this.schedulingProperties = schedulingProperties;
    }

    @Transactional(readOnly = true)
    public List<JobSummaryResponse> listJobs() {
        return configRepository.findAllByOrderByJobNameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public JobSummaryResponse updateCron(String jobName, String cronExpression) {
        // Validated before anything is persisted: storing a broken expression would take
        // the routine down at the next restart, long after whoever typed it has left.
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new InvalidCronExpressionException(cronExpression);
        }

        ScheduledJobConfig config = requireConfig(jobName);
        config.setCronExpression(cronExpression);
        config.setUpdatedAt(Instant.now());

        if (config.isEnabled()) {
            dynamicJobScheduler.schedule(jobName, cronExpression);
        }
        return toSummary(config);
    }

    @Transactional
    public JobSummaryResponse setEnabled(String jobName, boolean enabled) {
        ScheduledJobConfig config = requireConfig(jobName);
        config.setEnabled(enabled);
        config.setUpdatedAt(Instant.now());

        if (enabled) {
            dynamicJobScheduler.schedule(jobName, config.getCronExpression());
        } else {
            dynamicJobScheduler.cancel(jobName);
        }
        return toSummary(config);
    }

    /**
     * Fire-and-forget: the routine is handed to the scheduler pool and the caller gets an
     * immediate 202. If another instance is already holding the lock, ShedLock skips this
     * run silently — which is why the answer is "check the execution log", not a result.
     */
    public void trigger(String jobName) {
        if (!jobRegistry.isKnown(jobName)) {
            throw new UnknownJobException(jobName);
        }
        dynamicJobScheduler.runNow(jobName);
    }

    @Transactional(readOnly = true)
    public List<JobExecutionResponse> executions(String jobName, int limit) {
        if (!jobRegistry.isKnown(jobName) && configRepository.findByJobName(jobName).isEmpty()) {
            throw new UnknownJobException(jobName);
        }
        return executionLogRepository
                .findByJobNameOrderByStartedAtDesc(jobName, PageRequest.of(0, limit)).stream()
                .map(JobExecutionResponse::from)
                .toList();
    }

    private ScheduledJobConfig requireConfig(String jobName) {
        return configRepository.findByJobName(jobName)
                .orElseThrow(() -> new UnknownJobException(jobName));
    }

    private JobSummaryResponse toSummary(ScheduledJobConfig config) {
        return new JobSummaryResponse(
                config.getJobName(),
                config.getCronExpression(),
                config.getDescription(),
                config.isEnabled(),
                dynamicJobScheduler.isScheduled(config.getJobName()),
                nextExecution(config));
    }

    private Instant nextExecution(ScheduledJobConfig config) {
        if (!config.isEnabled() || !CronExpression.isValidExpression(config.getCronExpression())) {
            return null;
        }
        ZonedDateTime next = CronExpression.parse(config.getCronExpression())
                .next(ZonedDateTime.now(schedulingProperties.zoneId()));
        return next == null ? null : next.toInstant();
    }
}
