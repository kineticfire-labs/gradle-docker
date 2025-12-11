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
import spock.lang.Stepwise

import static io.restassured.RestAssured.given
import static org.hamcrest.Matchers.*

/**
 * Integration Test: dockerProject Scenario 5 - ContextDir Mode
 *
 * This test verifies the dockerProject DSL with contextDir mode where the
 * Docker build context is a pre-existing directory containing the Dockerfile
 * and all required files.
 *
 * Key characteristics:
 * - Uses contextDir instead of jarFrom
 * - Docker context is user-managed (not auto-generated)
 * - Dockerfile is in the context directory itself
 * - Application JAR is copied to context by prepareContext task
 *
 * Test Strategy:
 * - Verify container is healthy and responding
 * - Test all HTTP endpoints to confirm correct image was built
 * - Verify the application runs correctly from contextDir-based build
 */
@Stepwise
class ProjectContextDirIT extends Specification {

    def setupSpec() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9304

        println "=== dockerProject Integration Test: ContextDir Mode ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "Build mode: contextDir (pre-existing Docker context)"
    }

    def "container should be healthy"() {
        expect: "health endpoint returns healthy status"
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))

        println "Health check passed - container is healthy"
    }

    def "should return current time from /time endpoint"() {
        expect: "time endpoint returns valid timestamp"
        given()
            .when()
            .get("/time")
            .then()
            .statusCode(200)
            .body("time", notNullValue())
            .body("timezone", equalTo("UTC"))
            .body("epoch", greaterThan(0))

        println "Time endpoint working correctly"
    }

    def "should echo message from /echo endpoint"() {
        given: "a test message"
        def testMessage = "contextDir-mode-test"

        expect: "echo endpoint returns the same message"
        given()
            .queryParam("msg", testMessage)
            .when()
            .get("/echo")
            .then()
            .statusCode(200)
            .body("echo", equalTo(testMessage))
            .body("length", equalTo(testMessage.length()))
            .body("uppercase", equalTo(testMessage.toUpperCase()))

        println "Echo endpoint working correctly"
    }

    def "should return metrics with request count"() {
        expect: "metrics endpoint returns valid data"
        given()
            .when()
            .get("/metrics")
            .then()
            .statusCode(200)
            .body("requests", greaterThanOrEqualTo(0))
            .body("uptime", greaterThanOrEqualTo(0))
            .body("startTime", notNullValue())

        println "Metrics endpoint working correctly"
    }
}
