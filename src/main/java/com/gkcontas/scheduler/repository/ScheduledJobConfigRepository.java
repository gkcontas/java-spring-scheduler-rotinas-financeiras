package com.gkcontas.scheduler.repository;

import com.gkcontas.scheduler.model.ScheduledJobConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledJobConfigRepository extends JpaRepository<ScheduledJobConfig, Long> {

    Optional<ScheduledJobConfig> findByJobName(String jobName);

    List<ScheduledJobConfig> findAllByEnabledTrue();

    List<ScheduledJobConfig> findAllByOrderByJobNameAsc();
}
