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

import spock.lang.Specification
import io.restassured.RestAssured

import static io.restassured.RestAssured.given

/**
 * Integration tests for Complex Success scenario.
 *
 * This test class verifies the application works correctly. When tests pass,
 * the pipeline will execute all success operations:
 * - Apply additional tags ('verified', 'stable')
 * - Save image to tar.gz file
 *
 * Test Environment:
 * - Port: 9204 (workflow scenario 5)
 * - Image: workflow-scenario5-app:latest
 */
class ComplexSuccessIT extends Specification {

    def setupSpec() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9204
    }

    /**
     * Verify the health endpoint returns HTTP 200.
     * This basic smoke test must pass for success operations to execute.
     */
    def "health endpoint returns HTTP 200"() {
        expect:
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
    }

    /**
     * Verify the time endpoint is serving requests correctly.
     * This confirms the application is fully functional.
     */
    def "time endpoint returns HTTP 200"() {
        expect:
        given()
            .when()
            .get("/time")
            .then()
            .statusCode(200)
    }

    /**
     * Verify the time endpoint returns expected content.
     * Additional validation to ensure the app is working correctly.
     */
    def "time endpoint returns time data"() {
        expect:
        given()
            .when()
            .get("/time")
            .then()
            .statusCode(200)
            .body("timezone", org.hamcrest.Matchers.equalTo("UTC"))
    }
}
