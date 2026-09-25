package com.example.kirotest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Application entry point.
 *
 * <p>Enables asynchronous method execution used by the embedding pipeline to
 * re-ingest ticket content off the request thread. JPA auditing is enabled
 * separately in {@link com.example.kirotest.config.JpaConfig} so that targeted
 * test slices can opt out of it independently.
 */
@SpringBootApplication
@EnableAsync
public class KirotestApplication {

	/**
	 * Boots the Spring application context and starts the embedded web server.
	 *
	 * @param args command-line arguments forwarded to Spring Boot (e.g. {@code --spring.profiles.active=dev})
	 */
	public static void main(String[] args) {
		SpringApplication.run(KirotestApplication.class, args);
	}

}
