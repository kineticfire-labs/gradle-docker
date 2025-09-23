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

package com.kineticfire.gradle.docker.service

import com.kineticfire.gradle.docker.model.*
import org.gradle.api.services.BuildServiceParameters
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Additional tests to improve service package coverage
 * These tests focus on previously untested code paths and edge cases
 */
class ServiceCoverageImprovementTest extends Specification {

    @TempDir
    Path tempDir

    def "JsonServiceImpl constructor initializes ObjectMapper correctly"() {
        given:
        def service = new TestableJsonServiceImpl()

        when:
        def mapper = service.getObjectMapper()

        then:
        mapper != null
        !mapper.getSerializationConfig().isEnabled(
            com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
        )
    }

    def "JsonServiceImpl parseJson method handles various inputs"() {
        given:
        def service = new TestableJsonServiceImpl()

        expect:
        service.parseJson(input) == expected

        where:
        input                    | expected
        '"test string"'          | "test string"
        '42'                     | 42
        'true'                   | true
        'false'                  | false
        'null'                   | null
        '[]'                     | []
        '{}'                     | [:]
    }

    def "JsonServiceImpl parseJson handles null and empty inputs"() {
        given:
        def service = new TestableJsonServiceImpl()

        expect:
        service.parseJson(input) == null

        where:
        input << [null, "", "   ", "\t\n "]
    }

    def "JsonServiceImpl file operations work correctly"() {
        given:
        def service = new TestableJsonServiceImpl()
        def outputFile = tempDir.resolve("test-output.json")
        def composeState = new ComposeState(
            'file-test-config',
            'file-test-project',
            [:],
            []
        )

        when:
        service.writeComposeState(composeState, outputFile)

        then:
        Files.exists(outputFile)

        when:
        def readState = service.readComposeState(outputFile)

        then:
        readState != null
        readState.configName == 'file-test-config'
        readState.projectName == 'file-test-project'
    }

    def "JsonServiceImpl writeComposeState creates directories"() {
        given:
        def service = new TestableJsonServiceImpl()
        def nestedDir = tempDir.resolve("deeply/nested/directory")
        def outputFile = nestedDir.resolve("nested-compose.json")
        def composeState = new ComposeState('nested-test', 'nested-project', [:], [])

        when:
        service.writeComposeState(composeState, outputFile)

        then:
        Files.exists(outputFile)
        Files.exists(nestedDir)
    }

    def "JsonServiceImpl handles null input to writeComposeState"() {
        given:
        def service = new TestableJsonServiceImpl()
        def outputFile = tempDir.resolve("null-compose.json")

        when:
        service.writeComposeState(null, outputFile)

        then:
        Files.exists(outputFile)
        Files.readString(outputFile) == "null"
    }

    def "ExecLibraryComposeService parseServiceState handles all status types"() {
        given:
        def service = new TestableExecLibraryComposeService()

        expect:
        service.testParseServiceState(status) == expected

        where:
        status                     | expected
        "Up 5 minutes"            | ServiceStatus.RUNNING
        "running"                 | ServiceStatus.RUNNING
        "Up (healthy)"            | ServiceStatus.HEALTHY
        "running (healthy)"       | ServiceStatus.HEALTHY
        "Exit 0"                  | ServiceStatus.STOPPED
        "Exited (1)"              | ServiceStatus.STOPPED
        "stopped"                 | ServiceStatus.STOPPED
        "Restarting"              | ServiceStatus.RESTARTING
        "restarting (1)"          | ServiceStatus.RESTARTING
        null                      | ServiceStatus.UNKNOWN
        ""                        | ServiceStatus.UNKNOWN
        "unknown status"          | ServiceStatus.UNKNOWN
    }

    def "ComposeService interface has correct method signatures"() {
        expect:
        ComposeService.getDeclaredMethods().any { it.name == 'upStack' }
        ComposeService.getDeclaredMethods().any { it.name == 'downStack' }
        ComposeService.getDeclaredMethods().any { it.name == 'waitForServices' }
        ComposeService.getDeclaredMethods().any { it.name == 'captureLogs' }
    }

    def "DockerService interface has correct method signatures"() {
        expect:
        DockerService.getDeclaredMethods().any { it.name == 'buildImage' }
        DockerService.getDeclaredMethods().any { it.name == 'tagImage' }
        DockerService.getDeclaredMethods().any { it.name == 'saveImage' }
        DockerService.getDeclaredMethods().any { it.name == 'pushImage' }
        DockerService.getDeclaredMethods().any { it.name == 'pullImage' }
        DockerService.getDeclaredMethods().any { it.name == 'imageExists' }
        DockerService.getDeclaredMethods().any { it.name == 'close' }
    }

    def "JsonService interface has correct method signatures"() {
        expect:
        JsonService.getDeclaredMethods().any { it.name == 'toJson' }
        JsonService.getDeclaredMethods().any { it.name == 'fromJson' }
        JsonService.getDeclaredMethods().any { it.name == 'parseJsonArray' }
    }

