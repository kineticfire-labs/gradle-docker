package com.kineticfire.test

import com.kineticfire.test.DockerComposeValidator
import com.kineticfire.test.StateFileValidator
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Integration tests for multi-service orchestration verification.
 * <p>
 * Tests complex orchestration with 4 services:
 * - Spring Boot app (custom image, HEALTHY)
 * - PostgreSQL database (public image, RUNNING)
 * - Redis cache (public image, RUNNING)
 * - Nginx reverse proxy (public image, RUNNING)
 * <p>
 * Validates:
 * - All services start correctly
 * - Mixed wait strategies (waitForHealthy + waitForRunning)
 * - Inter-service communication
 * - Database persistence
 * - Cache operations
 * - Reverse proxy functionality
 */
class MultiServicePluginIT extends Specification {
    static String projectName
    static Map stateData
    static String appPort
    static String nginxPort
    static String postgresPort
    static String redisPort

    def setupSpec() {
        def stateFilePath = System.getProperty('STATE_FILE_PATH')
        projectName = System.getProperty('COMPOSE_PROJECT_NAME')

        assert stateFilePath: "STATE_FILE_PATH system property must be set"
        assert projectName: "COMPOSE_PROJECT_NAME system property must be set"

        stateData = StateFileValidator.parseStateFile(new File(stateFilePath))

        // Extract ports for all services
        appPort = stateData.services['multi-service-app'].publishedPorts[0].host
        nginxPort = stateData.services['nginx'].publishedPorts[0].host
        postgresPort = stateData.services['postgres'].publishedPorts[0].host
        redisPort = stateData.services['redis'].publishedPorts[0].host
    }

    // ========================================
    // State File and Container Validation
    // ========================================

    def "state file should contain all 4 services"() {
        expect: "state file has all services"
        stateData.services.size() == 4
        stateData.services.containsKey('multi-service-app')
        stateData.services.containsKey('postgres')
        stateData.services.containsKey('redis')
        stateData.services.containsKey('nginx')
    }

    def "all containers should be running"() {
        when: "we check container states"
        def appRunning = DockerComposeValidator.isContainerRunning(projectName, 'multi-service-app')
        def postgresRunning = DockerComposeValidator.isContainerRunning(projectName, 'postgres')
        def redisRunning = DockerComposeValidator.isContainerRunning(projectName, 'redis')
        def nginxRunning = DockerComposeValidator.isContainerRunning(projectName, 'nginx')

        then: "all containers are running"
        appRunning
        postgresRunning
        redisRunning
        nginxRunning
    }

    def "app container should be healthy"() {
        expect: "app container is healthy"
        DockerComposeValidator.isServiceHealthyViaCompose(projectName, 'multi-service-app')
    }

    def "mixed wait strategies should be reflected in state file"() {
        when: "we check the state data"
        def appState = stateData.services['multi-service-app'].state
        def postgresState = stateData.services['postgres'].state
        def redisState = stateData.services['redis'].state
        def nginxState = stateData.services['nginx'].state

        then: "all services are running (state file reports 'running' for all)"
        appState == 'running'
        postgresState == 'running'
        redisState == 'running'
        nginxState == 'running'
    }

    // ========================================
    // Application Health Check
    // ========================================

    def "app health endpoint should be accessible"() {
        when: "we call the health endpoint"
        def url = "http://localhost:${appPort}/health"
        def connection = new URL(url).openConnection()
        connection.setRequestMethod("GET")
        def response = new JsonSlurper().parse(connection.inputStream)

        then: "health check passes"
        response.status == 'UP'
        response.database == 'connected'
        response.redis == 'connected'
    }

    // ========================================
    // PostgreSQL Database Integration
    // ========================================

    def "app should save message to PostgreSQL database"() {
        when: "we post a message"
        def url = "http://localhost:${appPort}/messages"
        def payload = '{"content": "Test message", "author": "Integration Test"}'

        def connection = new URL(url).openConnection()
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.write(payload.bytes)

        def response = new JsonSlurper().parse(connection.inputStream)

        then: "message is saved with ID"
        response.id != null
        response.content == 'Test message'
        response.author == 'Integration Test'
    }

    def "app should retrieve messages from PostgreSQL database"() {
        given: "we have saved a message"
        def saveUrl = "http://localhost:${appPort}/messages"
        def payload = '{"content": "Retrieval test", "author": "Spock Test"}'

        def saveConn = new URL(saveUrl).openConnection()
        saveConn.setRequestMethod("POST")
        saveConn.setDoOutput(true)
        saveConn.setRequestProperty("Content-Type", "application/json")
        saveConn.outputStream.write(payload.bytes)
        saveConn.inputStream.close()

        when: "we retrieve all messages"
        def getUrl = "http://localhost:${appPort}/messages"
        def getConn = new URL(getUrl).openConnection()
        def messages = new JsonSlurper().parse(getConn.inputStream)

        then: "messages are retrieved"
        messages.size() >= 1
        messages.any { it.content == 'Retrieval test' }
    }

