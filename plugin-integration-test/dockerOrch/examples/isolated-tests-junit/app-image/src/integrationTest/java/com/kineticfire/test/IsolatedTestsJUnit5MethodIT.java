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

import com.kineticfire.gradle.docker.junit.DockerComposeMethodExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Example: JUnit 5 with METHOD Lifecycle for Complete Isolation
 *
 * <p>This example demonstrates <strong>when and why to use METHOD lifecycle</strong> for integration testing.
 *
 * <p>METHOD lifecycle means:
 * <ul>
 *   <li>Containers start in @BeforeEach before EACH test method</li>
 *   <li>One test method runs</li>
 *   <li>Containers stop in @AfterEach after EACH test method</li>
 *   <li>State does NOT persist between test methods</li>
 * </ul>
 *
 * <p>This example tests a database-backed user API where:
 * <ol>
 *   <li>Test 1: Create user "alice"</li>
 *   <li>Test 2: Verify user "alice" does NOT exist (fresh database!)</li>
 *   <li>Test 3: Create user "alice" again (should succeed because database is fresh)</li>
 *   <li>Test 4: Create user "bob"</li>
 *   <li>Test 5: Verify user "bob" does NOT exist (fresh database!)</li>
 * </ol>
 *
 * <p>Why METHOD lifecycle is appropriate here:
 * <ul>
 *   <li>Each test needs a completely clean database</li>
 *   <li>Tests must be independent and idempotent (can run in any order)</li>
 *   <li>Demonstrates database isolation (no data persists between tests)</li>
 *   <li>Proves containers restart for each test</li>
 * </ul>
 *
 * <p><strong>Trade-off</strong>: SLOWER than CLASS lifecycle (containers restart for each test)
 * but guarantees isolation.
 *
 * <p>When NOT to use METHOD lifecycle:
 * <ul>
 *   <li>When tests build on each other (workflow: register → login → update)</li>
 *   <li>When startup time is expensive and state isolation isn't critical</li>
 *   <li>When testing read-only operations against the same data</li>
 *   <li>→ For these cases, see the web-app-junit example with CLASS lifecycle</li>
 * </ul>
 *
 * <p><strong>Copy and adapt this example for your own isolated testing scenarios!</strong></p>
 */
@ExtendWith(DockerComposeMethodExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IsolatedTestsJUnit5MethodIT {

    // Instance variables (NOT static!) - fresh for each test
    private String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Static counters to track lifecycle calls
    private static int setupCallCount = 0;
    private static int cleanupCallCount = 0;

    @BeforeEach
    void setupEach() throws IOException {
        setupCallCount++;

        // Extension provides COMPOSE_STATE_FILE system property
        // Fresh state file for EACH test
        String stateFilePath = System.getProperty("COMPOSE_STATE_FILE");
        File stateFile = new File(stateFilePath);
        JsonNode stateData = objectMapper.readTree(stateFile);

        // Extract the host port for the isolated-tests service
        int port = stateData.get("services")
            .get("isolated-tests")
            .get("publishedPorts")
            .get(0)
            .get("host")
            .asInt();

        baseUrl = "http://localhost:" + port;

        System.out.println("=== Test " + setupCallCount + ": Fresh environment at " + baseUrl + " ===");
        System.out.println("=== Using METHOD lifecycle - containers restart for each test ===");

        // Configure RestAssured
        RestAssured.baseURI = baseUrl;
    }

    @AfterEach
    void cleanupEach() {
        cleanupCallCount++;
        System.out.println("=== Test " + cleanupCallCount + " completed, containers stopping ===");
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Should create user alice with fresh database")
    void test1_shouldCreateUserAliceWithFreshDatabase() {
        Response response = given()
            .contentType("application/json")
            .body("{\"username\":\"alice\",\"email\":\"alice@example.com\"}")
            .post("/users");

        assertEquals(200, response.statusCode(), "Expected 200 OK");
        assertEquals("alice", response.jsonPath().getString("username"));
        assertEquals("alice@example.com", response.jsonPath().getString("email"));
        assertEquals(1L, response.jsonPath().getLong("id"));

        System.out.println("✓ Test 1: User 'alice' created with ID " + response.jsonPath().getLong("id"));
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Should NOT find alice (database is fresh)")
    void test2_shouldNotFindAlice() {
        Response response = given()
            .get("/users/alice");

        assertEquals(404, response.statusCode(), "User should NOT exist (database is fresh)");

        System.out.println("✓ Test 2: User 'alice' does NOT exist (proves isolation!)");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Should create user alice again with fresh database")
    void test3_shouldCreateUserAliceAgainWithFreshDatabase() {
        Response response = given()
            .contentType("application/json")
            .body("{\"username\":\"alice\",\"email\":\"alice@example.com\"}")
            .post("/users");

        assertEquals(200, response.statusCode(), "Expected 200 OK (fresh database!)");
        assertEquals("alice", response.jsonPath().getString("username"));
        assertEquals(1L, response.jsonPath().getLong("id"), "ID is 1 again because database is fresh!");

        System.out.println("✓ Test 3: User 'alice' created again with ID 1 (proves fresh database!)");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Should create user bob with fresh database")
    void test4_shouldCreateUserBobWithFreshDatabase() {
        Response response = given()
            .contentType("application/json")
            .body("{\"username\":\"bob\",\"email\":\"bob@example.com\"}")
            .post("/users");

        assertEquals(200, response.statusCode(), "Expected 200 OK");
        assertEquals("bob", response.jsonPath().getString("username"));
        assertEquals("bob@example.com", response.jsonPath().getString("email"));
        assertEquals(1L, response.jsonPath().getLong("id"));

        System.out.println("✓ Test 4: User 'bob' created with ID " + response.jsonPath().getLong("id"));
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Should NOT find bob (database is fresh)")
    void test5_shouldNotFindBob() {
        Response response = given()
            .get("/users/bob");

        assertEquals(404, response.statusCode(), "User should NOT exist (database is fresh)");

        System.out.println("✓ Test 5: User 'bob' does NOT exist (proves isolation!)");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Should have empty database (no users from previous tests)")
    void test6_shouldHaveEmptyDatabase() {
        Response response = given()
            .get("/users");

        assertEquals(200, response.statusCode(), "Expected 200 OK");

        List<Object> users = response.jsonPath().getList("$");
        assertEquals(0, users.size(), "Database should be empty");

        System.out.println("✓ Test 6: Database is empty (no users from previous tests)");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Should respond to health check endpoint")
    void test7_shouldRespondToHealthCheckEndpoint() {
        Response response = given()
            .get("/health");

        assertEquals(200, response.statusCode(), "Expected 200 OK");
        assertEquals("UP", response.jsonPath().getString("status"));
        assertEquals(0L, response.jsonPath().getLong("userCount"), "Fresh database should have 0 users");

        System.out.println("✓ Test 7: Health check passed, userCount = 0 (fresh database)");
    }
}
