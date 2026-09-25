package com.example.kirotest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Spring Data JPA configuration.
 *
 * <p>Enables JPA auditing so that entities annotated with
 * {@code @EntityListeners(AuditingEntityListener.class)} have their
 * {@code @CreatedDate} and {@code @LastModifiedDate} fields populated
 * automatically on persist and update.
 *
 * <p>Auditing is kept in a dedicated configuration class (rather than on the AI
 * or application configuration) so that {@code @WebMvcTest} slices and other
 * targeted test contexts can opt out of JPA auditing without dragging in
 * unrelated configuration.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
    // No beans required — @EnableJpaAuditing wires the AuditingEntityListener
    // and the auditing infrastructure automatically.
}
