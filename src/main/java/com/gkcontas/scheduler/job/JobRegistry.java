package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.exception.UnknownJobException;
import com.gkcontas.scheduler.model.TriggerSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Maps a job name to the code that runs it.
 *
 * <p>The method references stored here point at the <em>injected beans</em>, which are
 * Spring proxies. That detail is what keeps {@code @SchedulerLock} working: ShedLock
 * applies the lock through an around-advice on the proxy, so invoking the plain target
 * object would silently run the routine with no lock at all — the same trap as calling a
 * {@code @Transactional} method on {@code this}.
 */
@Component
public class JobRegistry {

    private final Map<String, Consumer<TriggerSource>> tasksByName;

    public JobRegistry(ReconciliationJob reconciliationJob, InterestAccrualJob interestAccrualJob) {
        Map<String, Consumer<TriggerSource>> tasks = new LinkedHashMap<>();
        tasks.put(ReconciliationJob.JOB_NAME, reconciliationJob::run);
        tasks.put(InterestAccrualJob.JOB_NAME, interestAccrualJob::run);
        this.tasksByName = Map.copyOf(tasks);
    }

    public Runnable scheduledTaskFor(String jobName) {
        Consumer<TriggerSource> task = taskFor(jobName);
        return () -> task.accept(TriggerSource.SCHEDULE);
    }

    public Runnable manualTaskFor(String jobName) {
        Consumer<TriggerSource> task = taskFor(jobName);
        return () -> task.accept(TriggerSource.MANUAL);
    }

    public boolean isKnown(String jobName) {
        return tasksByName.containsKey(jobName);
    }

    public Set<String> jobNames() {
        return tasksByName.keySet();
    }

    private Consumer<TriggerSource> taskFor(String jobName) {
        Consumer<TriggerSource> task = tasksByName.get(jobName);
        if (task == null) {
            throw new UnknownJobException(jobName);
        }
        return task;
    }
}
