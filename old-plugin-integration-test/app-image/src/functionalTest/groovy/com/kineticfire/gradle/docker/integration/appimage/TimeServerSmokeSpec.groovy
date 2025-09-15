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

package com.kineticfire.gradle.docker.integration.appimage

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

/**
 * Smoke tests for time-server Docker image using suite lifecycle.
 * 
 * Purpose: Quick validation that the Docker image built by gradle-docker plugin works correctly.
 * This demonstrates UC-6 (Docker image creation) and UC-7 (compose orchestration) in a fast,
 * shared-environment pattern. Tests validate basic functionality without deep integration concerns.
 * 
 * Lifecycle: Suite - compose up once for all tests (fastest execution)
 * Network: Uses smokeTest compose stack on port 8080
 */
class TimeServerSmokeSpec extends Specification {
    
    private static final String BASE_URL = "http://localhost:8080"
    private ObjectMapper objectMapper = new ObjectMapper()
    
    def "health endpoint returns healthy status"() {
        when: "health endpoint is called"
        def connection = new URL("${BASE_URL}/health").openConnection()
        connection.requestMethod = 'GET'
        def response = connection.inputStream.text
        def jsonResponse = objectMapper.readTree(response)
        
        then: "service reports healthy"
        connection.responseCode == 200
        jsonResponse.get('status').asText() == 'healthy'
        jsonResponse.has('timestamp')
        jsonResponse.has('version')
    }
    
    def "time endpoint returns current time"() {
        when: "time endpoint is called"
        def connection = new URL("${BASE_URL}/time").openConnection()
        connection.requestMethod = 'GET'
        def response = connection.inputStream.text
        def jsonResponse = objectMapper.readTree(response)
        
        then: "current time is returned in UTC"
        connection.responseCode == 200
        jsonResponse.has('time')
        jsonResponse.get('timezone').asText() == 'UTC'
        jsonResponse.has('epoch')
        jsonResponse.get('epoch').asLong() > 0
    }
    
    def "echo endpoint works correctly"() {
        when: "echo endpoint is called with message"
        def message = "hello-smoke-test"
        def connection = new URL("${BASE_URL}/echo?msg=${message}").openConnection()
        connection.requestMethod = 'GET'
        def response = connection.inputStream.text
        def jsonResponse = objectMapper.readTree(response)
        
        then: "message is echoed back with metadata"
        connection.responseCode == 200
        jsonResponse.get('echo').asText() == message
        jsonResponse.get('length').asInt() == message.length()
        jsonResponse.get('uppercase').asText() == message.toUpperCase()
    }
    
    def "metrics endpoint returns application stats"() {
        when: "metrics endpoint is called"
        def connection = new URL("${BASE_URL}/metrics").openConnection()
        connection.requestMethod = 'GET'
        def response = connection.inputStream.text
        def jsonResponse = objectMapper.readTree(response)
        
        then: "application metrics are returned"
        connection.responseCode == 200
        jsonResponse.has('uptime')
        jsonResponse.get('uptime').asLong() >= 0
        jsonResponse.has('requests')
        jsonResponse.get('requests').asLong() >= 0
        jsonResponse.has('startTime')
    }
}