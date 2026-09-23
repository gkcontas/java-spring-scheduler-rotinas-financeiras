package com.gkcontas.scheduler.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Distributed locking.
 *
 * <p>{@code @Scheduled} guarantees a routine does not overlap <em>with itself inside one
 * JVM</em>. It says nothing about other JVMs: run three replicas and all three fire the
 * same cron at the same second, so interest gets charged three times. The lock lives in
 * a database row, which is the one thing every replica shares.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        // Times the lock by the database clock rather than each
                        // application's clock. Replicas drift, and a few seconds of drift
                        // is enough to let two of them believe the lock has expired.
                        .usingDbTime()
                        .build());
    }
}
