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

package com.kineticfire.gradle.docker.integration.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple HTTP time server application for demonstrating gradle-docker plugin usage.
 * 
 * This application provides REST endpoints that can be easily tested in containers:
 * - GET /health - Health check endpoint
 * - GET /time - Returns current time in UTC
 * - GET /echo - Echo service with message length
 * - GET /metrics - Basic application metrics
 * 
 * Purpose: Demonstrates a realistic Java application that would be containerized
 * and tested using the gradle-docker plugin's compose orchestration capabilities.
 */
public class TimeServerApp {
    
    private static final Logger logger = LoggerFactory.getLogger(TimeServerApp.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Application metrics
    private final AtomicLong requestCount = new AtomicLong(0);
    private final Instant startTime = Instant.now();
    
    public static void main(String[] args) {
        new TimeServerApp().start();
    }
    
    public void start() {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.requestLogger.http((ctx, executionTimeMs) -> {
                requestCount.incrementAndGet();
                logger.info("{} {} - {}ms", ctx.method(), ctx.path(), executionTimeMs);
            });
        }).start(port);
        
        logger.info("Time Server started on port {}", port);
        
        // Health check endpoint
        app.get("/health", ctx -> {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "healthy");
            response.put("timestamp", Instant.now().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT));
            response.put("version", getClass().getPackage().getImplementationVersion());
            ctx.json(response);
        });
        
        // Time endpoint  
        app.get("/time", ctx -> {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("time", Instant.now().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT));
            response.put("timezone", "UTC");
            response.put("epoch", Instant.now().getEpochSecond());
            ctx.json(response);
        });
        
        // Echo endpoint
        app.get("/echo", ctx -> {
            String message = ctx.queryParam("msg");
            if (message == null) {
                message = "hello";
            }
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("echo", message);
            response.put("length", message.length());
            response.put("uppercase", message.toUpperCase());
            ctx.json(response);
        });
        
        // Metrics endpoint
        app.get("/metrics", ctx -> {
            long uptimeSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("uptime", uptimeSeconds);
            response.put("requests", requestCount.get());
            response.put("startTime", startTime.atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT));
            ctx.json(response);
        });
        
        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Time Server...");
            app.stop();
        }));
    }
}