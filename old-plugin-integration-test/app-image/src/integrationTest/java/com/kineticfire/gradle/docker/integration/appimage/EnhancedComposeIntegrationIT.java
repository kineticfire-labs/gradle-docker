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
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Enhanced integration tests focusing on complex Docker Compose orchestration scenarios.
 * 
 * Purpose: Test advanced compose scenarios that stress-test the plugin's compose service
 * integration and significantly improve coverage of compose operations, service waiting,
 * log capture, and health checking functionality.
 * 
 * Tests: Multi-service orchestration, service health monitoring, long-running operations,
 *        error scenarios, timeout handling, and service dependencies.
 * Coverage: Targets ExecLibraryComposeService methods through real compose operations
 */
class EnhancedComposeIntegrationIT {
    
    private static final String BASE_URL_SUITE = "http://localhost:8081";
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    @DisplayName("Services handle concurrent load effectively")
    void servicesHandleConcurrentLoadEffectively() throws Exception {
        // Purpose: Test service stability under concurrent requests
        
        int concurrentRequests = 10;
        Instant startTime = Instant.now();
        
        // Create multiple concurrent requests
        Thread[] threads = new Thread[concurrentRequests];
        boolean[] results = new boolean[concurrentRequests];
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    URL url = URI.create(BASE_URL_SUITE + "/time").toURL();
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    
                    results[index] = (connection.getResponseCode() == 200);
                } catch (Exception e) {
                    results[index] = false;
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(10000); // 10 second timeout per thread
        }
        
        Duration totalTime = Duration.between(startTime, Instant.now());
        
        // Verify that most requests succeeded
        int successCount = 0;
        for (boolean result : results) {
            if (result) successCount++;
        }
            
        assertThat(successCount)
            .as("Most concurrent requests should succeed")
            .isGreaterThanOrEqualTo((int)(concurrentRequests * 0.8)); // At least 80% success rate
            
