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
 * Integration tests for time-server using suite lifecycle pattern.
 * 
 * Purpose: Demonstrates gradle-docker plugin compose orchestration with shared environment
 * for performance-oriented integration testing. This showcases UC-7 (compose orchestration)
 * with suite lifecycle - container environment is shared across all tests in this class
 * for optimal performance while maintaining adequate test isolation.
 * 
 * Lifecycle: Suite - compose up once for entire test class (balanced performance)
 * Network: Uses integrationSuite compose stack on port 8081
 * Pattern: Shared state across tests, faster execution, good for functional validation
 */
class TimeServerSuiteLifecycleIT {
    
    private static final String BASE_URL = "http://localhost:8081";
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void healthCheckEndpointIsAccessible() throws IOException {
        // Purpose: Verify container health check endpoint is working
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
    void timeEndpointReturnsValidTimeData() throws IOException {
        // Purpose: Verify time service functionality works in containerized environment
        URL url = new URL(BASE_URL + "/time");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        assertThat(jsonResponse.has("time")).isTrue();
        assertThat(jsonResponse.get("timezone").asText()).isEqualTo("UTC");
        assertThat(jsonResponse.get("epoch").asLong()).isPositive();
    }
    
    @Test
    void echoServiceProcessesParametersCorrectly() throws IOException {
        // Purpose: Test parameterized endpoints work correctly in Docker environment
        String testMessage = "integration-suite-test";
        URL url = new URL(BASE_URL + "/echo?msg=" + testMessage);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        assertThat(jsonResponse.get("echo").asText()).isEqualTo(testMessage);
        assertThat(jsonResponse.get("length").asInt()).isEqualTo(testMessage.length());
        assertThat(jsonResponse.get("uppercase").asText()).isEqualTo(testMessage.toUpperCase());
    }
    
    @Test
    void metricsEndpointShowsApplicationState() throws IOException {
        // Purpose: Verify application metrics collection works in containerized deployment
        URL url = new URL(BASE_URL + "/metrics");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        assertThat(jsonResponse.has("uptime")).isTrue();
        assertThat(jsonResponse.get("uptime").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(jsonResponse.has("requests")).isTrue();
        assertThat(jsonResponse.get("requests").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(jsonResponse.has("startTime")).isTrue();
    }
}