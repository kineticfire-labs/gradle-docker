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
 * Verification Test: Using Existing/Public Docker Images (nginx, redis)
 *
 * INTERNAL TEST - Validates plugin mechanics with public images, not application behavior.
 *
 * This test validates that the dockerOrch plugin correctly:
 * - Works with public Docker Hub images (nginx, redis) using sourceRef pattern
 * - Pulls public images automatically (no build needed)
 * - Uses MIXED wait strategy (waitForHealthy for app, waitForRunning for public images)
 * - Starts multiple services (custom app + 2 public images)
 * - Generates state files with port mappings for all services
 * - Enables realistic multi-service orchestration with external dependencies
 * - Cleans up all resources on composeDown
 *
 * For user-facing examples of testing applications, see examples/web-app/
 */
class ExistingImagesPluginIT extends Specification {

    static String projectName
    static Map stateData

    def setupSpec() {
        // Read system properties set by Gradle
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')
        def stateFilePath = System.getProperty('COMPOSE_STATE_FILE')

        println "=== Verification: Existing Images Plugin Mechanics ==="
        println "Project Name: ${projectName}"
        println "State File: ${stateFilePath}"

        // Parse state file
        def stateFile = new File(stateFilePath)
        stateData = StateFileValidator.parseStateFile(stateFile)
    }

    // NOTE: Cleanup is handled by Gradle task workflow (composeDown via finalizedBy)
    def cleanupSpec() {
        // Force cleanup even if tests fail
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

    def "plugin should generate valid state file with all three services"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateData, 'existingImagesTest', projectName)

        and: "all three services are present"
        def serviceNames = StateFileValidator.getServiceNames(stateData)
        serviceNames.contains('existing-images-app')
        serviceNames.contains('nginx')
        serviceNames.contains('redis')

