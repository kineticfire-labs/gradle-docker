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
import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for time-server using class lifecycle pattern.
 * 
 * Purpose: Demonstrates gradle-docker plugin compose orchestration with fresh environment
 * per test class. This showcases UC-7 (compose orchestration) with class lifecycle - 
 * containers are started/stopped for each test class, providing balanced isolation vs performance.
 * Useful for tests that need clean state per class but can share state within the class.
 * 
 * Lifecycle: Class - compose up/down per test class (balanced isolation and performance)
 * Network: Uses integrationClass compose stack on port 8082  
 * Pattern: Fresh environment per class, moderate performance, good for stateful testing
 */
@ExtendWith(DockerComposeClassExtension.class)
class TimeServerClassLifecycleIT {
    
    private static final String BASE_URL = "http://localhost:8082";
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void containerStartsWithFreshState() throws IOException {
        // Purpose: Verify fresh container environment provides clean initial state
        URL url = new URL(BASE_URL + "/metrics");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        // Fresh container should have low request count and uptime
        assertThat(jsonResponse.get("uptime").asLong()).isLessThan(60); // Less than 1 minute
        assertThat(jsonResponse.has("startTime")).isTrue();
    }
    
    @Test
    void healthCheckConfirmsContainerReadiness() throws IOException {
        // Purpose: Verify class lifecycle container reaches healthy state correctly
        URL url = new URL(BASE_URL + "/health");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        assertThat(jsonResponse.get("status").asText()).isEqualTo("healthy");
        assertThat(jsonResponse.has("timestamp")).isTrue();
        assertThat(jsonResponse.has("version")).isTrue();
    }
    
    @Test
    void echoServiceWorksInFreshEnvironment() throws IOException {
        // Purpose: Test service functionality in clean class-level container instance
        String testMessage = "class-lifecycle-test";
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
    void timeServiceProvidesAccurateTime() throws IOException {
        // Purpose: Validate time service accuracy in isolated class environment
        long beforeCall = System.currentTimeMillis() / 1000;
        
        URL url = new URL(BASE_URL + "/time");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        long afterCall = System.currentTimeMillis() / 1000;
        
        assertThat(connection.getResponseCode()).isEqualTo(200);
        
        String response = new String(connection.getInputStream().readAllBytes());
        JsonNode jsonResponse = objectMapper.readTree(response);
        
        long serverTime = jsonResponse.get("epoch").asLong();
        assertThat(serverTime).isBetween(beforeCall - 2, afterCall + 2); // Allow 2 second variance
        assertThat(jsonResponse.get("timezone").asText()).isEqualTo("UTC");
    }
}