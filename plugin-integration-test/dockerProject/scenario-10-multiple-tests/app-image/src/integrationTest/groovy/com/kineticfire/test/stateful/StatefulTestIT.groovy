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

package com.kineticfire.test.stateful

import io.restassured.RestAssured
import spock.lang.Specification

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * Integration Test: dockerProject Scenario 10 - Stateful Tests (METHOD lifecycle)
 *
 * This test class is part of the 'statefulTests' configuration in the tests {} DSL.
 * It demonstrates METHOD lifecycle mode where containers are restarted for each
 * test method, providing isolation between tests.
 *
 * METHOD lifecycle is ideal for:
 * - Tests that modify application state
 * - Tests requiring clean state for each execution
 * - Tests that might interfere with each other
 * - When test isolation is more important than performance
 *
 * The test flow:
 * 1. Container starts fresh (before each test method)
 * 2. Single test method runs
 * 3. Container stops (after each test method)
 * 4. Repeat for next test method
 *
 * Package: com.kineticfire.test.stateful.** (matches testClasses pattern)
 * Port: 9311 (per README conventions)
 */
class StatefulTestIT extends Specification {

    def setupSpec() {
        // Port 9311 is allocated for statefulTests configuration
        def port = System.getProperty('PORT', '9311') as int
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = port

        println "=== dockerProject Scenario 10: Stateful Tests (METHOD lifecycle) ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "This test uses METHOD lifecycle - container restarted for each test method"
    }

    def cleanupSpec() {
        // Force cleanup in case tests fail
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME', 'project-scenario10-stateful')
        try {
            println "=== Cleaning up Docker Compose stack: ${projectName} ==="
            def process = ['docker', 'compose', '-p', projectName, 'down', '-v'].execute()
            process.waitFor()
        } catch (Exception e) {
            println "Warning: Cleanup failed: ${e.message}"
        }
    }

    def "health endpoint returns healthy status with fresh container"() {
        expect: "the health endpoint should return healthy status from fresh container"
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
    }

    def "time endpoint returns time from fresh container"() {
        expect: "the time endpoint should return valid time from fresh container"
        given()
            .when()
            .get("/time")
            .then()
            .statusCode(200)
            .body("time", notNullValue())
            .body("timezone", equalTo("UTC"))
            .body("epoch", greaterThan(0))
    }

    def "echo endpoint works in isolated environment"() {
        expect: "the echo endpoint should work in isolated container"
        given()
            .queryParam("msg", "stateful-test")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo("stateful-test"))
            .body("length", equalTo(13))
            .body("uppercase", equalTo("STATEFUL-TEST"))
    }

    def "metrics endpoint shows fresh start"() {
        expect: "the metrics endpoint should show metrics from fresh container"
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
