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
 * Integration tests for Multiple Pipelines scenario.
 *
 * This test class is used by all three pipelines (dev, staging, prod) to verify
 * the application works correctly. The same tests run regardless of which pipeline
 * is being executed - the difference is what happens AFTER successful tests
 * (different tags applied based on the pipeline).
 *
 * Test Environment:
 * - Port: 9203 (workflow scenario 4)
 * - Image: workflow-scenario4-app:latest
 */
class MultiplePipelinesIT extends Specification {

    def setupSpec() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9203
    }

    /**
     * Verify the health endpoint returns HTTP 200.
     * This is a basic smoke test used by all pipelines.
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
     * This test ensures the app is functional before applying environment-specific tags.
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
