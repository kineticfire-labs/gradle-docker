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

/**
 * Integration tests for dockerWorkflows scenario 6: Hooks and Customization.
 *
 * These tests verify the containerized application is working correctly during
 * the pipeline's test phase. The hooks execution is verified by the wrapper
 * task (verifyHooks) which checks for marker files created by each hook.
 *
 * The tests run against port 9205 as allocated for workflow scenario 6.
 */
class HooksIT extends Specification {

    def setupSpec() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9205
    }

    def "health endpoint returns HTTP 200"() {
        when:
        def response = RestAssured.get("/health")

        then:
        response.statusCode() == 200
    }

    def "time endpoint returns HTTP 200"() {
        when:
        def response = RestAssured.get("/time")

        then:
        response.statusCode() == 200
    }

    def "time endpoint returns time data"() {
        when:
        def response = RestAssured.get("/time")

        then:
        response.statusCode() == 200
        response.contentType().contains("application/json")
        response.jsonPath().getString("time") != null
    }
}
