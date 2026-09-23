package com.gkcontas.scheduler.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for the integration tests.
 *
 * <p>The container follows the <em>singleton container</em> pattern: started in a static
 * initializer and never handed to the {@code @Testcontainers} JUnit extension. That
 * extension ties a static container's lifecycle to the <em>test class</em>, stopping it
 * when the class finishes and starting a fresh one, on a new random port, for the next
 * class. Spring caches the application context across classes with the same
 * configuration, so from the second class onwards the cached connection pool would point
 * at a container that no longer exists. One container per JVM keeps the two lifecycles
 * aligned; Ryuk still removes it when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
