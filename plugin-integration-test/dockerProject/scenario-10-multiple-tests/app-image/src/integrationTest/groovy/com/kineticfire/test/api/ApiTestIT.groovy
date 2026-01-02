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

package com.kineticfire.test.api

import io.restassured.RestAssured
import spock.lang.Specification

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * Integration Test: dockerProject Scenario 10 - API Tests (CLASS lifecycle)
 *
 * This test class is part of the 'apiTests' configuration in the tests {} DSL.
 * It demonstrates CLASS lifecycle mode where containers are started once per
 * test class and shared across all test methods.
 *
 * CLASS lifecycle is ideal for:
 * - Stateless API tests
 * - Read-only operations
 * - Tests that don't modify application state
 * - Better performance (containers not restarted between tests)
 *
 * The test flow:
 * 1. Container starts once (before all tests in this class)
 * 2. All test methods run sharing the same container
 * 3. Container stops after all tests complete
 *
 * Package: com.kineticfire.test.api.** (matches testClasses pattern)
 * Port: 9310 (per README conventions)
 */
class ApiTestIT extends Specification {

    def setupSpec() {
        // Port 9310 is allocated for apiTests configuration
        def port = System.getProperty('PORT', '9310') as int
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = port

        println "=== dockerProject Scenario 10: API Tests (CLASS lifecycle) ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "This test uses CLASS lifecycle - container shared across test methods"
    }

    def cleanupSpec() {
        // Force cleanup in case tests fail
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME', 'project-scenario10-api')
        try {
            println "=== Cleaning up Docker Compose stack: ${projectName} ==="
            def process = ['docker', 'compose', '-p', projectName, 'down', '-v'].execute()
            process.waitFor()
        } catch (Exception e) {
            println "Warning: Cleanup failed: ${e.message}"
        }
    }

    def "health endpoint returns healthy status"() {
        expect: "the health endpoint should return healthy status"
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
    }

    def "time endpoint returns current time"() {
        expect: "the time endpoint should return valid time data"
        given()
            .when()
            .get("/time")
            .then()
            .statusCode(200)
            .body("time", notNullValue())
            .body("timezone", equalTo("UTC"))
            .body("epoch", greaterThan(0))
    }

    def "echo endpoint echoes message"() {
        expect: "the echo endpoint should echo the message"
        given()
            .queryParam("msg", "api-test-message")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo("api-test-message"))
            .body("length", equalTo(16))
            .body("uppercase", equalTo("API-TEST-MESSAGE"))
    }

    def "metrics endpoint returns metrics"() {
        expect: "the metrics endpoint should return valid metrics"
        given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .body("uptime", greaterThanOrEqualTo(0))
            .body("requests", greaterThanOrEqualTo(0))
            .body("startTime", notNullValue())
    }
}
