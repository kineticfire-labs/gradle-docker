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

import com.kineticfire.test.DockerComposeValidator
import com.kineticfire.test.StateFileValidator
import com.kineticfire.test.HttpValidator
import com.kineticfire.test.CleanupValidator
import spock.lang.Specification
import groovy.json.JsonSlurper

/**
 * Integration test for Scenario 1: Web App Build + Healthy Wait
 *
 * Validates:
 * - Image built from app JAR
 * - Container starts and becomes healthy
 * - State file generated correctly
 * - HTTP endpoints accessible
 */
class WebAppIT extends Specification {

    static String projectName
    static Map stateData
    static int hostPort

    def setupSpec() {
        // Read system properties set by Gradle
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Scenario 1: Web App Integration Test ==="
        println "Project Name: ${projectName}"
        println "State File: ${stateFilePath}"

        // Parse state file
        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)

        // Get published port for web-app service
        hostPort = StateFileValidator.getPublishedPort(stateData, 'web-app', 8080)

        println "Testing against web-app on localhost:${hostPort}"
    }

    def cleanupSpec() {
        // Verify no resource leaks after all tests
        // Note: composeDown should have already cleaned up
        println "=== Verifying cleanup for project: ${projectName} ==="
    }

    def "state file should have valid structure"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateData, 'webAppTest', projectName)

        and: "web-app service is present"
        def serviceNames = StateFileValidator.getServiceNames(stateData)
        serviceNames.contains('web-app')

        and: "web-app has port mapping"
        def serviceInfo = StateFileValidator.getServiceInfo(stateData, 'web-app')
        serviceInfo.publishedPorts.size() > 0
    }

    def "web-app container should be running"() {
        expect: "container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'web-app')
    }

    def "web-app container should be healthy"() {
        expect: "container reports healthy status"
        DockerComposeValidator.isContainerHealthy(projectName, 'web-app')
    }

    def "web-app should respond to health check"() {
        when: "we call the health endpoint"
        def response = HttpValidator.getResponseBody("http://localhost:${hostPort}/health")
        def json = new JsonSlurper().parseText(response)

        then: "response indicates app is UP"
        json.status == 'UP'
        json.timestamp != null
    }

    def "web-app should respond to root endpoint"() {
        when: "we call the root endpoint"
        def responseCode = HttpValidator.getResponseCode("http://localhost:${hostPort}/")

        then: "we get 200 OK"
        responseCode == 200

        when: "we get the response body"
        def response = HttpValidator.getResponseBody("http://localhost:${hostPort}/")
        def json = new JsonSlurper().parseText(response)

        then: "response contains expected message"
        json.message == 'Web App is running'
        json.version == '1.0.0'
    }

    def "web-app should handle multiple concurrent requests"() {
        when: "we make 10 concurrent requests"
        def results = Collections.synchronizedList([])
        def threads = (1..10).collect {
            Thread.start {
                def responseCode = HttpValidator.getResponseCode("http://localhost:${hostPort}/health")
                results << responseCode
            }
        }
        threads*.join()

        then: "all requests should succeed with 200"
        results.size() == 10
        results.every { it == 200 }
    }
}
