package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.model.TriggerSource;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wraps a routine so that starting, finishing and failing are always recorded, without
 * each routine having to remember to do it.
 */
@Service
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final JobExecutionRecorder recorder;

    public JobRunner(JobExecutionRecorder recorder) {
        this.recorder = recorder;
    }

    public void run(String jobName, TriggerSource triggeredBy, IntSupplier work) {
        Long executionId = recorder.start(jobName, triggeredBy);
        long startedAt = System.currentTimeMillis();
        try {
            int processed = work.getAsInt();
            recorder.succeed(executionId, processed);
            log.info("Routine '{}' finished: {} item(s) in {} ms ({})",
                    jobName, processed, System.currentTimeMillis() - startedAt, triggeredBy);
        } catch (RuntimeException e) {
            recorder.fail(executionId, e.toString());
            log.error("Routine '{}' failed after {} ms ({})",
                    jobName, System.currentTimeMillis() - startedAt, triggeredBy, e);
            throw e;
        }
    }
}
