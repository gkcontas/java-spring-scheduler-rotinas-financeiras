package com.gkcontas.scheduler.repository;

import com.gkcontas.scheduler.model.JobExecutionLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, Long> {

    List<JobExecutionLog> findByJobNameOrderByStartedAtDesc(String jobName, Pageable pageable);

    long countByJobName(String jobName);

    /**
     * A bulk delete needs a transaction of its own. Declaring it here avoids an extra
     * bean whose only job would be to carry the {@code @Transactional} across a proxy.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM JobExecutionLog l WHERE l.startedAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);
}
