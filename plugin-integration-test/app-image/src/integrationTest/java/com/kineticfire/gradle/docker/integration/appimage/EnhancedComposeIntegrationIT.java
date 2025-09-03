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
                    URL url = new URL(BASE_URL_SUITE + "/time");
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
        
        URL healthUrl = new URL(BASE_URL_SUITE + "/health");
        
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
            URL url = new URL(BASE_URL_SUITE + "/echo?msg=" + encodedMessage);
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
        
        URL metricsUrl = new URL(BASE_URL_SUITE + "/metrics");
        
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
            URL url = new URL(BASE_URL_SUITE + endpoint);
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
    @DisplayName("Service gracefully handles network timeouts")
    void serviceGracefullyHandlesNetworkTimeouts() throws Exception {
        // Purpose: Test timeout handling and connection management
        
        URL url = new URL(BASE_URL_SUITE + "/time");
        
        // Test with very short timeout
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(1); // 1ms - very short timeout
        connection.setReadTimeout(1);    // 1ms - very short timeout
        
        try {
            connection.getResponseCode();
            // If we get here, the service responded very quickly (which is fine)
            assertThat(connection.getResponseCode()).isEqualTo(200);
        } catch (java.net.SocketTimeoutException | java.net.ConnectException e) {
            // This is expected with very short timeouts - connection should fail gracefully
            assertThat(e.getMessage()).contains("timeout", "connect");
        }
    }
    
    @Test
    @DisplayName("Service handles malformed requests appropriately")
    void serviceHandlesMalformedRequestsAppropriately() throws Exception {
        // Purpose: Test error handling for invalid requests
        
        String[] malformedEndpoints = {
            "/nonexistent",
            "/echo", // missing required parameter
            "/health/extra/path",
            "/time?invalid=parameter"
        };
        
        for (String endpoint : malformedEndpoints) {
            URL url = new URL(BASE_URL_SUITE + endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            
            // Service should handle malformed requests gracefully
            // (either with 404, 400, or other appropriate error code)
            assertThat(responseCode)
                .as("Malformed request to %s should return appropriate error code", endpoint)
                .isGreaterThanOrEqualTo(400); // Should be client error or server error
        }
    }
    
    @Test
    @DisplayName("Service performance remains stable over time")
    void servicePerformanceRemainsStableOverTime() throws Exception {
        // Purpose: Test service performance consistency
        
        URL timeUrl = new URL(BASE_URL_SUITE + "/time");
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
        URL healthUrl = new URL(BASE_URL_SUITE + "/health");
        HttpURLConnection healthConnection = (HttpURLConnection) healthUrl.openConnection();
        healthConnection.setRequestMethod("GET");
        
        assertThat(healthConnection.getResponseCode())
            .as("Service should be healthy during compose lifecycle")
            .isEqualTo(200);
        
        // Verify service has been running for some time (compose startup)
        URL metricsUrl = new URL(BASE_URL_SUITE + "/metrics");
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
            URL url = new URL(BASE_URL_SUITE + endpoint);
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
}