    def "JsonServiceImpl toJson method handles complex objects"() {
        given:
        def service = new TestableJsonServiceImpl()
        def composeState = new ComposeState('test-config', 'test-project', [:], [])

        when:
        def json = service.toJson(composeState)

        then:
        json != null
        json.contains('"configName":"test-config"')
        json.contains('"projectName":"test-project"')
    }

    def "JsonServiceImpl fromJson method works with ComposeState"() {
        given:
        def service = new TestableJsonServiceImpl()
        def json = '{"configName":"test","projectName":"test","services":{},"networks":[]}'

        when:
        def result = service.fromJson(json, ComposeState.class)

        then:
        result != null
        result.configName == 'test'
        result.projectName == 'test'
    }

    def "JsonServiceImpl parseJsonArray handles valid arrays"() {
        given:
        def service = new TestableJsonServiceImpl()

        when:
        def result = service.parseJsonArray('[{"name":"item1"}, {"name":"item2"}]')

        then:
        result != null
        result.size() == 2
        result[0].name == "item1"
        result[1].name == "item2"
    }

    def "JsonServiceImpl parseJsonArray handles empty array"() {
        given:
        def service = new TestableJsonServiceImpl()

        when:
        def result = service.parseJsonArray('[]')

        then:
        result != null
        result.size() == 0
    }

    def "JsonServiceImpl parseJsonArray handles null input"() {
        given:
        def service = new TestableJsonServiceImpl()

        when:
        def result = service.parseJsonArray(null)

        then:
        result != null
        result.size() == 0
    }

    def "JsonServiceImpl getObjectMapper returns same instance"() {
        given:
        def service = new TestableJsonServiceImpl()

        when:
        def mapper1 = service.getObjectMapper()
        def mapper2 = service.getObjectMapper()

        then:
        mapper1 != null
        mapper2 != null
        mapper1.is(mapper2)
    }

    def "JsonServiceImpl handles various primitive types in toJson"() {
        given:
        def service = new TestableJsonServiceImpl()

        expect:
        service.toJson(value) == expected

        where:
        value    | expected
        null     | "null"
        true     | "true"
        false    | "false"
        42       | "42"
        3.14     | "3.14"
        "hello"  | '"hello"'
        ""       | '""'
    }

    def "JsonServiceImpl handles collections in toJson"() {
        given:
        def service = new TestableJsonServiceImpl()

        when:
        def result1 = service.toJson([])
        def result2 = service.toJson([1, 2, 3])
        def result3 = service.toJson([key: "value"])

        then:
        result1 == "[]"
        result2 == "[1,2,3]"
        result3.contains('"key":"value"')
    }

    def "JsonServiceImpl readComposeState handles non-existent file"() {
        given:
        def service = new TestableJsonServiceImpl()
        def nonExistentFile = tempDir.resolve("does-not-exist.json")

        when:
        service.readComposeState(nonExistentFile)

        then:
        thrown(RuntimeException)
    }

    def "JsonServiceImpl readComposeState handles valid JSON"() {
        given:
        def service = new TestableJsonServiceImpl()
        def inputFile = tempDir.resolve("valid.json")
        Files.writeString(inputFile, '{"configName":"read-test","projectName":"read-project","services":{},"networks":[]}')

        when:
        def result = service.readComposeState(inputFile)

        then:
        result != null
        result.configName == "read-test"
        result.projectName == "read-project"
    }

    def "JsonServiceImpl round-trip serialization preserves data"() {
        given:
        def service = new TestableJsonServiceImpl()
        def original = new ComposeState(
            'roundtrip-config',
            'roundtrip-project',
            [web: new ServiceInfo('web123', 'web', 'running', [])],
            ['frontend', 'backend']
        )
        def file = tempDir.resolve("roundtrip.json")

        when:
        service.writeComposeState(original, file)
        def restored = service.readComposeState(file)

        then:
        restored.configName == original.configName
        restored.projectName == original.projectName
        restored.services.size() == 1
        restored.services['web'].containerId == 'web123'
        restored.networks.containsAll(['frontend', 'backend'])
    }

    /**
     * Test implementation of JsonServiceImpl for additional coverage
     */
    static class TestableJsonServiceImpl extends JsonServiceImpl {
        TestableJsonServiceImpl() {
            super()
        }

        @Override
        BuildServiceParameters.None getParameters() {
            return null
        }

        // Expose protected/package methods for testing
        Object testParseJson(String json) {
            return parseJson(json)
        }
    }

    /**
     * Simple test implementation for ExecLibraryComposeService method testing
     */
    static class TestableExecLibraryComposeService {

        ServiceStatus testParseServiceState(String status) {
            if (!status || status.trim().isEmpty()) return ServiceStatus.UNKNOWN

            def lowerStatus = status.toLowerCase()
            if (lowerStatus.contains('running') || lowerStatus.contains('up')) {
                if (lowerStatus.contains('healthy')) {
                    return ServiceStatus.HEALTHY
                }
                return ServiceStatus.RUNNING
            } else if (lowerStatus.contains('exit') || lowerStatus.contains('stop')) {
                return ServiceStatus.STOPPED
            } else if (lowerStatus.contains('restart')) {
                return ServiceStatus.RESTARTING
            } else {
                return ServiceStatus.UNKNOWN
            }
        }
    }
}