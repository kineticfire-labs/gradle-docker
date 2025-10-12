package com.kineticfire.test

import groovy.json.JsonSlurper
import io.restassured.RestAssured
import spock.lang.Specification

import static io.restassured.RestAssured.given

class LifecycleClassIT extends Specification {

    static String projectName
    static Map stateData
    static String baseUrl
    static int setupSpecCallCount = 0
    static int cleanupSpecCallCount = 0

    def setupSpec() {
        setupSpecCallCount++

        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)

        def port = stateData.services['state-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"
        RestAssured.baseURI = baseUrl
    }

    def cleanupSpec() {
        cleanupSpecCallCount++
        // Note: Don't check for cleanup here - composeDown runs AFTER cleanupSpec via finalizedBy
        // Containers are still running at this point, which is correct for CLASS lifecycle
    }

    def "setupSpec should be called exactly once"() {
        expect: "setupSpec called once for entire test class"
        setupSpecCallCount == 1
    }

    def "plugin should generate valid state file"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateData, 'lifecycleClassTest', projectName)
    }

    def "containers should remain running between tests"() {
        expect: "same containers from setupSpec still running"
        DockerComposeValidator.isContainerRunning(projectName, 'state-app')
        DockerComposeValidator.isContainerHealthy(projectName, 'state-app')
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
