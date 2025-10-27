package com.kineticfire.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Multi-service orchestration demonstration application.
 * <p>
 * This application demonstrates complex multi-service orchestration with:
 * - PostgreSQL database for persistent storage
 * - Redis for caching
 * - Nginx as reverse proxy
 * <p>
 * Used for testing the dockerOrch plugin's ability to orchestrate complex stacks.
 */
@SpringBootApplication
public class MultiServiceApp {
    private static final Logger logger = LoggerFactory.getLogger(MultiServiceApp.class);

    public static void main(String[] args) {
        logger.info("Starting Multi-Service Application");
        SpringApplication.run(MultiServiceApp.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        logger.info("Multi-service application is ready");
        logger.info("Connected to PostgreSQL and Redis");
    }
}
