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
import io.restassured.RestAssured
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * Integration Test: dockerWorkflows Scenario 8 - Method Lifecycle
 *
 * This test demonstrates the 'method' lifecycle mode where containers are started
 * fresh before each test method and stopped after each test method.
 *
 * Key characteristics:
 * - Uses @ComposeUp annotation which reads system properties set by the pipeline
 * - Each test method gets fresh containers (no state persists between tests)
 * - Container start time will be DIFFERENT for each test method
 * - Request counts reset for each test method
 *
 * The dockerWorkflows pipeline with lifecycle=METHOD:
 * 1. Sets system properties (docker.compose.lifecycle, docker.compose.files, etc.)
 * 2. Skips Gradle composeUp/composeDown tasks
 * 3. The @ComposeUp extension handles compose lifecycle per method
 *
 * Verification strategy:
 * - Record container start time in first test
 * - Verify container start time is DIFFERENT in subsequent tests (proving fresh containers)
 * - Verify request counts start at 0 (proving fresh state)
 */
@ComposeUp  // This reads system properties set by the pipeline's METHOD lifecycle
@Stepwise
class MethodLifecycleIT extends Specification {

    @Shared
    String firstTestStartTime = null

    @Shared
    String secondTestStartTime = null

    def setupSpec() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9207

        println "=== dockerWorkflows Integration Test: Method Lifecycle ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "Container lifecycle: METHOD (fresh container for each test)"
    }

    def "first test method gets fresh container and records start time"() {
        when: "we wait for container to be healthy and get metrics"
        // Give container time to start and become healthy
        sleep(2000)

        def response = given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
            .extract()
            .response()
        println "First test: Health check passed"

        def metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response()

        firstTestStartTime = metricsResponse.jsonPath().getString("startTime")
        def requestCount = metricsResponse.jsonPath().getInt("requests")
        println "First test: Container start time: ${firstTestStartTime}"
        println "First test: Request count: ${requestCount}"

        then: "container is running and request count is low (fresh container)"
        firstTestStartTime != null
        // Fresh container should have low request count (health checks + our calls)
        // The waitForHealthy logic may do 2-3 health checks before our test calls
        requestCount <= 5
    }

    def "second test method gets a DIFFERENT fresh container"() {
        when: "we wait for the new container and get metrics"
        // Give container time to start and become healthy
        sleep(2000)

        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
        println "Second test: Health check passed"

        def metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response()

        secondTestStartTime = metricsResponse.jsonPath().getString("startTime")
        def requestCount = metricsResponse.jsonPath().getInt("requests")
        println "Second test: Container start time: ${secondTestStartTime}"
        println "Second test: Request count: ${requestCount}"
        println "Second test: First test start time was: ${firstTestStartTime}"

        then: "this is a DIFFERENT container (different start time)"
        secondTestStartTime != null
        // KEY ASSERTION: Start time should be DIFFERENT, proving fresh container per method
        secondTestStartTime != firstTestStartTime
        println "SUCCESS: Different container instance (method lifecycle verified)"

        and: "request count is low (fresh container, no persisted state)"
        requestCount <= 5
    }

    def "third test method also gets a fresh container"() {
        when: "we wait for yet another new container"
        // Give container time to start and become healthy
        sleep(2000)

        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
        println "Third test: Health check passed"

        def metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response()

        def thirdTestStartTime = metricsResponse.jsonPath().getString("startTime")
        def requestCount = metricsResponse.jsonPath().getInt("requests")
        println "Third test: Container start time: ${thirdTestStartTime}"
        println "Third test: Request count: ${requestCount}"

        then: "this is a DIFFERENT container from both previous tests"
        thirdTestStartTime != null
        thirdTestStartTime != firstTestStartTime
        thirdTestStartTime != secondTestStartTime
        println "SUCCESS: Third container also fresh (different from first: ${firstTestStartTime}, second: ${secondTestStartTime})"

        and: "request count is low (fresh container)"
        requestCount <= 5
    }
}
