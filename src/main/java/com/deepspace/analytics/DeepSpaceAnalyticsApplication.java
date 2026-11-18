package com.deepspace.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the DeepSpace Analytics web application.
 *
 * <p>Stage 1 establishes the Spring Boot foundation, project layout, and
 * homepage presentation for the Poisson and Geometric deep-space communication
 * probability models.
 */
@SpringBootApplication
public class DeepSpaceAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeepSpaceAnalyticsApplication.class, args);
    }
}

