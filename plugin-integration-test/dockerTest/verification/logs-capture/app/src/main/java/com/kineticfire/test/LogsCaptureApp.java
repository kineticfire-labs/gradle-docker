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

package com.kineticfire.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot application for testing log capture functionality.
 *
 * This application logs messages at various levels and in different
 * scenarios to validate the plugin's log capture capabilities.
 */
@SpringBootApplication
@RestController
public class LogsCaptureApp {

    private static final Logger logger = LoggerFactory.getLogger(LogsCaptureApp.class);
    private int requestCounter = 0;

    public static void main(String[] args) {
        logger.info("=== Starting Logs Capture Test Application ===");
        SpringApplication.run(LogsCaptureApp.class, args);
    }

    @PostConstruct
    public void init() {
        logger.info("Application initialized successfully");
        logger.info("Log capture verification test is ready");
        logger.warn("This is a warning message for testing");
        logger.error("This is an error message for testing (not a real error)");
        logger.info("Multi-line log test - Line 1");
        logger.info("Multi-line log test - Line 2");
        logger.info("Multi-line log test - Line 3");
        logger.info("Multi-line log test - Line 4");
        logger.info("Multi-line log test - Line 5");
        logger.info("Initialization complete");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        logger.debug("Health check endpoint called");
        return Map.of("status", "UP");
    }

    @GetMapping("/log/{level}")
    public Map<String, Object> logMessage(@PathVariable String level) {
        requestCounter++;
        String message = "Test log message #" + requestCounter + " at level: " + level;

        switch (level.toUpperCase()) {
            case "INFO":
                logger.info(message);
                break;
            case "WARN":
                logger.warn(message);
                break;
            case "ERROR":
                logger.error(message);
                break;
            case "DEBUG":
                logger.debug(message);
                break;
            default:
                logger.info("Unknown level requested: " + level);
                return Map.of(
                    "status", "error",
                    "message", "Unknown log level: " + level,
                    "requestNumber", requestCounter
                );
        }

        return Map.of(
            "status", "logged",
            "level", level,
            "message", message,
            "requestNumber", requestCounter
        );
    }

    @GetMapping("/multiline")
    public Map<String, String> multilineLog() {
        requestCounter++;
        logger.info("=== Multi-line Log Test #" + requestCounter + " ===");
        logger.info("Line 1 of multi-line test");
        logger.info("Line 2 of multi-line test");
        logger.info("Line 3 of multi-line test");
        logger.info("Line 4 of multi-line test");
        logger.info("Line 5 of multi-line test");
        logger.info("=== End of Multi-line Test ===");

        return Map.of(
            "status", "logged",
            "lines", "6",
            "requestNumber", String.valueOf(requestCounter)
        );
    }

    @GetMapping("/counter")
    public Map<String, Object> getCounter() {
        return Map.of(
            "requestCounter", requestCounter,
            "status", "ok"
        );
    }
}
