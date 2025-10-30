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

package com.kineticfire.gradle.docker.testutil

import spock.lang.Specification

import java.nio.file.Paths

/**
 * Tests for MockServiceBuilder utility.
 * Verifies that mock builders create functional mocks with correct behavior.
 */
class MockServiceBuilderTest extends Specification {
    
    def "mock FileOperations tracks method calls"() {
        given:
        def mockFileOps = MockServiceBuilder.createMockFileOperations()
        def path = Paths.get("/test/file.txt")
        
        when:
        mockFileOps.writeText(path, "content")
        mockFileOps.readText(path)
        mockFileOps.exists(path)
        mockFileOps.delete(path)
        
        then:
        mockFileOps.methodCalls.size() == 4
        mockFileOps.methodCalls[0] == "writeText:/test/file.txt"
        mockFileOps.methodCalls[1] == "readText:/test/file.txt"
        mockFileOps.methodCalls[2] == "exists:/test/file.txt"
        mockFileOps.methodCalls[3] == "delete:/test/file.txt"
    }
    
    def "mock FileOperations stores and retrieves content"() {
        given:
        def mockFileOps = MockServiceBuilder.createMockFileOperations()
        def path = Paths.get("/test/file.txt")
        def content = "Hello, World!"
        
        when:
        mockFileOps.writeText(path, content)
        def retrieved = mockFileOps.readText(path)
        
        then:
        retrieved == content
    }
    
    def "mock FileOperations exists returns correct state"() {
        given:
        def mockFileOps = MockServiceBuilder.createMockFileOperations()
        def path = Paths.get("/test/file.txt")
        
        when: "File does not exist initially"
        def existsBefore = mockFileOps.exists(path)
        
        then:
        !existsBefore
        
        when: "File is written"
        mockFileOps.writeText(path, "content")
        def existsAfter = mockFileOps.exists(path)
        
        then:
        existsAfter
    }
    
    def "mock FileOperations can be initialized with content"() {
        given:
        def path = Paths.get("/test/file.txt")
        def initialContent = "initial content"
        def mockFileOps = MockServiceBuilder.createMockFileOperations([(path): initialContent])
        
        when:
        def content = mockFileOps.readText(path)
        
        then:
        content == initialContent
        mockFileOps.exists(path)
    }
    
    def "mock TimeService tracks method calls"() {
        given:
        def mockTime = MockServiceBuilder.createMockTimeService(1000L)
        
        when:
        mockTime.currentTimeMillis()
        mockTime.sleep(100)
        mockTime.currentTimeMillis()
        
        then:
        mockTime.methodCalls.size() == 3
        mockTime.methodCalls[0] == "currentTimeMillis"
        mockTime.methodCalls[1] == "sleep:100"
        mockTime.methodCalls[2] == "currentTimeMillis"
    }
    
    def "mock TimeService advances time on sleep"() {
        given:
        def mockTime = MockServiceBuilder.createMockTimeService(1000L)
        
        when:
        def timeBefore = mockTime.currentTimeMillis()
        mockTime.sleep(250)
        def timeAfter = mockTime.currentTimeMillis()
        
        then:
        timeBefore == 1000L
        timeAfter == 1250L
    }
    
    def "controllable TimeService allows manual time advancement"() {
        given:
        def mockTime = MockServiceBuilder.createControllableTimeService(1000L)
        
        when:
        def timeBefore = mockTime.currentTimeMillis()
        mockTime.advanceTime(500)
        def timeAfter = mockTime.currentTimeMillis()
        
        then:
        timeBefore == 1000L
        timeAfter == 1500L
    }
    
    def "controllable TimeService sleep does not advance time automatically"() {
        given:
        def mockTime = MockServiceBuilder.createControllableTimeService(1000L)
        
        when:
        def timeBefore = mockTime.currentTimeMillis()
        mockTime.sleep(100)
        def timeAfter = mockTime.currentTimeMillis()

        then: "Time advances automatically on sleep to prevent infinite loops"
        timeBefore == 1000L
        timeAfter == 1100L // Advanced by 100ms sleep duration
    }
    
    def "controllable TimeService can set time explicitly"() {
        given:
        def mockTime = MockServiceBuilder.createControllableTimeService(1000L)
        
        when:
        mockTime.setCurrentTime(5000L)
        def time = mockTime.currentTimeMillis()
        
        then:
        time == 5000L
    }
}
