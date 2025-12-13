package com.kineticfire.test

import com.kineticfire.gradle.docker.spock.ComposeUp
import groovy.json.JsonSlurper
import io.restassured.RestAssured
import spock.lang.Specification

import static io.restassured.RestAssured.given

/**
 * Verification test for CLASS lifecycle using @ComposeUp extension.
 *
 * This test verifies that the Spock extension correctly implements CLASS lifecycle:
 * - Containers start ONCE before all tests (setupSpec)
 * - State PERSISTS between test methods
 * - Containers stop ONCE after all tests (cleanupSpec)
 *
 * CONFIGURATION:
 * =============
 * All Docker Compose configuration is in build.gradle (single source of truth):
 * - Stack name: lifecycleClassTest
 * - Compose file: src/integrationTest/resources/compose/lifecycle-class.yml
 * - Lifecycle: CLASS (set via usesCompose in build.gradle)
 * - Wait settings: Wait for 'state-app' service to be HEALTHY
 *
 * The @ComposeUp annotation has NO parameters - all config comes from build.gradle!
 */
@ComposeUp  // No parameters! All configuration comes from build.gradle via usesCompose()
class LifecycleClassIT extends Specification {

    // Static variables (shared across all tests in class)
    static String projectName
    static Map stateData
    static String baseUrl
    static int setupSpecCallCount = 0
    static int cleanupSpecCallCount = 0

    def setupSpec() {
        setupSpecCallCount++

        // Extension provides COMPOSE_STATE_FILE and COMPOSE_PROJECT_NAME system properties
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateFile = new File(stateFilePath)
        stateData = new JsonSlurper().parse(stateFile)

        def port = stateData.services['state-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"
        RestAssured.baseURI = baseUrl

        println "=== SetupSpec complete with shared containers at ${baseUrl} ==="
    }

    def cleanupSpec() {
        cleanupSpecCallCount++
        println "=== CleanupSpec complete, containers stopping ==="
    }

    def "setupSpec should be called exactly once"() {
        expect: "setupSpec called once for entire test class"
        setupSpecCallCount == 1
    }

    def "extension should generate valid state file"() {
        expect: "state file has required fields with CLASS lifecycle"
        stateData.stackName == 'lifecycleClassTest'
        stateData.lifecycle == 'class'
        stateData.testClass != null
        stateData.testMethod == null  // No testMethod in CLASS lifecycle
        stateData.services['state-app'] != null
    }

    def "containers should remain running between tests"() {
        expect: "same containers from setupSpec still running"
        DockerComposeValidator.isContainerRunning(projectName, 'state-app')
        // Use isServiceHealthyViaCompose to match the plugin's health check mechanism
        // This avoids timing lag between 'docker compose ps' (used by plugin) and 'docker inspect'
        DockerComposeValidator.isServiceHealthyViaCompose(projectName, 'state-app')
    }

    def "state should persist across test methods - write"() {
        when: "we write state to the application"
        def response = given()
            .contentType("application/json")
            .body('{"key":"test","value":"class-lifecycle"}')
            .post("/state")

        then: "state is written successfully"
        response.statusCode() == 200
        response.jsonPath().getString("value") == "class-lifecycle"
    }

    def "state should persist across test methods - read"() {
        when: "we read state written in previous test"
        def response = given().get("/state/test")

        then: "state from previous test still exists"
        response.statusCode() == 200
        response.jsonPath().getString("value") == "class-lifecycle"
    }

    def "containers should still be running after all tests"() {
        expect: "containers not torn down between tests"
        DockerComposeValidator.isContainerRunning(projectName, 'state-app')
    }

    def "cleanupSpec will be called exactly once after all tests"() {
        expect: "cleanup not yet called (will be called after this test)"
        cleanupSpecCallCount == 0
    }
}