    def "app should return correct message count from database"() {
        when: "we get message count"
        def url = "http://localhost:${appPort}/messages/count"
        def connection = new URL(url).openConnection()
        def response = new JsonSlurper().parse(connection.inputStream)

        then: "count is returned"
        response.count >= 0
    }

    // ========================================
    // Redis Cache Integration
    // ========================================

    def "app should store value in Redis cache"() {
        when: "we store a value in cache"
        def url = "http://localhost:${appPort}/cache/testkey"
        def payload = '{"value": "testvalue"}'

        def connection = new URL(url).openConnection()
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.write(payload.bytes)

        def response = new JsonSlurper().parse(connection.inputStream)

        then: "value is cached"
        response.key == 'testkey'
        response.value == 'testvalue'
        response.cached == true
    }

    def "app should retrieve value from Redis cache"() {
        given: "we have stored a value"
        def storeUrl = "http://localhost:${appPort}/cache/retrievekey"
        def payload = '{"value": "retrievevalue"}'

        def storeConn = new URL(storeUrl).openConnection()
        storeConn.setRequestMethod("POST")
        storeConn.setDoOutput(true)
        storeConn.setRequestProperty("Content-Type", "application/json")
        storeConn.outputStream.write(payload.bytes)
        storeConn.inputStream.close()

        when: "we retrieve the value"
        def getUrl = "http://localhost:${appPort}/cache/retrievekey"
        def getConn = new URL(getUrl).openConnection()
        def response = new JsonSlurper().parse(getConn.inputStream)

        then: "value is retrieved"
        response.key == 'retrievekey'
        response.value == 'retrievevalue'
        response.found == true
    }

    def "app should delete value from Redis cache"() {
        given: "we have stored a value"
        def storeUrl = "http://localhost:${appPort}/cache/deletekey"
        def payload = '{"value": "deletevalue"}'

        def storeConn = new URL(storeUrl).openConnection()
        storeConn.setRequestMethod("POST")
        storeConn.setDoOutput(true)
        storeConn.setRequestProperty("Content-Type", "application/json")
        storeConn.outputStream.write(payload.bytes)
        storeConn.inputStream.close()

        when: "we delete the value"
        def deleteUrl = "http://localhost:${appPort}/cache/deletekey"
        def deleteConn = new URL(deleteUrl).openConnection()
        deleteConn.setRequestMethod("DELETE")
        def response = new JsonSlurper().parse(deleteConn.inputStream)

        then: "value is deleted"
        response.key == 'deletekey'
        response.deleted == true
    }

    // ========================================
    // Nginx Reverse Proxy
    // ========================================

    def "nginx should proxy requests to app"() {
        when: "we call app through nginx"
        def url = "http://localhost:${nginxPort}/info"
        def connection = new URL(url).openConnection()
        def response = new JsonSlurper().parse(connection.inputStream)

        then: "request is proxied successfully"
        response.app == 'Multi-Service Orchestration Demo'
        response.services.size() == 4
        response.services.contains('app')
        response.services.contains('postgres')
        response.services.contains('redis')
        response.services.contains('nginx')
    }

    def "nginx should proxy database requests to app"() {
        when: "we get message count through nginx"
        def url = "http://localhost:${nginxPort}/messages/count"
        def connection = new URL(url).openConnection()
        def response = new JsonSlurper().parse(connection.inputStream)

        then: "request is proxied and database is accessed"
        response.count >= 0
    }

    // ========================================
    // Service Dependencies Validation
    // ========================================

    def "app should depend on postgres and redis"() {
        expect: "all dependent services are running before app starts"
        // This is implicitly validated by the fact that app starts successfully
        // and can connect to both postgres and redis
        DockerComposeValidator.isContainerRunning(projectName, 'postgres')
        DockerComposeValidator.isContainerRunning(projectName, 'redis')
        DockerComposeValidator.isServiceHealthyViaCompose(projectName, 'multi-service-app')
    }

    def "nginx should depend on app"() {
        expect: "app is running before nginx starts"
        // This is implicitly validated by the fact that nginx can proxy to app
        DockerComposeValidator.isContainerRunning(projectName, 'multi-service-app')
        DockerComposeValidator.isContainerRunning(projectName, 'nginx')
    }
}
