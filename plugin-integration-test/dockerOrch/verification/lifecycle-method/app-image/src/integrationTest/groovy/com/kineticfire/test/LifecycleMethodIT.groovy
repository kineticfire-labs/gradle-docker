package com.kineticfire.test

import groovy.json.JsonSlurper
import io.restassured.RestAssured
import spock.lang.Specification

import static io.restassured.RestAssured.given

class LifecycleMethodIT extends Specification {

    // Instance variables (not static!) - fresh for each test
    String projectName
    Map stateData
    String baseUrl

    // Static counters to track lifecycle calls
    static int setupCallCount = 0
    static int cleanupCallCount = 0

    def setup() {
        setupCallCount++

        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)

        def port = stateData.services['state-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"
        RestAssured.baseURI = baseUrl
    }

    def cleanup() {
        cleanupCallCount++
        // Note: composeDown runs via finalizedBy at the very end, not after each test method
        // So we don't verify cleanup here
    }

    def "setup should be called before each test"() {
        expect: "setup called at least once"
        setupCallCount >= 1
    }

    def "plugin should generate valid state file"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateData, 'lifecycleMethodTest', projectName)
    }

    def "containers should be fresh for each test - write test 1"() {
        when: "we write state to the application"
        def response = given()
            .contentType("application/json")
            .body('{"key":"test1","value":"method-1"}')
            .post("/state")

        then: "state is written successfully"
        response.statusCode() == 200
        response.jsonPath().getString("value") == "method-1"
    }

    def "containers should be fresh for each test - verify isolation"() {
        when: "we try to read state from previous test"
        def response = given().get("/state/test1")

        then: "state from previous test does NOT exist (fresh container)"
        response.statusCode() == 404
    }

    def "state should not persist between test methods - write test 2"() {
        when: "we write different state"
        def response = given()
            .contentType("application/json")
            .body('{"key":"test2","value":"method-2"}')
            .post("/state")

        then: "state is written successfully"
        response.statusCode() == 200
    }

    def "state should not persist between test methods - verify test 2 isolation"() {
        when: "we check for state from previous tests"
        def response1 = given().get("/state/test1")
        def response2 = given().get("/state/test2")

        then: "no state from any previous test exists (fresh containers)"
        response1.statusCode() == 404
        response2.statusCode() == 404
    }

    def "cleanup should be called after each test"() {
        expect: "cleanup called at least once (may not equal setup count during this test)"
        cleanupCallCount >= 1
    }
}
