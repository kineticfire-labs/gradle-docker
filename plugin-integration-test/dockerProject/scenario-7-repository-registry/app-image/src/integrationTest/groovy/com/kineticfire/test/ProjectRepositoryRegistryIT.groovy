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
 * Integration Test: dockerProject Scenario 7 - Repository Mode with Registry Publishing
 *
 * This test validates that the containerized TimeServerApp is running correctly
 * when built using the repository property and published to a private registry.
 *
 * The dockerProject DSL simplifies the three-DSL approach (docker + dockerTest + dockerWorkflows)
 * into a single block configuration. This scenario specifically tests:
 *
 * - repository property: Alternative to imageName that includes namespace
 * - registry property: Target registry for the image
 * - publishRegistry/publishTags: Publishing to private registry on success
 *
 * The test flow (orchestrated by dockerProject):
 * 1. Docker image is built with repository-style name (scenario7org/scenario7-app)
 * 2. Image is tagged with 'latest' and '1.0.0'
 * 3. Container is started (compose file with waitForHealthy)
 * 4. These tests run (integrationTest)
 * 5. Container is stopped
 * 6. On success:
 *    - 'tested' and 'stable' tags are added
 *    - Image is published to localhost:5037/scenario7org/scenario7-app
 */
class ProjectRepositoryRegistryIT extends Specification {

    def setupSpec() {
        // The compose file exposes port 8080 to 9307
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9307

        println "=== dockerProject Integration Test: Repository + Registry ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "Image name: scenario7org/scenario7-app (repository property)"
        println "Registry: localhost:5037"
    }

    def cleanupSpec() {
        // Force cleanup in case tests fail
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME', 'project-scenario7-test')
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
            .queryParam("msg", "repo-registry-test")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo("repo-registry-test"))
            .body("length", equalTo(18))
            .body("uppercase", equalTo("REPO-REGISTRY-TEST"))
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
