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

package com.kineticfire.gradle.docker.integration.appimage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for time-server using method lifecycle pattern.
 * 
 * Purpose: Demonstrates gradle-docker plugin compose orchestration with maximum isolation -
 * fresh container environment for each test method. This showcases UC-7 (compose orchestration)
 * with method lifecycle for scenarios requiring complete test isolation, such as testing 
 * stateful operations, data persistence, or scenarios where test order independence is critical.
 * 
 * Lifecycle: Method - compose up/down per test method (maximum isolation, slowest)
 * Network: Uses integrationMethod compose stack on port 8083
 * Pattern: Fresh environment per method, slowest performance, maximum test independence
 */
class TimeServerMethodLifecycleIT {
    
    private static final String BASE_URL = "http://localhost:8083";
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void eachMethodGetsFreshContainerForMaximumIsolation() throws IOException {
        // Purpose: Verify each test method gets completely fresh container environment
        URL url = new URL(BASE_URL + "/metrics");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        // Fresh container per method should have minimal request count
        long requests = jsonResponse.get("requests").asLong();
        assertThat(requests).isLessThanOrEqualTo(1); // Only this request or none yet
        
        // Uptime should be very low (fresh start)
        assertThat(jsonResponse.get("uptime").asLong()).isLessThan(30);
    }
    
    @Test
    void healthCheckWorksInCompletelyIsolatedEnvironment() throws IOException {
        // Purpose: Validate health check in maximum isolation scenario
        URL url = new URL(BASE_URL + "/health");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        assertThat(jsonResponse.get("status").asText()).isEqualTo("healthy");
        assertThat(jsonResponse.has("timestamp")).isTrue();
    }
    
    @Test
    void timeServiceStartsCleanWithMethodLifecycle() throws IOException {
        // Purpose: Test that time service starts with clean state for each method
        URL url = new URL(BASE_URL + "/time");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        assertThat(jsonResponse.has("time")).isTrue();
        assertThat(jsonResponse.get("timezone").asText()).isEqualTo("UTC");
        
        // Verify we get current time (within reasonable bounds)
        long epochTime = jsonResponse.get("epoch").asLong();
        long currentTime = System.currentTimeMillis() / 1000;
        assertThat(epochTime).isBetween(currentTime - 10, currentTime + 10);
    }
    
    @Test
    void echoServiceHasNoStateFromPreviousTests() throws IOException {
        // Purpose: Verify echo service has completely fresh state (no memory from other tests)
        String uniqueMessage = "method-isolation-" + System.nanoTime();
        URL url = new URL(BASE_URL + "/echo?msg=" + uniqueMessage);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        assertThat(jsonResponse.get("echo").asText()).isEqualTo(uniqueMessage);
        assertThat(jsonResponse.get("length").asInt()).isEqualTo(uniqueMessage.length());
        assertThat(jsonResponse.get("uppercase").asText()).isEqualTo(uniqueMessage.toUpperCase());
        
        // Verify metrics show this is first interaction
        URL metricsUrl = new URL(BASE_URL + "/metrics");
        HttpURLConnection metricsConnection = (HttpURLConnection) metricsUrl.openConnection();
        String metricsResponse = new String(metricsConnection.getInputStream().readAllBytes());
        JsonNode metricsJson = objectMapper.readTree(metricsResponse);
        
        // Should show only our requests (echo + metrics = 2 requests)
        assertThat(metricsJson.get("requests").asLong()).isEqualTo(2);
    }
}