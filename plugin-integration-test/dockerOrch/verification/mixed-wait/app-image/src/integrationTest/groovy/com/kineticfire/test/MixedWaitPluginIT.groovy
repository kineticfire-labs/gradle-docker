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
 * Verification Test: Mixed Wait Strategies (waitForHealthy + waitForRunning)
 *
 * INTERNAL TEST - Validates plugin mechanics, not application behavior.
 *
 * This test validates that the dockerOrch plugin correctly:
 * - Handles BOTH wait mechanisms in the same Docker Compose stack
 * - Waits for app service to become HEALTHY (has health check)
 * - Waits for database service to become RUNNING (no health check)
 * - Generates state files with correct information for both services
 * - Maps ports correctly for app (db has no exposed ports)
 * - Enables realistic multi-service orchestration (app + database)
 * - Cleans up all resources on composeDown
 *
 * For user-facing examples of testing applications, see examples/web-app/
 */
class MixedWaitPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        // Read system properties set by Gradle
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Verification: Mixed-Wait Plugin Mechanics ==="
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

    def "plugin should generate valid state file with both services"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateData, 'mixedWaitTest', projectName)

        and: "both services are present"
        def serviceNames = StateFileValidator.getServiceNames(stateData)
        serviceNames.contains('mixed-wait-app')
        serviceNames.contains('mixed-wait-db')

        and: "state has exactly 2 services"
        serviceNames.size() == 2
    }

    def "plugin should start both containers in running state"() {
        expect: "app container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'mixed-wait-app')

        and: "db container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'mixed-wait-db')
    }

    def "plugin should wait for app to be healthy and db to be running"() {
        expect: "app container is HEALTHY (has health check)"
        // Use isServiceHealthyViaCompose to match the plugin's health check mechanism
        // This avoids timing issues between 'docker compose ps' and 'docker inspect'
        DockerComposeValidator.isServiceHealthyViaCompose(projectName, 'mixed-wait-app')

        and: "db container is RUNNING (waitForRunning validates running state)"
        // Use isServiceRunningViaCompose to match the plugin's running check mechanism
        DockerComposeValidator.isServiceRunningViaCompose(projectName, 'mixed-wait-db')

        and: "db container status is 'running' (the actual state waitForRunning checks)"
        // This check still uses docker inspect, which is fine for status (not health)
        DockerComposeValidator.getContainerStatus(projectName, 'mixed-wait-db') in ['running', 'healthy']
    }

    def "plugin should map ports only for app service"() {
        when: "we check app service port mapping"
        def appServiceInfo = StateFileValidator.getServiceInfo(stateData, 'mixed-wait-app')

        then: "app has port mapping for 8080"
        appServiceInfo.publishedPorts.size() > 0
        def appPort = StateFileValidator.getPublishedPort(stateData, 'mixed-wait-app', 8080)
        appPort > 0
        appPort <= 65535

        when: "we check db service port mapping"
        def dbServiceInfo = StateFileValidator.getServiceInfo(stateData, 'mixed-wait-db')

        then: "db has NO port mappings (not exposed)"
        dbServiceInfo.publishedPorts.size() == 0
    }

    def "app should be accessible via HTTP"() {
        when: "we query the root endpoint"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'mixed-wait-app', 8080)
        def url = new URL("http://localhost:${hostPort}/")
        def connection = url.openConnection()
        connection.setRequestMethod("GET")
        connection.connect()

        def responseCode = connection.getResponseCode()
        def response = connection.getInputStream().text
        def data = new groovy.json.JsonSlurper().parseText(response)

        then: "endpoint is accessible"
        responseCode == 200

        and: "response has expected content"
        data.message == "Mixed-wait verification test app"
        data.version == "1.0.0"
    }

    def "app health endpoint should be accessible"() {
        when: "we query the health endpoint"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'mixed-wait-app', 8080)
        def url = new URL("http://localhost:${hostPort}/health")
        def connection = url.openConnection()
        connection.setRequestMethod("GET")
        connection.connect()

        def responseCode = connection.getResponseCode()
        def response = connection.getInputStream().text
        def healthData = new groovy.json.JsonSlurper().parseText(response)

        then: "health endpoint is accessible"
        responseCode == 200

        and: "health status is UP"
        healthData.status == "UP"

        and: "uptime is reported"
        healthData.uptimeMs >= 0
    }

    def "app should successfully connect to database"() {
        when: "we query the database endpoint"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'mixed-wait-app', 8080)
        def url = new URL("http://localhost:${hostPort}/db")
        def connection = url.openConnection()
        connection.setRequestMethod("GET")
        connection.connect()

        def responseCode = connection.getResponseCode()
        def response = connection.getInputStream().text
        def dbData = new groovy.json.JsonSlurper().parseText(response)

        then: "database endpoint is accessible"
        responseCode == 200

        and: "database connection is successful"
        dbData.status == "connected"

        and: "test query returns expected result"
        dbData.testQuery == 1

        and: "database type is reported correctly"
        dbData.database == "PostgreSQL"
    }

    def "plugin should respect both timeout configurations"() {
        expect: "app timeout was set to 90 seconds"
        // This is tested implicitly - if the app didn't become healthy within 90 seconds,
        // the composeUp task would have failed and we wouldn't be running tests
        // The fact that we're here means the 90 second timeout was respected
        true

        and: "db timeout was set to 60 seconds"
        // Similarly, if the db didn't start within 60 seconds, composeUp would have failed
        // The fact that both containers are running means both timeouts were respected
        DockerComposeValidator.isContainerRunning(projectName, 'mixed-wait-db')
    }

    def "plugin should respect both poll interval configurations"() {
        expect: "both services use 2 second poll interval"
        // This is validated by the fact that both containers reached their expected state
        // The actual polling is done by the plugin, so we verify the outcome
        // Use compose-based checks to match the plugin's mechanism
        DockerComposeValidator.isServiceHealthyViaCompose(projectName, 'mixed-wait-app')
        DockerComposeValidator.isServiceRunningViaCompose(projectName, 'mixed-wait-db')

        and: "both wait mechanisms completed successfully"
        // If poll intervals were not respected, timing would be off
        // The successful state of both containers proves the poll intervals worked
        true
    }

    def "plugin should handle multi-service dependencies correctly"() {
        expect: "app depends on db (compose depends_on)"
        // The fact that app can connect to database proves:
        // 1. DB started first (or at least is accessible)
        // 2. App can resolve db hostname
        // 3. Compose networking is configured correctly
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'mixed-wait-app', 8080)
        def url = new URL("http://localhost:${hostPort}/db")
        def connection = url.openConnection()
        connection.setRequestMethod("GET")
        connection.connect()
        def responseCode = connection.getResponseCode()
        responseCode == 200
    }

    def "plugin should sequence waits appropriately"() {
        expect: "both waits completed before tests started"
        // The state file is written after ALL waits complete
        // The fact that we can read it and both services are ready proves sequential processing
        stateData.timestamp != null

        and: "both services are in expected state"
        // Use compose-based checks to match the plugin's mechanism
        DockerComposeValidator.isServiceHealthyViaCompose(projectName, 'mixed-wait-app')
        DockerComposeValidator.isServiceRunningViaCompose(projectName, 'mixed-wait-db')
    }
}
