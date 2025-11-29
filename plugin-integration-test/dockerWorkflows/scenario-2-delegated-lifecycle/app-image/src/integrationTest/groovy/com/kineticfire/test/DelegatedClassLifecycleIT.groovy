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

import io.restassured.RestAssured
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * Integration Test: dockerWorkflows Scenario 2 - Delegated Class Lifecycle
 *
 * This test demonstrates the 'class' lifecycle mode where containers persist across
 * all test methods within the class. The testIntegration extension manages the compose
 * lifecycle (composeUp before first test, composeDown after last test), while the
 * dockerWorkflows pipeline uses delegateStackManagement=true to skip its own compose calls.
 *
 * The test flow:
 * 1. testIntegration calls composeUp before this class runs
 * 2. All test methods share the SAME container instance
 * 3. testIntegration calls composeDown after this class completes
 * 4. dockerWorkflows pipeline's test step delegates lifecycle to testIntegration
 *
 * Key verification:
 * - Same container ID across all test methods (class lifecycle)
 * - Container persists between test methods
 * - State modifications persist across tests (proving same container)
 */
// NOTE: We do NOT use @ComposeUp here because the Gradle task dependencies
// (composeUp/composeDown) are managed by testIntegration.usesCompose() in build.gradle.
// The @ComposeUp annotation is for tests that need Spock extension-based lifecycle management.
@Stepwise
class DelegatedClassLifecycleIT extends Specification {

    @Shared
    String firstTestContainerId = null

    @Shared
    String echoTestMessage = "class-lifecycle-test-${System.currentTimeMillis()}"

    def setupSpec() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9201

        println "=== dockerWorkflows Integration Test: Delegated Class Lifecycle ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "Container lifecycle: CLASS (same container for all tests)"
    }

    def "first test method verifies container is running and records state"() {
        when: "we call the health endpoint and get metrics"
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

        def startTime = metricsResponse.jsonPath().getString("startTime")
        firstTestContainerId = startTime  // Record for comparison in later tests
        println "First test: Recorded container start time: ${firstTestContainerId}"

        given()
            .queryParam("msg", echoTestMessage)
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo(echoTestMessage))
        println "First test: Echo request successful"

        then: "container is running and responding"
        firstTestContainerId != null
    }

    def "second test method verifies same container instance (class lifecycle)"() {
        when: "we check the health endpoint again"
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))

        and: "we get metrics to verify same container"
        def metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response()

        then: "the container start time matches (same container)"
        def currentStartTime = metricsResponse.jsonPath().getString("startTime")
        println "Second test: Container start time: ${currentStartTime}"
        println "Second test: First test start time: ${firstTestContainerId}"

        currentStartTime == firstTestContainerId
        println "SUCCESS: Same container instance (class lifecycle verified)"

        and: "request count shows previous requests (state persisted)"
        def requestCount = metricsResponse.jsonPath().getInt("requests")
        println "Second test: Request count: ${requestCount}"
        requestCount > 0
    }

    def "third test method confirms container persists across all tests"() {
        when: "we verify container identity one more time"
        def metricsResponse = given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .response()

        then: "still the same container"
        def currentStartTime = metricsResponse.jsonPath().getString("startTime")
        currentStartTime == firstTestContainerId
        println "Third test: Container still matches (start time: ${currentStartTime})"

        and: "uptime has increased (container running continuously)"
        def uptime = metricsResponse.jsonPath().getLong("uptime")
        println "Third test: Container uptime: ${uptime}ms"
        uptime > 0

        and: "request count continues to accumulate"
        def requestCount = metricsResponse.jsonPath().getInt("requests")
        println "Third test: Total requests: ${requestCount}"
        requestCount >= 3  // At least our previous health + metrics calls
    }
}
