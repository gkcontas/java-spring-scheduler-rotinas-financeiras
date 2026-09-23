package com.gkcontas.scheduler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * The schedule of a routine, stored as data rather than as a constant in an annotation.
 *
 * <p>A cron inside {@code @Scheduled("0 0 3 * * *")} can only change through a code
 * change, a build and a deploy. Keeping it in a row means an operator can move the
 * nightly run to a different hour while the service stays up.
 */
@Entity
@Table(name = "scheduled_job_config")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledJobConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, unique = true, length = 80)
    private String jobName;

    @Column(name = "cron_expression", nullable = false, length = 120)
    private String cronExpression;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ScheduledJobConfig(String jobName, String cronExpression, String description, boolean enabled) {
        this.jobName = jobName;
        this.cronExpression = cronExpression;
        this.description = description;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
