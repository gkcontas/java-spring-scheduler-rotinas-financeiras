package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.config.SchedulingProperties;
import com.gkcontas.scheduler.model.ScheduledJobConfig;
import com.gkcontas.scheduler.repository.ScheduledJobConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registers the stored schedules once the application is fully up.
 *
 * <p>{@code ApplicationReadyEvent} rather than {@code @PostConstruct}: the routines need
 * a working {@code DataSource} and a migrated schema, and at construction time neither is
 * guaranteed. Starting a job that immediately queries a table Flyway has not created yet
 * is a confusing way to fail.
 */
@Component
public class JobBootstrap {

    private static final Logger log = LoggerFactory.getLogger(JobBootstrap.class);

    private final ScheduledJobConfigRepository configRepository;
    private final DynamicJobScheduler dynamicJobScheduler;
    private final JobRegistry jobRegistry;
    private final SchedulingProperties schedulingProperties;

    public JobBootstrap(ScheduledJobConfigRepository configRepository,
                        DynamicJobScheduler dynamicJobScheduler,
                        JobRegistry jobRegistry,
                        SchedulingProperties schedulingProperties) {
        this.configRepository = configRepository;
        this.dynamicJobScheduler = dynamicJobScheduler;
        this.jobRegistry = jobRegistry;
        this.schedulingProperties = schedulingProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleEnabledJobs() {
        if (!schedulingProperties.bootstrapEnabled()) {
            log.info("Automatic scheduling is disabled; routines will only run when triggered");
            return;
        }
        for (ScheduledJobConfig config : configRepository.findAllByEnabledTrue()) {
            if (!jobRegistry.isKnown(config.getJobName())) {
                // A configuration row with no matching bean: worth a warning rather than
                // a startup failure, since it usually means a routine was removed in code
                // while its row is still in the table.
                log.warn("Configuration for unknown routine '{}' ignored", config.getJobName());
                continue;
            }
            dynamicJobScheduler.schedule(config.getJobName(), config.getCronExpression());
        }
    }
}
