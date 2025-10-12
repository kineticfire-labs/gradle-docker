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

import com.kineticfire.gradle.docker.junit.DockerComposeClassExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Example: JUnit 5 with CLASS Lifecycle
 *
 * <p>Demonstrates how to use DockerComposeClassExtension for CLASS-level lifecycle:
 * <ul>
 *   <li>Containers start once before all tests (@BeforeAll)</li>
 *   <li>All tests run against the same containers</li>
 *   <li>Containers stop once after all tests (@AfterAll)</li>
 * </ul>
 *
 * <p>This example shows how to test a Spring Boot REST API using the plugin's JUnit 5 extension.
 *
 * <p>The extension handles:
 * <ul>
 *   <li>Building the Docker image from your JAR</li>
 *   <li>Starting containers with Docker Compose</li>
 *   <li>Waiting for the app to be healthy</li>
 *   <li>Providing port mapping information</li>
 *   <li>Cleaning up after tests</li>
 * </ul>
 *
 * <p>Your tests focus on:
 * <ul>
 *   <li>Testing your application's business logic</li>
 *   <li>Validating API contracts</li>
 *   <li>Checking error handling</li>
 *   <li>Testing concurrent behavior</li>
 * </ul>
 *
 * <p><strong>Copy and adapt this example for your own projects!</strong></p>
 */
@ExtendWith(DockerComposeClassExtension.class)
class WebAppJUnit5ClassIT {

    private static String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setupAll() throws IOException {
        // Extension provides COMPOSE_STATE_FILE system property
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        File stateFile = new File(stateFilePath);

        // Parse the state file to get container information
        JsonNode stateData = objectMapper.readTree(stateFile);

        // Extract the host port for the web-app service
        // The plugin maps container port 8080 to a random host port
        int port = stateData.get("services")
            .get("web-app")
            .get("publishedPorts")
            .get(0)
            .get("host")
            .asInt();

        baseUrl = "http://localhost:" + port;

        System.out.println("=== Testing Web App at " + baseUrl + " ===");

        // Configure RestAssured base URL
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void shouldRespondToHealthCheckEndpoint() {
        Response response = given()
            .get("/health");

        assertEquals(200, response.statusCode(), "Expected 200 OK from health endpoint");
        assertEquals("UP", response.jsonPath().getString("status"), "Expected status UP");
        assertNotNull(response.jsonPath().getString("timestamp"), "Expected timestamp in response");
    }

    @Test
    void shouldReturnAppInformationFromRootEndpoint() {
        Response response = given()
            .get("/");

        assertEquals(200, response.statusCode(), "Expected 200 OK from root endpoint");
        assertEquals("Web App is running", response.jsonPath().getString("message"));
        assertEquals("1.0.0", response.jsonPath().getString("version"));
    }

    @Test
    void shouldHandleMultipleConcurrentRequestsSuccessfully() {
        int concurrentRequests = 50;

        // Create list of async requests
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        IntStream.range(0, concurrentRequests).forEach(i -> {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                return given()
                    .get("/health")
                    .statusCode();
            });
            futures.add(future);
        });

        // Wait for all requests to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        allFutures.join();

        // Verify all succeeded
        List<Integer> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        assertEquals(concurrentRequests, results.size(), "Expected all requests to complete");
        assertTrue(results.stream().allMatch(status -> status == 200),
            "Expected all requests to return 200 OK");
    }

    @Test
    void shouldReturnJsonContentType() {
        Response response = given()
            .get("/health");

        assertTrue(response.contentType().contains("application/json"),
            "Expected application/json content type");
    }

    @Test
    void healthEndpointShouldRespondQuickly() {
        long startTime = System.currentTimeMillis();

        Response response = given()
            .get("/health");

        long responseTime = System.currentTimeMillis() - startTime;

        assertEquals(200, response.statusCode(), "Expected 200 OK");
        assertTrue(responseTime < 1000,
            "Expected response time under 1 second, got " + responseTime + "ms");
    }
}
