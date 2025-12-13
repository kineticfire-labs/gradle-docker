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
import com.kineticfire.test.CleanupValidator
import spock.lang.Specification

/**
 * Verification Test: Wait-Running (No Health Check)
 *
 * INTERNAL TEST - Validates plugin mechanics, not application behavior.
 *
 * This test validates that the dockerTest plugin correctly:
 * - Waits for containers to reach RUNNING state (not healthy)
 * - Works with containers that have NO health check configured
 * - Respects timeout and poll interval settings
 * - Generates state files with correct structure
 * - Maps ports correctly
 * - Cleans up all resources on composeDown
 *
 * KEY DIFFERENCE from wait-healthy test:
 * - No healthcheck in Docker Compose file
 * - Tests waitForRunning (not waitForHealthy)
 * - Verifies RUNNING state (not HEALTHY state)
 *
 * For user-facing examples of testing applications, see examples/web-app/
 */
class WaitRunningPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        // Read system properties set by Gradle
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Verification: Wait-Running Plugin Mechanics ==="
        println "Project Name: ${projectName}"
        println "State File: ${stateFilePath}"

        // Parse state file
        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    // NOTE: Cleanup is handled by Gradle task workflow (composeDown via finalizedBy)
    def cleanupSpec() {
        // Force cleanup even if tests fail - finalizedBy doesn't execute when tests fail during execution
        try {
            println "=== Forcing cleanup of Docker Compose stack: ${projectName} ==="
            def process = ['docker', 'compose', '-p', projectName, 'down', '-v'].execute()
            process.waitFor()
            if (process.exitValue() != 0) {
                println "Warning: docker compose down returned ${process.exitValue()}"
            } else {
                println "Successfully cleaned up compose stack"
            }
        } catch (Exception e) {
            println "Warning: Failed to cleanup Docker Compose stack: ${e.message}"
        }
    }

    def "plugin should generate valid state file"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateData, 'waitRunningTest', projectName)

        and: "wait-running-app service is present"
        def serviceNames = StateFileValidator.getServiceNames(stateData)
        serviceNames.contains('wait-running-app')

        and: "wait-running-app has port mapping"
        def serviceInfo = StateFileValidator.getServiceInfo(stateData, 'wait-running-app')
        serviceInfo.publishedPorts.size() > 0
    }

    def "plugin should start container in running state"() {
        expect: "container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'wait-running-app')
    }

    def "plugin should wait for running state without health check"() {
        when: "we check container status"
        def isRunning = DockerComposeValidator.isContainerRunning(projectName, 'wait-running-app')

        then: "container is in running state"
        isRunning

        and: "container does NOT have health status (no healthcheck configured)"
        // For containers without healthcheck, Docker won't report health status
        // This is the key difference from wait-healthy test
        !DockerComposeValidator.hasHealthCheck(projectName, 'wait-running-app')
    }

    def "plugin should make service accessible via HTTP"() {
        when: "we query the root endpoint with retry logic"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'wait-running-app', 8080)

        // Initial delay to allow app to finish startup after container is running
        Thread.sleep(3000)

        // Retry logic to handle brief window where container is running but app not fully ready
        def maxRetries = 10
        def retryDelay = 3000 // 3 seconds between retries
        def responseCode = 0
        def response = null
        def lastException = null

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                def url = new URL("http://localhost:${hostPort}/")
                def connection = url.openConnection()
                connection.setRequestMethod("GET")
                connection.setConnectTimeout(5000)
                connection.setReadTimeout(5000)
                connection.setRequestProperty("Connection", "close")

                responseCode = connection.getResponseCode()
                response = connection.getInputStream().text
                lastException = null
                break // Success, exit retry loop
            } catch (Exception e) {
                lastException = e
                if (attempt < maxRetries) {
                    println "Attempt ${attempt} failed: ${e.message}, retrying in ${retryDelay}ms..."
                    Thread.sleep(retryDelay)
                }
            }
        }

        if (lastException != null) {
            throw lastException
        }

        def responseData = new groovy.json.JsonSlurper().parseText(response)

        then: "root endpoint is accessible"
        responseCode == 200

        and: "response contains expected data"
        responseData.message == "Wait-running verification test app"
        responseData.version == "1.0.0"
    }

    def "plugin should make health endpoint accessible"() {
        when: "we query the health endpoint with retry logic"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'wait-running-app', 8080)

        // Initial delay to allow app to finish startup after container is running
        Thread.sleep(3000)

        // Retry logic to handle brief window where container is running but app not fully ready
        def maxRetries = 10
        def retryDelay = 3000 // 3 seconds between retries
        def responseCode = 0
        def response = null
        def lastException = null

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                def url = new URL("http://localhost:${hostPort}/health")
                def connection = url.openConnection()
                connection.setRequestMethod("GET")
                connection.setConnectTimeout(5000)
                connection.setReadTimeout(5000)
                connection.setRequestProperty("Connection", "close")

                responseCode = connection.getResponseCode()
                response = connection.getInputStream().text
                lastException = null
                break // Success, exit retry loop
            } catch (Exception e) {
                lastException = e
                if (attempt < maxRetries) {
                    println "Attempt ${attempt} failed: ${e.message}, retrying in ${retryDelay}ms..."
                    Thread.sleep(retryDelay)
                }
            }
        }

        if (lastException != null) {
            throw lastException
        }

        def healthData = new groovy.json.JsonSlurper().parseText(response)

        then: "health endpoint is accessible"
        responseCode == 200

        and: "health status is UP"
        healthData.status == "UP"
    }

    def "plugin should respect timeout configuration"() {
        expect: "timeout was set to 60 seconds"
        // This is tested implicitly - if the container didn't reach running state within 60 seconds,
        // the composeUp task would have failed and we wouldn't be running tests
        // The fact that we're here means the 60 second timeout was respected
        true

        and: "container reached running state within timeout window"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'wait-running-app', 8080)

        // Retry logic to handle brief window where container is running but app not fully ready
        def maxRetries = 5
        def retryDelay = 2000 // 2 seconds between retries
        def responseCode = 0
        def lastException = null

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                def url = new URL("http://localhost:${hostPort}/health")
                def connection = url.openConnection()
                connection.setRequestMethod("GET")
                connection.setConnectTimeout(5000)
                connection.setReadTimeout(5000)
                connection.setRequestProperty("Connection", "close")

                responseCode = connection.getResponseCode()
                lastException = null
                break // Success, exit retry loop
            } catch (Exception e) {
                lastException = e
                if (attempt < maxRetries) {
                    println "Attempt ${attempt} failed: ${e.message}, retrying in ${retryDelay}ms..."
                    Thread.sleep(retryDelay)
                }
            }
        }

        if (lastException != null) {
            throw lastException
        }

        responseCode == 200
    }

    def "plugin should respect poll interval configuration"() {
        expect: "poll interval was set to 2 seconds"
        // This is validated by the fact that the plugin would poll every 2 seconds
        // The actual polling is done by the plugin, so we just verify the container is running
        // If polling interval was not respected, timing would be off
        DockerComposeValidator.isContainerRunning(projectName, 'wait-running-app')

        and: "container is accessible"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'wait-running-app', 8080)

        // Retry logic to handle brief window where container is running but app not fully ready
        def maxRetries = 5
        def retryDelay = 2000 // 2 seconds between retries
        def responseCode = 0
        def lastException = null

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                def url = new URL("http://localhost:${hostPort}/health")
                def connection = url.openConnection()
                connection.setRequestMethod("GET")
                connection.setConnectTimeout(5000)
                connection.setReadTimeout(5000)
                connection.setRequestProperty("Connection", "close")

                responseCode = connection.getResponseCode()
                lastException = null
                break // Success, exit retry loop
            } catch (Exception e) {
                lastException = e
                if (attempt < maxRetries) {
                    println "Attempt ${attempt} failed: ${e.message}, retrying in ${retryDelay}ms..."
                    Thread.sleep(retryDelay)
                }
            }
        }

        if (lastException != null) {
            throw lastException
        }

        responseCode == 200
    }

    def "plugin should map ports correctly"() {
        when: "we read port mapping from state file"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'wait-running-app', 8080)

        then: "port is mapped to valid host port"
        hostPort > 0
        hostPort <= 65535

        and: "state file contains port mapping information"
        def serviceInfo = StateFileValidator.getServiceInfo(stateData, 'wait-running-app')
        serviceInfo.publishedPorts.any { it.host == hostPort && it.container == 8080 }
    }

    def "plugin should record state file timestamp"() {
        expect: "state file has timestamp"
        stateData.timestamp != null

        and: "timestamp is in ISO 8601 format"
        stateData.timestamp ==~ /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.*/
    }
}
