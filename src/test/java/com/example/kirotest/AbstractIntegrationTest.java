package com.example.kirotest;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for full-slice integration tests.
 *
 * <p>Boots the complete Spring context on a random port against a real PostgreSQL instance
 * backed by the {@code pgvector} extension, provisioned via Testcontainers.
 *
 * <p><b>Singleton container pattern.</b> The container is started once in a static initializer
 * and deliberately <em>never</em> stopped — the Ryuk resource-reaper (or JVM shutdown) tears it
 * down at the end of the test run. This is intentional rather than using JUnit's
 * {@code @Testcontainers}/{@code @Container} lifecycle, which stops the container after the first
 * test class completes. Because each integration test class gets its own Spring context (and thus
 * its own connection pool), a per-class container lifecycle would leave a later class's reused
 * context pointing at an already-stopped container ("connection refused"). A single shared
 * container that outlives all classes avoids that and keeps the suite fast.
 *
 * <p>The {@code pgvector/pgvector:pg16} image is a drop-in PostgreSQL replacement; it is marked
 * as a compatible substitute for {@code postgres} so Testcontainers applies the correct
 * wait-strategy and JDBC handling. Datasource coordinates are bound dynamically via
 * {@link DynamicPropertySource}. Integration tests use the {@code integrationtest} profile,
 * which validates the Flyway-managed schema rather than letting Hibernate generate it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integrationtest")
public abstract class AbstractIntegrationTest {

    /**
     * Shared, singleton PostgreSQL + pgvector container for the entire integration-test run.
     * Started once and reused across every test class; never explicitly stopped.
     */
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ticketdb")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    /**
     * Binds the Spring datasource properties to the running container's JDBC coordinates.
     *
     * @param registry the registry Spring uses to resolve dynamic properties at context init
     */
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
