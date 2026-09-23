package com.gkcontas.scheduler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    /**
     * Declaring this bean is not a formality.
     *
     * <p>Spring's default scheduler is a pool of <strong>one</strong> thread. Two
     * routines due at the same instant do not run together — the second waits for the
     * first, and a slow routine delays every other one behind it. The symptom is a job
     * that "runs late" for no visible reason, and nothing in the code hints at it.
     *
     * <p>Defining a {@code taskScheduler} bean means {@code spring.task.scheduling.*}
     * no longer applies, so pool size and shutdown behaviour are set here instead.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(SchedulingProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.poolSize());
        scheduler.setThreadNamePrefix("finance-job-");

        // Graceful shutdown: on SIGTERM, let a running routine finish instead of killing
        // it halfway through a chunk.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(properties.awaitTerminationSeconds());

        // Without an error handler, an exception escaping a scheduled task is logged by
        // Spring but — more importantly — must never be allowed to cancel the trigger.
        // Handling it here keeps the schedule alive after a failed run.
        scheduler.setErrorHandler(throwable ->
                log.error("Scheduled routine failed; the schedule remains active", throwable));

        return scheduler;
    }
}