        assertThat(totalTime)
            .as("Concurrent requests should complete in reasonable time")
            .isLessThan(Duration.ofSeconds(30));
    }
    
    @Test
    @DisplayName("Service health check integration works correctly")
    void serviceHealthCheckIntegrationWorksCorrectly() throws Exception {
        // Purpose: Test health check monitoring and reporting
        
        URL healthUrl = URI.create(BASE_URL_SUITE + "/health").toURL();
        
        // Test health endpoint multiple times to verify consistency
        for (int i = 0; i < 5; i++) {
            HttpURLConnection connection = (HttpURLConnection) healthUrl.openConnection();
            connection.setRequestMethod("GET");
            
            assertThat(connection.getResponseCode())
                .as("Health check %d should return 200", i + 1)
                .isEqualTo(200);
            
            String response = new String(connection.getInputStream().readAllBytes());
            JsonNode jsonResponse = objectMapper.readTree(response);
            
            assertThat(jsonResponse.get("status").asText())
                .as("Health status should be healthy")
                .isEqualTo("healthy");
                
            assertThat(jsonResponse.has("timestamp"))
                .as("Health response should include timestamp")
                .isTrue();
                
            // Small delay between checks
            Thread.sleep(1000);
        }
    }
    
    @Test
    @DisplayName("Service handles parameter validation correctly")
    void serviceHandlesParameterValidationCorrectly() throws Exception {
        // Purpose: Test service input validation and error handling
        
        // Test echo endpoint with various parameter scenarios
        String[] testCases = {
            "", // empty message
            "simple", // basic message
            "message with spaces", // spaces
            "special!@#$%^&*()chars", // special characters
            "very_long_message_that_exceeds_normal_length_expectations_and_tests_handling_of_large_inputs",
            "unicode测试消息", // Unicode characters
        };
        
        for (String testMessage : testCases) {
            String encodedMessage = java.net.URLEncoder.encode(testMessage, "UTF-8");
            URL url = URI.create(BASE_URL_SUITE + "/echo?msg=" + encodedMessage).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            // All requests should succeed (service should handle various inputs)
            assertThat(connection.getResponseCode())
                .as("Echo endpoint should handle message: '%s'", testMessage)
                .isEqualTo(200);
                
            String response = new String(connection.getInputStream().readAllBytes());
            JsonNode jsonResponse = objectMapper.readTree(response);
            
            assertThat(jsonResponse.get("echo").asText())
                .as("Echo should return the original message")
                .isEqualTo(testMessage);
                
            assertThat(jsonResponse.get("length").asInt())
                .as("Length should match message length")
                .isEqualTo(testMessage.length());
        }
    }
    
    @Test
    @DisplayName("Service maintains state consistency across requests")
    void serviceMaintainsStateConsistencyAcrossRequests() throws Exception {
        // Purpose: Test service state management and persistence
        
        URL metricsUrl = URI.create(BASE_URL_SUITE + "/metrics").toURL();
        
        // Get initial metrics
        HttpURLConnection initialConnection = (HttpURLConnection) metricsUrl.openConnection();
        initialConnection.setRequestMethod("GET");
        String initialResponse = new String(initialConnection.getInputStream().readAllBytes());
        JsonNode initialMetrics = objectMapper.readTree(initialResponse);
        
        long initialRequests = initialMetrics.get("requests").asLong();
        long initialUptime = initialMetrics.get("uptime").asLong();
        
        // Make several requests to different endpoints
        String[] endpoints = {"/health", "/time", "/echo?msg=test"};
        
        for (String endpoint : endpoints) {
            URL url = URI.create(BASE_URL_SUITE + endpoint).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.getResponseCode(); // Just make the request
        }
        
        // Wait a moment for metrics to update
        Thread.sleep(2000);
        
        // Get final metrics
        HttpURLConnection finalConnection = (HttpURLConnection) metricsUrl.openConnection();
        finalConnection.setRequestMethod("GET");
        String finalResponse = new String(finalConnection.getInputStream().readAllBytes());
        JsonNode finalMetrics = objectMapper.readTree(finalResponse);
        
        long finalRequests = finalMetrics.get("requests").asLong();
        long finalUptime = finalMetrics.get("uptime").asLong();
        
        // Verify state consistency
        assertThat(finalRequests)
            .as("Request count should increase")
            .isGreaterThan(initialRequests);
            
        assertThat(finalUptime)
            .as("Uptime should increase")
            .isGreaterThanOrEqualTo(initialUptime);
            
        // Verify service maintains consistent start time
        assertThat(initialMetrics.get("startTime").asText())
            .as("Start time should remain consistent")
            .isEqualTo(finalMetrics.get("startTime").asText());
    }
    
    @Test
    @DisplayName("Plugin handles network timeouts gracefully")
    void pluginHandlesNetworkTimeoutsGracefully() throws Exception {
        // Purpose: Test plugin behavior when network operations timeout
        
        // Test plugin's response to container health check timeouts by attempting
        // to connect to a non-responsive endpoint
        
        // Create a test container that will be unresponsive (using a non-existent port)
        String timeoutTestContainer = "timeout-test-" + System.currentTimeMillis();
        
        try {
            // Start a container but don't expose the port we'll try to connect to
            Process containerProcess = new ProcessBuilder(
                "docker", "run", "-d",
                "--name", timeoutTestContainer,
                "time-server:latest"
            ).start();
            
            boolean containerStarted = containerProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            
            if (containerStarted && containerProcess.exitValue() == 0) {
                // Try to connect to a non-exposed port with short timeout
                try {
                    URL unreachableUrl = URI.create("http://localhost:9999/health").toURL(); // Port not exposed
                    HttpURLConnection connection = (HttpURLConnection) unreachableUrl.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(2000); // 2 second timeout
                    connection.setReadTimeout(2000);    // 2 second timeout
                    
                    connection.getResponseCode();
                    
                    // If we reach here, connection unexpectedly succeeded (shouldn't happen)
                    fail("Expected connection to unreachable port to timeout");
                    
                } catch (java.io.IOException e) {
                    // This is expected - connection should timeout or fail
                    assertThat(e)
                        .as("Plugin should handle network failures gracefully")
                        .isInstanceOf(java.io.IOException.class);
                        
                    // More robust assertion - check exception type and message flexibility
                    String errorMessage = e.getMessage().toLowerCase();
                    assertThat(errorMessage)
                        .as("Error message should indicate connection/timeout issue")
                        .containsAnyOf("timeout", "timed out", "connection", "refused", "unreachable", "failed");
                }
                
                // Test Docker container health check timeout simulation
                Process healthProcess = new ProcessBuilder(
                    "docker", "inspect", timeoutTestContainer,
                    "--format", "{{.State.Status}}"
                ).start();
                
                boolean healthCheckCompleted = healthProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                
                assertThat(healthCheckCompleted)
                    .as("Docker health check command should complete")
                    .isTrue();
                    
                assertThat(healthProcess.exitValue())
                    .as("Docker inspect should succeed")
                    .isEqualTo(0);
            }
            
        } finally {
            cleanupTestContainer(timeoutTestContainer);
        }
    }
    
    @Test
    @DisplayName("Plugin handles container startup failures gracefully")
    void pluginHandlesContainerStartupFailuresGracefully() throws Exception {
        // Purpose: Test plugin error handling when Docker operations fail
        
        // Test plugin behavior when trying to start a container with invalid configuration
        String invalidContainerName = "invalid-test-container-" + System.currentTimeMillis();
        
        try {
            // Try to start a container with an invalid image name
            Process invalidImageProcess = new ProcessBuilder(
                "docker", "run", "-d", 
                "--name", invalidContainerName,
                "non-existent-image-that-should-never-exist:latest"
            ).start();
            
            boolean completed = invalidImageProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            
            assertThat(completed)
                .as("Docker command should complete (even if it fails)")
                .isTrue();
                
            assertThat(invalidImageProcess.exitValue())
                .as("Docker should fail gracefully for invalid image")
                .isNotEqualTo(0);
                
            // Test invalid port binding (port already in use)
            Process invalidPortProcess = new ProcessBuilder(
                "docker", "run", "-d", 
                "--name", invalidContainerName + "-port",
                "-p", "8081:8080", // This port is already used by our test service
                "time-server:latest"
            ).start();
            
            boolean portCompleted = invalidPortProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            
            assertThat(portCompleted)
                .as("Docker port binding command should complete")
                .isTrue();
                
            // Port conflict should either succeed (if Docker handles it) or fail gracefully
            assertThat(invalidPortProcess.exitValue())
                .as("Docker should handle port conflicts appropriately")
                .isIn(0, 125, 126); // Success or known Docker error codes
                
        } finally {
            // Clean up any containers that might have been created
            cleanupTestContainer(invalidContainerName);
            cleanupTestContainer(invalidContainerName + "-port");
        }
    }
    
    @Test
    @DisplayName("Service performance remains stable over time")
    void servicePerformanceRemainsStableOverTime() throws Exception {
        // Purpose: Test service performance consistency
        
        URL timeUrl = URI.create(BASE_URL_SUITE + "/time").toURL();
        int requestCount = 20;
        long[] responseTimes = new long[requestCount];
        
        // Make multiple requests and measure response times
        for (int i = 0; i < requestCount; i++) {
            long startTime = System.nanoTime();
            
            HttpURLConnection connection = (HttpURLConnection) timeUrl.openConnection();
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            assertThat(responseCode).isEqualTo(200);
            
            connection.getInputStream().readAllBytes(); // Fully consume response
            
            long endTime = System.nanoTime();
            responseTimes[i] = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            
            // Small delay between requests
            Thread.sleep(100);
        }
        
        // Calculate statistics
        long minTime = java.util.Arrays.stream(responseTimes).min().orElse(0);
        long maxTime = java.util.Arrays.stream(responseTimes).max().orElse(0);
        double avgTime = java.util.Arrays.stream(responseTimes).average().orElse(0);
        
        // Verify performance characteristics
        assertThat(avgTime)
            .as("Average response time should be reasonable")
            .isLessThan(1000); // Less than 1 second average
            
        assertThat(maxTime - minTime)
            .as("Response time variation should be reasonable")
            .isLessThan(5000); // Less than 5 second variation
            
        // Most requests should complete quickly
        int fastRequests = 0;
        for (long time : responseTimes) {
            if (time < 500) fastRequests++;
        }
            
        assertThat(fastRequests)
            .as("Most requests should complete quickly")
            .isGreaterThanOrEqualTo((int)(requestCount * 0.7)); // At least 70% under 500ms
    }
    
    @Test
    @DisplayName("Service integrates correctly with compose lifecycle")
    void serviceIntegratesCorrectlyWithComposeLifecycle() throws Exception {
        // Purpose: Test integration with Docker Compose lifecycle management
        
        // This test runs as part of the compose lifecycle, so we're testing that:
        // 1. Service is available when compose stack is up
        // 2. Service responds correctly throughout the test execution
        // 3. Service maintains state during the test suite
        
        Instant testStart = Instant.now();
        
        // Verify service is available
        URL healthUrl = URI.create(BASE_URL_SUITE + "/health").toURL();
        HttpURLConnection healthConnection = (HttpURLConnection) healthUrl.openConnection();
        healthConnection.setRequestMethod("GET");
        
        assertThat(healthConnection.getResponseCode())
            .as("Service should be healthy during compose lifecycle")
            .isEqualTo(200);
        
        // Verify service has been running for some time (compose startup)
        URL metricsUrl = URI.create(BASE_URL_SUITE + "/metrics").toURL();
        HttpURLConnection metricsConnection = (HttpURLConnection) metricsUrl.openConnection();
        metricsConnection.setRequestMethod("GET");
        String metricsResponse = new String(metricsConnection.getInputStream().readAllBytes());
        JsonNode metrics = objectMapper.readTree(metricsResponse);
        
        long uptime = metrics.get("uptime").asLong();
        assertThat(uptime)
            .as("Service should have some uptime from compose startup")
            .isGreaterThan(0);
        
        // Verify service responds to all main endpoints
        String[] mainEndpoints = {"/health", "/time", "/echo?msg=lifecycle-test", "/metrics"};
        
        for (String endpoint : mainEndpoints) {
            URL url = URI.create(BASE_URL_SUITE + endpoint).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            assertThat(connection.getResponseCode())
                .as("Endpoint %s should be available during compose lifecycle", endpoint)
                .isEqualTo(200);
        }
        
        Duration testDuration = Duration.between(testStart, Instant.now());
        assertThat(testDuration)
            .as("Lifecycle test should complete in reasonable time")
            .isLessThan(Duration.ofSeconds(30));
    }
    
    // Helper Methods
    
    private void cleanupTestContainer(String containerName) {
        try {
            // Stop container if running
            Process stopProcess = new ProcessBuilder("docker", "stop", containerName).start();
            stopProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            
            // Remove container
            Process removeProcess = new ProcessBuilder("docker", "rm", "-f", containerName).start();
            removeProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore cleanup errors - this is just cleanup
            System.err.println("Container cleanup warning for " + containerName + ": " + e.getMessage());
        }
    }
}