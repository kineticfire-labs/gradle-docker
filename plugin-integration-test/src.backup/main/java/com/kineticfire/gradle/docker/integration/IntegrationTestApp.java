/*
 * (c) Copyright 2023-2025 gradle-docker Contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kineticfire.gradle.docker.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Integration test application for validating the gradle-docker plugin
 */
public class IntegrationTestApp {
    
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestApp.class);
    
    public static void main(String[] args) {
        logger.info("Starting Integration Test Application");
        
        // Display build information from Docker build args
        String buildVersion = System.getProperty("app.build.version", "unknown");
        String buildTime = System.getProperty("app.build.time", "unknown");
        
        logger.info("Application Version: {}", buildVersion);
        logger.info("Build Time: {}", buildTime);
        logger.info("Java Version: {}", System.getProperty("java.version"));
        logger.info("Application started at: {}", 
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        // Create a health check endpoint simulation
        HealthChecker healthChecker = new HealthChecker();
        healthChecker.start();
        
        // Keep the application running
        try {
            logger.info("Integration Test App is running...");
            logger.info("Application will run for 60 seconds for testing purposes");
            
            Thread.sleep(60000); // Run for 1 minute for testing
            
        } catch (InterruptedException e) {
            logger.warn("Application interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            healthChecker.stop();
            logger.info("Integration Test Application shutting down");
        }
    }
    
    /**
     * Simulates a health check service that would be monitored by Docker/Compose
     */
    static class HealthChecker {
        private final Logger logger = LoggerFactory.getLogger(HealthChecker.class);
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        private volatile boolean healthy = true;
        
        public void start() {
            logger.info("Starting health checker...");
            
            // Schedule health status logging every 10 seconds
            scheduler.scheduleAtFixedRate(() -> {
                if (healthy) {
                    logger.info("Health Check: APPLICATION_HEALTHY - All systems operational");
                } else {
                    logger.warn("Health Check: APPLICATION_UNHEALTHY - Issues detected");
                }
            }, 5, 10, TimeUnit.SECONDS);
        }
        
        public void stop() {
            logger.info("Stopping health checker...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }
    }
}