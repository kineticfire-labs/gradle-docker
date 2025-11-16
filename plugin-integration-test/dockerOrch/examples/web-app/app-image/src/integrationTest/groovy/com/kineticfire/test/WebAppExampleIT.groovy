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

package com.kineticfire.test

import com.kineticfire.gradle.docker.spock.ComposeUp
import com.kineticfire.gradle.docker.spock.LifecycleMode
import spock.lang.Specification
import spock.lang.Shared
import groovy.json.JsonSlurper
import io.restassured.RestAssured
import static io.restassured.RestAssured.given

/**
 * Example: Testing a Web Application with Docker Compose
 *
 * This example demonstrates how to test a Spring Boot REST API using the dockerOrch plugin with CLASS lifecycle.
 *
 * WHY THIS PATTERN?
 * ================
 * - CLASS lifecycle: Containers start ONCE before all tests, stop ONCE after all tests
 * - Faster execution: Avoids restarting containers for each test method
 * - Perfect for read-only tests or tests that don't modify shared state
 * - All tests in this class are independent READ operations (health checks, GET requests)
 *
 * WHEN TO USE:
 * ===========
 * ✅ Testing REST API endpoints (GET requests)
 * ✅ Read-only operations
 * ✅ Performance-critical test suites
 * ✅ When tests don't modify global state
 *
 * WHEN NOT TO USE:
 * ===============
 * ❌ Tests that modify database state (use METHOD lifecycle instead)
 * ❌ Tests that must run in complete isolation
 * ❌ Stateful workflows where order matters (see StatefulWebAppExampleIT instead)
 *
 * The @ComposeUp extension automatically handles:
 * - Starting containers with Docker Compose before all tests (setupSpec)
 * - Waiting for the app to be healthy (waitForHealthy parameter)
 * - Providing port mapping information via COMPOSE_STATE_FILE
 * - Cleaning up containers after all tests complete (cleanupSpec)
 *
 * Your tests focus on:
 * - Testing your application's business logic
 * - Validating API contracts
 * - Checking error handling
 * - Testing concurrent behavior
 *
 * Copy and adapt this example for your own projects!
 */
@ComposeUp(
    stackName = "webAppTest",                                      // Unique name for this stack
    composeFile = "src/integrationTest/resources/compose/web-app.yml",  // Path to compose file
    lifecycle = LifecycleMode.CLASS,                               // Containers start once (faster)
    waitForHealthy = ["web-app"],                                  // Wait for service to be HEALTHY
    timeoutSeconds = 60,                                           // Max wait time for healthy status
    pollSeconds = 2                                                // Check health every 2 seconds
)
class WebAppExampleIT extends Specification {

    // @Shared variables persist across test methods (CLASS lifecycle)
    @Shared String baseUrl

    def setupSpec() {
        // WHY: The @ComposeUp extension provides COMPOSE_STATE_FILE system property
        // This file contains runtime information about the Docker Compose stack:
        // - Service names and their container IDs
        // - Published ports (random host ports mapped to container ports)
        // - Network details
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateFile = new File(stateFilePath)
        def stateData = new JsonSlurper().parse(stateFile)

        // WHY: Extract the host port for the web-app service
        // Docker Compose maps container port 8080 to a RANDOM host port to avoid conflicts
        // We must read the actual assigned port from the state file
        def port = stateData.services['web-app'].publishedPorts[0].host

        baseUrl = "http://localhost:${port}"

        println "=== Testing Web App at ${baseUrl} ==="

        // WHY: Configure RestAssured base URL once for all tests
        // Since this is CLASS lifecycle, all tests share the same baseUrl
        RestAssured.baseURI = baseUrl
    }

    def "should respond to health check endpoint"() {
        when: "we call the /health endpoint"
        def response = given()
            .get("/health")

        then: "we get 200 OK"
        response.statusCode() == 200

        and: "response indicates app is UP"
        response.jsonPath().getString("status") == "UP"
        response.jsonPath().getString("timestamp") != null
    }

    def "should return app information from root endpoint"() {
        when: "we call the root endpoint"
        def response = given()
            .get("/")

        then: "we get 200 OK"
        response.statusCode() == 200

        and: "response contains app information"
        response.jsonPath().getString("message") == "Web App is running"
        response.jsonPath().getString("version") == "1.0.0"
    }

    def "should handle multiple concurrent requests successfully"() {
        given: "we want to test concurrent load"
        def concurrentRequests = 50
        def results = Collections.synchronizedList([])

        when: "we make many concurrent requests"
        def threads = (1..concurrentRequests).collect {
            Thread.start {
                def statusCode = given()
                    .get("/health")
                    .statusCode()
                results.add(statusCode)
            }
        }
        threads*.join()

        then: "all requests should succeed"
        results.size() == concurrentRequests
        results.every { it == 200 }
    }

    def "should return JSON content type"() {
        when: "we call any endpoint"
        def response = given()
            .get("/health")

        then: "content type is JSON"
        response.contentType().contains("application/json")
    }

    def "health endpoint should respond quickly"() {
        when: "we measure response time"
        def startTime = System.currentTimeMillis()
        def response = given()
            .get("/health")
        def responseTime = System.currentTimeMillis() - startTime

        then: "request succeeds"
        response.statusCode() == 200

        and: "response is fast (under 1 second)"
        responseTime < 1000
    }
}
