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
 * Integration Test: dockerProject Scenario 9 - Configuration Cache Verification
 *
 * This test validates that the containerized TimeServerApp is running correctly
 * when built using the dockerProject DSL with configuration cache enabled.
 *
 * Configuration Cache Testing:
 * This scenario is designed to verify that the dockerProject DSL works correctly
 * with Gradle's configuration cache. The key verification points are:
 *
 * 1. First run: Pipeline executes successfully and stores configuration
 * 2. Second run: Pipeline reuses cached configuration (no recalculation)
 * 3. All tasks execute correctly from cached state
 *
 * To manually verify configuration cache:
 * ```bash
 * # First run (stores cache)
 * ./gradlew --configuration-cache :dockerProject:scenario-9-config-cache:app-image:runProjectscenario9appPipeline
 *
 * # Second run (should reuse cache - look for "Reusing configuration cache")
 * ./gradlew --configuration-cache :dockerProject:scenario-9-config-cache:app-image:runProjectscenario9appPipeline
 * ```
 *
 * The test flow (orchestrated by dockerProject):
 * 1. Docker image is built (project-scenario9-app)
 * 2. Image is tagged with 'latest'
 * 3. Container is started (compose file with waitForHealthy)
 * 4. These tests run (integrationTest)
 * 5. Container is stopped
 * 6. On success, 'tested' tag is added
 */
class ProjectConfigCacheIT extends Specification {

    def setupSpec() {
        // The compose file exposes port 8080 to 9309
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9309

        println "=== dockerProject Integration Test: Configuration Cache ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "This test verifies configuration cache compatibility"
    }

    def cleanupSpec() {
        // Force cleanup in case tests fail
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME', 'project-scenario9-test')
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
            .queryParam("msg", "config-cache-test")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo("config-cache-test"))
            .body("length", equalTo(17))
            .body("uppercase", equalTo("CONFIG-CACHE-TEST"))
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
