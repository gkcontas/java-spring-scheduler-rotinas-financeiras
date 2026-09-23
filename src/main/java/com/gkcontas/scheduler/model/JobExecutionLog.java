package com.gkcontas.scheduler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit trail of every run.
 *
 * <p>A scheduled routine has no user watching it. Without a record of when it ran, how
 * long it took, how much it processed and why it failed, the only evidence a nightly job
 * stopped working three weeks ago is the damage it did not prevent.
 */
@Entity
@Table(name = "job_execution_log")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 80)
    private String jobName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 20)
    private TriggerSource triggeredBy;

    public JobExecutionLog(String jobName, TriggerSource triggeredBy) {
        this.jobName = jobName;
        this.triggeredBy = triggeredBy;
        this.startedAt = Instant.now();
        this.status = JobStatus.RUNNING;
        this.processedCount = 0;
    }
}
