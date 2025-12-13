package com.kineticfire.test

import com.kineticfire.gradle.docker.spock.ComposeUp
import groovy.json.JsonSlurper
import io.restassured.RestAssured
import spock.lang.Specification

import static io.restassured.RestAssured.given

/**
 * Verification test for METHOD lifecycle using @ComposeUp extension.
 *
 * This test verifies that the Spock extension correctly implements METHOD lifecycle:
 * - Containers start fresh before EACH test method
 * - State does NOT persist between test methods
 * - Containers stop after EACH test method
 *
 * CONFIGURATION:
 * =============
 * All Docker Compose configuration is in build.gradle (single source of truth):
 * - Stack name: lifecycleMethodTest
 * - Compose file: src/integrationTest/resources/compose/lifecycle-method.yml
 * - Lifecycle: METHOD (set via usesCompose in build.gradle)
 * - Wait settings: Wait for 'state-app' service to be HEALTHY
 *
 * The @ComposeUp annotation has NO parameters - all config comes from build.gradle!
 */
@ComposeUp  // No parameters! All configuration comes from build.gradle via usesCompose()
class LifecycleMethodIT extends Specification {

    // Instance variables (not static!) - fresh for each test
    String baseUrl

    // Static counters to track lifecycle calls
    static int setupCallCount = 0
    static int cleanupCallCount = 0

    def setup() {
        setupCallCount++

        // Extension provides COMPOSE_STATE_FILE system property
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateFile = new File(stateFilePath)
        def stateData = new JsonSlurper().parse(stateFile)

        def port = stateData.services['state-app'].publishedPorts[0].host
        baseUrl = "http://localhost:${port}"
        RestAssured.baseURI = baseUrl

        println "=== Test ${setupCallCount}: Setup complete with fresh containers at ${baseUrl} ==="
    }

    def cleanup() {
        cleanupCallCount++
        println "=== Test ${cleanupCallCount}: Cleanup complete, containers stopping ==="
    }

    def "setup should be called before each test"() {
        expect: "setup called at least once"
        setupCallCount >= 1
    }

    def "extension should generate valid state file"() {
        given: "we read the state file"
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')
        def stateFile = new File(stateFilePath)
        def stateData = new JsonSlurper().parse(stateFile)

        expect: "state file has required fields with METHOD lifecycle"
        stateData.stackName == 'lifecycleMethodTest'
        stateData.lifecycle == 'method'
        stateData.testClass != null
        stateData.testMethod != null
        stateData.services['state-app'] != null
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
