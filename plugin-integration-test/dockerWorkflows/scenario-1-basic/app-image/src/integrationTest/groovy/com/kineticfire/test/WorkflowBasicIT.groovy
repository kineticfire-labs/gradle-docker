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
 * Integration Test: dockerWorkflows Scenario 1 - Basic Workflow
 *
 * This test validates that the containerized TimeServerApp is running correctly.
 * The test is executed as part of the dockerWorkflows pipeline to verify the app works.
 *
 * The test flow (orchestrated by dockerWorkflows):
 * 1. Docker image is built (dockerBuildWorkflowBasicApp)
 * 2. Container is started (composeUpWorkflowTest)
 * 3. These tests run (integrationTest)
 * 4. Container is stopped (composeDownWorkflowTest)
 * 5. On success, 'tested' tag is added (onTestSuccess)
 */
class WorkflowBasicIT extends Specification {

    def setupSpec() {
        // The compose file exposes port 8080 to 9200
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9200

        println "=== dockerWorkflows Integration Test: Basic Workflow ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
    }

    def cleanupSpec() {
        // Force cleanup in case tests fail
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME', 'workflow-scenario1-test')
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
            .queryParam("msg", "workflow-test")
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo("workflow-test"))
            .body("length", equalTo(13))
            .body("uppercase", equalTo("WORKFLOW-TEST"))
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
