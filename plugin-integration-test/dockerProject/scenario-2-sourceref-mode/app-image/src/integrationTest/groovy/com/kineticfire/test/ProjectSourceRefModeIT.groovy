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
 * Integration Test: dockerProject Scenario 2 - SourceRef Mode
 *
 * This test validates that an nginx container pulled via sourceRef mode is running correctly.
 * The test is executed as part of the dockerProject pipeline to verify the sourceRef workflow.
 *
 * The dockerProject DSL with sourceRef mode simplifies using existing Docker Hub images
 * by specifying component properties (registry, namespace, imageName, tag) instead of
 * building from a Dockerfile.
 *
 * The test flow (orchestrated by dockerProject):
 * 1. Image is pulled from Docker Hub (docker.io/library/nginx:1.27-alpine) if pullIfMissing=true
 * 2. Additional tags are applied as aliases to the source image
 * 3. Container is started using the source image tag (nginx:1.27-alpine)
 * 4. These tests run (integrationTest)
 * 5. Container is stopped via composeDown
 * 6. On success, 'verified' tag is added as an additional alias (onSuccess.additionalTags)
 *
 * Key differences from Build Mode (Scenario 1):
 * - No Dockerfile or build context needed
 * - Image comes from Docker Hub via sourceRef component properties
 * - Compose file references the SOURCE image tag (not a custom local name)
 * - Uses waitForRunning (not waitForHealthy) since nginx:alpine has no health check
 */
class ProjectSourceRefModeIT extends Specification {

    def setupSpec() {
        // The compose file exposes port 80 to 9301
        RestAssured.baseURI = "http://localhost"
        RestAssured.port = 9301

        println "=== dockerProject Integration Test: SourceRef Mode ==="
        println "Testing endpoint: ${RestAssured.baseURI}:${RestAssured.port}"
        println "Image source: docker.io/library/nginx:1.27-alpine"
        println "Local tag: project-scenario2-nginx:latest"
    }

    def cleanupSpec() {
        // Force cleanup in case tests fail
        def projectName = System.getProperty('COMPOSE_PROJECT_NAME', 'project-scenario2-test')
        try {
            println "=== Cleaning up Docker Compose stack: ${projectName} ==="
            def process = ['docker', 'compose', '-p', projectName, 'down', '-v'].execute()
            process.waitFor()
        } catch (Exception e) {
            println "Warning: Cleanup failed: ${e.message}"
        }
    }

    def "nginx default page should be accessible"() {
        expect: "nginx serves the default welcome page"
        given()
            .when()
            .get("/")
            .then()
            .statusCode(200)
            .body(containsString("Welcome to nginx"))
    }

    def "nginx should return correct server header"() {
        expect: "response includes nginx server header"
        given()
            .when()
            .get("/")
            .then()
            .statusCode(200)
            .header("Server", containsString("nginx"))
    }

    def "nginx should handle 404 for non-existent paths"() {
        expect: "non-existent path returns 404"
        given()
            .when()
            .get("/this-path-does-not-exist-12345")
            .then()
            .statusCode(404)
    }

    def "nginx should return Content-Type header for HTML"() {
        expect: "default page returns HTML content type"
        given()
            .when()
            .get("/")
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
    }

    def "nginx HEAD request should work"() {
        expect: "HEAD request returns 200 with no body"
        given()
            .when()
            .head("/")
            .then()
            .statusCode(200)
    }
}
