package com.example.kirotest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test verifying the Spring application context loads.
 *
 * <p>Runs under the {@code test} profile (H2 in-memory database, Flyway disabled, a single
 * OpenAI-backed model provider with a placeholder key, and the in-memory
 * {@code SimpleVectorStore}). No network calls are made at context-load time, so this test
 * validates core wiring (JPA, web, services, exception handling, AI bean graph) rather than
 * external connectivity.
 */
@SpringBootTest
@ActiveProfiles("test")
class KirotestApplicationTests {

	@Test
	void contextLoads() {
	}

}