        and: "state has exactly 3 services"
        serviceNames.size() == 3
    }

    def "plugin should start all containers in running state"() {
        expect: "app container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'existing-images-app')

        and: "nginx container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'nginx')

        and: "redis container is running"
        DockerComposeValidator.isContainerRunning(projectName, 'redis')
    }

    def "plugin should use mixed wait strategy (healthy for app, running for public images)"() {
        expect: "app container is HEALTHY (has health check, uses waitForHealthy)"
        DockerComposeValidator.isServiceHealthyViaCompose(projectName, 'existing-images-app')

        and: "nginx container is RUNNING (public image, no health check, uses waitForRunning)"
        DockerComposeValidator.isServiceRunningViaCompose(projectName, 'nginx')

        and: "redis container is RUNNING (public image, no health check, uses waitForRunning)"
        DockerComposeValidator.isServiceRunningViaCompose(projectName, 'redis')
    }

    def "plugin should map ports for all services"() {
        when: "we check app service port mapping"
        def appServiceInfo = StateFileValidator.getServiceInfo(stateData, 'existing-images-app')

        then: "app has port mapping for 8080"
        appServiceInfo.publishedPorts.size() > 0
        def appPort = StateFileValidator.getPublishedPort(stateData, 'existing-images-app', 8080)
        appPort > 0
        appPort <= 65535

        when: "we check nginx service port mapping"
        def nginxServiceInfo = StateFileValidator.getServiceInfo(stateData, 'nginx')

        then: "nginx has port mapping for 80"
        nginxServiceInfo.publishedPorts.size() > 0
        def nginxPort = StateFileValidator.getPublishedPort(stateData, 'nginx', 80)
        nginxPort > 0
        nginxPort <= 65535

        when: "we check redis service port mapping"
        def redisServiceInfo = StateFileValidator.getServiceInfo(stateData, 'redis')

        then: "redis has port mapping for 6379"
        redisServiceInfo.publishedPorts.size() > 0
        def redisPort = StateFileValidator.getPublishedPort(stateData, 'redis', 6379)
        redisPort > 0
        redisPort <= 65535
    }

    def "nginx should serve static content"() {
        when: "we fetch the static HTML from nginx"
        def nginxPort = StateFileValidator.getPublishedPort(stateData, 'nginx', 80)
        def url = new URL("http://localhost:${nginxPort}/index.html")
        def connection = url.openConnection()
        connection.setRequestMethod("GET")
        connection.connect()

        def responseCode = connection.getResponseCode()
        def response = connection.getInputStream().text

        then: "endpoint is accessible"
        responseCode == 200

        and: "response contains expected content"
        response.contains("Existing Images Test")
        response.contains("nginx")
        response.contains("public Docker Hub image")
    }

    def "app health endpoint should be accessible"() {
        when: "we query the app health endpoint"
        def appPort = StateFileValidator.getPublishedPort(stateData, 'existing-images-app', 8080)
        def url = new URL("http://localhost:${appPort}/health")
        def connection = url.openConnection()
        connection.setRequestMethod("GET")
        connection.connect()

        def responseCode = connection.getResponseCode()
        def response = connection.getInputStream().text
        def data = new groovy.json.JsonSlurper().parseText(response)

        then: "endpoint is accessible"
        responseCode == 200

        and: "response indicates healthy"
        data.status == "UP"
    }

    def "app should fetch content from nginx"() {
        when: "we call app endpoint that fetches from nginx"
        def appPort = StateFileValidator.getPublishedPort(stateData, 'existing-images-app', 8080)
        def url = new URL("http://localhost:${appPort}/nginx")
        def connection = url.openConnection()
        connection.setRequestMethod("GET")
        connection.connect()

        def responseCode = connection.getResponseCode()
        def response = connection.getInputStream().text

        then: "endpoint is accessible"
        responseCode == 200

        and: "response contains nginx content"
        response.contains("Existing Images Test")
        response.contains("nginx")
    }

    def "app should test redis connection"() {
        when: "we call app endpoint that tests redis"
        def appPort = StateFileValidator.getPublishedPort(stateData, 'existing-images-app', 8080)
        def url = new URL("http://localhost:${appPort}/redis/test")
        def connection = url.openConnection()
        connection.setRequestMethod("GET")
        connection.connect()

        def responseCode = connection.getResponseCode()
        def response = connection.getInputStream().text
        def data = new groovy.json.JsonSlurper().parseText(response)

        then: "endpoint is accessible"
        responseCode == 200

        and: "redis responds to ping"
        data.redis == "PONG"
    }

    def "app should store and retrieve data from redis"() {
        given: "app is accessible"
        def appPort = StateFileValidator.getPublishedPort(stateData, 'existing-images-app', 8080)
        def testKey = "test-key-${System.currentTimeMillis()}"
        def testValue = "test-value-${UUID.randomUUID()}"

        when: "we store a value in redis via app"
        def storeUrl = new URL("http://localhost:${appPort}/cache/${testKey}")
        def storeConnection = storeUrl.openConnection()
        storeConnection.setRequestMethod("POST")
        storeConnection.setDoOutput(true)
        storeConnection.setRequestProperty("Content-Type", "text/plain")
        storeConnection.getOutputStream().write(testValue.bytes)

        def storeResponseCode = storeConnection.getResponseCode()
        def storeResponse = storeConnection.getInputStream().text
        def storeData = new groovy.json.JsonSlurper().parseText(storeResponse)

        then: "store operation succeeds"
        storeResponseCode == 200
        storeData.status == "stored"
        storeData.key == testKey

        when: "we retrieve the value from redis via app"
        def retrieveUrl = new URL("http://localhost:${appPort}/cache/${testKey}")
        def retrieveConnection = retrieveUrl.openConnection()
        retrieveConnection.setRequestMethod("GET")
        retrieveConnection.connect()

        def retrieveResponseCode = retrieveConnection.getResponseCode()
        def retrievedValue = retrieveConnection.getInputStream().text

        then: "retrieve operation succeeds"
        retrieveResponseCode == 200
        retrievedValue == testValue
    }

    def "app should delete data from redis"() {
        given: "a value exists in redis"
        def appPort = StateFileValidator.getPublishedPort(stateData, 'existing-images-app', 8080)
        def testKey = "delete-key-${System.currentTimeMillis()}"
        def testValue = "delete-value"

        // Store value
        def storeUrl = new URL("http://localhost:${appPort}/cache/${testKey}")
        def storeConnection = storeUrl.openConnection()
        storeConnection.setRequestMethod("POST")
        storeConnection.setDoOutput(true)
        storeConnection.setRequestProperty("Content-Type", "text/plain")
        storeConnection.getOutputStream().write(testValue.bytes)
        storeConnection.getResponseCode()

        when: "we delete the value"
        def deleteUrl = new URL("http://localhost:${appPort}/cache/${testKey}")
        def deleteConnection = deleteUrl.openConnection()
        deleteConnection.setRequestMethod("DELETE")
        deleteConnection.connect()

        def deleteResponseCode = deleteConnection.getResponseCode()
        def deleteResponse = deleteConnection.getInputStream().text
        def deleteData = new groovy.json.JsonSlurper().parseText(deleteResponse)

        then: "delete operation succeeds"
        deleteResponseCode == 200
        deleteData.status == "deleted"
        deleteData.key == testKey

        when: "we try to retrieve the deleted value"
        def retrieveUrl = new URL("http://localhost:${appPort}/cache/${testKey}")
        def retrieveConnection = retrieveUrl.openConnection()
        retrieveConnection.setRequestMethod("GET")
        retrieveConnection.connect()

        def retrieveResponseCode = retrieveConnection.getResponseCode()

        then: "value no longer exists"
        retrieveResponseCode == 404
    }

}
