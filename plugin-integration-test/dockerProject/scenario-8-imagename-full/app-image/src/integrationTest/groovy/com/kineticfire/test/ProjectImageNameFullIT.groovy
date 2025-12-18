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
 * Integration Test: dockerProject Scenario 8 - Full ImageName Mode
 *
 * This test validates that the containerized TimeServerApp is running correctly
 * when built using the full imageName mode with registry, namespace, multiple
 * buildArgs, and multiple labels.
 *
 * The dockerProject DSL simplifies the three-DSL approach (docker + dockerTest + dockerWorkflows)
 * into a single block configuration. This scenario specifically tests:
 *
 * - imageName + registry + namespace: Full image naming components
 * - buildArgs: Multiple build arguments (4)
 * - labels: Multiple OCI standard labels (4)
 * - saveFile: Save image to tar.gz
 * - publishRegistry/publishNamespace/publishTags: Full publish configuration
 *
 * The test flow (orchestrated by dockerProject):
 * 1. Docker image is built: localhost:5038/scenario8ns/scenario8-app
 * 2. Image is tagged with 'latest', '1.0.0', 'dev'
 * 3. Container is started (compose file with waitForHealthy)
 * 4. These tests run (integrationTest)
 * 5. Container is stopped
 * 6. On success:
 *    - 'tested' and 'verified' tags are added
 *    - Image is saved to build/images/scenario8-app.tar.gz
 *    - Image is published to localhost:5038/scenario8ns/scenario8-app
 */
class ProjectImageNameFullIT extends Specification {

    def setupSpec() {
        // The compose file exposes port 8080 to 9308
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9308

        println "=== dockerProject Integration Test: Full ImageName Mode ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "Image: localhost:5038/scenario8ns/scenario8-app"
        println "Registry: localhost:5038"
        println "Namespace: scenario8ns"
    }

    def cleanupSpec() {
        // Force cleanup in case tests fail
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME', 'project-scenario8-test')
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
            .queryParam("msg", "full-imagename-test")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo("full-imagename-test"))
            .body("length", equalTo(19))
            .body("uppercase", equalTo("FULL-IMAGENAME-TEST"))
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
