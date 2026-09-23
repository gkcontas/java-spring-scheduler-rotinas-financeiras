package com.gkcontas.scheduler.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bootstrapEnabled whether stored schedules are registered at startup. Turning it
 *                         off yields an instance that still serves the admin API and
 *                         manual triggers but never fires a routine on its own — useful
 *                         for a maintenance replica, and for tests that need to control
 *                         exactly when a routine runs.
 */
@ConfigurationProperties(prefix = "app.scheduling")
public record SchedulingProperties(
        String zone,
        int poolSize,
        int awaitTerminationSeconds,
        boolean bootstrapEnabled) {

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
