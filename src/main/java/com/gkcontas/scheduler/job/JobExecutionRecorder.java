package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.model.JobExecutionLog;
import com.gkcontas.scheduler.model.JobStatus;
import com.gkcontas.scheduler.model.TriggerSource;
import com.gkcontas.scheduler.repository.JobExecutionLogRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail in its own transaction.
 *
 * <p>Every method here is {@code REQUIRES_NEW}, and that is the entire point. If the
 * audit shared the routine's transaction, a failure would roll back the routine
 * <em>and the record of the failure with it</em> — leaving no trace that anything ever
 * went wrong. The audit has to survive precisely the cases it exists to report.
 */
@Service
public class JobExecutionRecorder {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final JobExecutionLogRepository repository;

    public JobExecutionRecorder(JobExecutionLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(String jobName, TriggerSource triggeredBy) {
        return repository.save(new JobExecutionLog(jobName, triggeredBy)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long executionId, int processedCount) {
        repository.findById(executionId).ifPresent(execution -> {
            execution.setStatus(JobStatus.SUCCESS);
            execution.setProcessedCount(processedCount);
            execution.setFinishedAt(Instant.now());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long executionId, String errorMessage) {
        repository.findById(executionId).ifPresent(execution -> {
            execution.setStatus(JobStatus.FAILED);
            execution.setErrorMessage(truncate(errorMessage));
            execution.setFinishedAt(Instant.now());
        });
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
