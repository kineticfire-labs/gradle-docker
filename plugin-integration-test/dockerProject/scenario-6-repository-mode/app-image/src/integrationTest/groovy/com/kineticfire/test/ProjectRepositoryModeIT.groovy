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
import spock.lang.Specification

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * Integration Test: dockerProject Scenario 6 - Repository Mode
 *
 * This test validates that the containerized TimeServerApp is running correctly
 * when built using the repository property (as an alternative to imageName).
 *
 * The dockerProject DSL simplifies the three-DSL approach (docker + dockerTest + dockerWorkflows)
 * into a single block configuration. This scenario specifically tests:
 *
 * - repository property: Alternative to imageName that includes namespace
 *   Example: repository.set('scenario6org/scenario6-app') instead of
 *            imageName.set('scenario6-app') + namespace.set('scenario6org')
 *
 * The test flow (orchestrated by dockerProject):
 * 1. Docker image is built with repository-style name (scenario6org/scenario6-app)
 * 2. Image is tagged with 'latest' and '1.0.0'
 * 3. Container is started (compose file with waitForHealthy)
 * 4. These tests run (integrationTest)
 * 5. Container is stopped
 * 6. On success, 'tested' tag is added (onSuccess.additionalTags)
 */
class ProjectRepositoryModeIT extends Specification {

    def setupSpec() {
        // The compose file exposes port 8080 to 9306
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9306

        println "=== dockerProject Integration Test: Repository Mode ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "Image name: scenario6org/scenario6-app (repository property)"
    }

    def cleanupSpec() {
        // Force cleanup in case tests fail
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME', 'project-scenario6-test')
        try {
            println "=== Cleaning up Docker Compose stack: ${projectName} ==="
            def process = ['docker', 'compose', '-p', projectName, 'down', '-v'].execute()
            process.waitFor()
        } catch (Exception e) {
            println "Warning: Cleanup failed: ${e.message}"
        }
    }

    def "health endpoint returns healthy status"() {
        expect:
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
    }

    def "time endpoint returns current time"() {
        expect:
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
        expect:
        given()
            .queryParam("msg", "repository-mode-test")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo("repository-mode-test"))
            .body("length", equalTo(20))
            .body("uppercase", equalTo("REPOSITORY-MODE-TEST"))
    }

    def "metrics endpoint returns uptime"() {
        expect:
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
