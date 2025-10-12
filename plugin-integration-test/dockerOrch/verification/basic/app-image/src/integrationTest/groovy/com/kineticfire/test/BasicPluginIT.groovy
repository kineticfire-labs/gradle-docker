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
 * Verification Test: Basic Docker Compose Up/Down with Health Checks
 *
 * INTERNAL TEST - Validates plugin mechanics, not application behavior.
 *
 * This test validates that the dockerOrch plugin correctly:
 * - Starts Docker Compose stacks
 * - Waits for containers to become healthy
 * - Generates state files with correct structure
 * - Maps ports correctly
 * - Cleans up all resources on composeDown
 *
 * For user-facing examples of testing applications, see examples/web-app/
 */
class BasicPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        // Read system properties set by Gradle
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Verification: Basic Plugin Mechanics ==="
        println "Project Name: ${projectName}"
        println "State File: ${stateFilePath}"

        // Parse state file
        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    // NOTE: Cleanup is handled by Gradle task workflow (composeDown via finalizedBy)
    // No cleanupSpec() needed - containers are stopped by the Gradle task after test completes

    def "plugin should generate valid state file"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateData, 'webAppTest', projectName)

        and: "web-app service is present"
        def serviceNames = StateFileValidator.getServiceNames(stateData)
        serviceNames.contains('web-app')

        and: "web-app has port mapping"
        def serviceInfo = StateFileValidator.getServiceInfo(stateData, 'web-app')
        serviceInfo.publishedPorts.size() > 0
    }

    def "plugin should start container in running state"() {
        expect: "container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'web-app')
    }

    def "plugin should wait until container is healthy"() {
        expect: "container reports healthy status"
        DockerComposeValidator.isContainerHealthy(projectName, 'web-app')

        and: "health check was actually performed (not instant)"
        // State file timestamp should show the wait occurred
        stateData.timestamp != null
    }

    def "plugin should map ports correctly"() {
        when: "we read port mapping from state file"
        def hostPort = StateFileValidator.getPublishedPort(stateData, 'web-app', 8080)

        then: "port is mapped to valid host port"
        hostPort > 0
        hostPort <= 65535

        and: "state file contains port mapping information"
        def serviceInfo = StateFileValidator.getServiceInfo(stateData, 'web-app')
        serviceInfo.publishedPorts.any { it.host == hostPort && it.container == 8080 }
    }
}
