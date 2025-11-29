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
 * Integration Test: dockerWorkflows Scenario 3 - Delegated Lifecycle (Additional Test)
 *
 * This test demonstrates the delegated stack management pattern using a different port.
 * The Gradle tasks (composeUp/composeDown) manage the compose lifecycle at the test task
 * level, while the dockerWorkflows pipeline uses delegateStackManagement=true to skip
 * its own compose calls.
 *
 * This is essentially a class lifecycle test - containers persist across all test methods
 * in this class. The test verifies that:
 * - Container is accessible and healthy
 * - Same container instance across all tests (class lifecycle)
 * - State persists between test methods
 *
 * NOTE: True per-method lifecycle (fresh container per test method) requires the
 * @ComposeUp Spock annotation, not Gradle task dependencies.
 */
// NOTE: We do NOT use @ComposeUp here because the Gradle task dependencies
// (composeUp/composeDown) are managed by testIntegration.usesCompose() in build.gradle.
@Stepwise
class DelegatedMethodLifecycleIT extends Specification {

    @Shared
    String firstTestStartTime = null

    @Shared
    String secondTestStartTime = null

    def setupSpec() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9202

        println "=== dockerWorkflows Integration Test: Delegated Lifecycle (Scenario 3) ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "Container lifecycle: CLASS (same container for all tests, managed by Gradle tasks)"
    }

    def "first test method verifies container is running and records start time"() {
        when: "we call the health endpoint and get metrics"
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))

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

        then: "container is running and responding"
        firstTestStartTime != null
    }

    def "second test method verifies same container instance (class lifecycle)"() {
        when: "we wait briefly and then check the container"
        Thread.sleep(100)

        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))

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
        println "Second test: First test start time: ${firstTestStartTime}"
        println "Second test: Request count: ${requestCount}"

        then: "the container start time is SAME (class lifecycle)"
        secondTestStartTime == firstTestStartTime
        println "SUCCESS: Same container instance (class lifecycle verified)"
    }

    def "third test confirms container persists across all tests"() {
        when: "we get metrics from this test's container"
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

        then: "the container is SAME as previous tests (class lifecycle)"
        thirdTestStartTime == firstTestStartTime
        thirdTestStartTime == secondTestStartTime
        println "SUCCESS: Container persists across all test methods (class lifecycle verified)"
    }
}
