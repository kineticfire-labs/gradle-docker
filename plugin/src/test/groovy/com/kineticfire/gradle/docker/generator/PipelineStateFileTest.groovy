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

package com.kineticfire.gradle.docker.generator

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for PipelineStateFile
 */
class PipelineStateFileTest extends Specification {

    @TempDir
    Path tempDir

    File tempFile

    def setup() {
        tempFile = tempDir.resolve("test-state.json").toFile()
    }

    // ===== TEST RESULT TESTS =====

    def "writeTestResult creates file with correct content"() {
        when:
        PipelineStateFile.writeTestResult(tempFile, true, "Tests passed", 1234567890L)

        then:
        tempFile.exists()
        def content = tempFile.text
        content.contains('"success": true')
        content.contains('"message": "Tests passed"')
        content.contains('"timestamp": 1234567890')
    }

    def "writeTestResult handles failure result"() {
        when:
        PipelineStateFile.writeTestResult(tempFile, false, "Tests failed", 9876543210L)

        then:
        tempFile.exists()
        def content = tempFile.text
        content.contains('"success": false')
        content.contains('"message": "Tests failed"')
        content.contains('"timestamp": 9876543210')
    }

    def "writeTestResult handles null message"() {
        when:
        PipelineStateFile.writeTestResult(tempFile, true, null, 1234567890L)

        then:
        tempFile.exists()
        def content = tempFile.text
        content.contains('"message": ""')
    }

    def "writeTestResult creates parent directories"() {
        given:
        def nestedFile = tempDir.resolve("nested/dir/test-state.json").toFile()

        when:
        PipelineStateFile.writeTestResult(nestedFile, true, "Test", 123L)

        then:
        nestedFile.exists()
        nestedFile.parentFile.isDirectory()
    }

    def "readTestResult reads file correctly"() {
        given:
        PipelineStateFile.writeTestResult(tempFile, true, "Tests passed", 1234567890L)

        when:
        def result = PipelineStateFile.readTestResult(tempFile)

        then:
        result.success == true
        result.message == "Tests passed"
        result.timestamp == 1234567890L
    }

    def "readTestResult handles failure result"() {
        given:
        PipelineStateFile.writeTestResult(tempFile, false, "Tests failed", 9876543210L)

        when:
        def result = PipelineStateFile.readTestResult(tempFile)

        then:
        result.success == false
        result.message == "Tests failed"
        result.timestamp == 9876543210L
    }

    def "readTestResult throws exception for non-existent file"() {
        given:
        def nonExistent = tempDir.resolve("non-existent.json").toFile()

        when:
        PipelineStateFile.readTestResult(nonExistent)

        then:
        thrown(IllegalArgumentException)
    }

    def "readTestResult throws exception for null file"() {
        when:
        PipelineStateFile.readTestResult(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "readTestResult handles malformed JSON"() {
        given:
        tempFile.text = "not valid json"

        when:
        PipelineStateFile.readTestResult(tempFile)

        then:
        thrown(IllegalArgumentException)
    }

    def "readTestResult handles partial JSON data"() {
        given:
        tempFile.text = '{"success": true}'

        when:
        def result = PipelineStateFile.readTestResult(tempFile)

        then:
        result.success == true
        result.message == ""
        result.timestamp == 0L
    }

    def "isTestSuccessful returns true for successful test"() {
        given:
        PipelineStateFile.writeTestResult(tempFile, true, "Passed", 123L)

        expect:
        PipelineStateFile.isTestSuccessful(tempFile) == true
    }

    def "isTestSuccessful returns false for failed test"() {
        given:
        PipelineStateFile.writeTestResult(tempFile, false, "Failed", 123L)

        expect:
        PipelineStateFile.isTestSuccessful(tempFile) == false
    }

    def "isTestSuccessful returns false for non-existent file"() {
        given:
        def nonExistent = tempDir.resolve("non-existent.json").toFile()

        expect:
        PipelineStateFile.isTestSuccessful(nonExistent) == false
    }

    def "isTestSuccessful returns false for malformed file"() {
        given:
        tempFile.text = "invalid json"

        expect:
        PipelineStateFile.isTestSuccessful(tempFile) == false
    }

    // ===== BUILD RESULT TESTS =====

    def "writeBuildResult creates file with correct content"() {
        when:
        PipelineStateFile.writeBuildResult(tempFile, "myapp", ["latest", "1.0.0"], 1234567890L)

        then:
        tempFile.exists()
        def content = tempFile.text
        content.contains('"imageName": "myapp"')
        content.contains('"tags"')
        content.contains('"latest"')
        content.contains('"1.0.0"')
        content.contains('"timestamp": 1234567890')
    }

    def "writeBuildResult handles null imageName"() {
        when:
        PipelineStateFile.writeBuildResult(tempFile, null, ["latest"], 123L)

        then:
        tempFile.exists()
        def content = tempFile.text
        content.contains('"imageName": ""')
    }

    def "writeBuildResult handles null tags"() {
        when:
        PipelineStateFile.writeBuildResult(tempFile, "myapp", null, 123L)

        then:
        tempFile.exists()
        def content = tempFile.text
        content.contains('"tags": [')
    }

    def "writeBuildResult without timestamp uses current time"() {
        when:
        def beforeTime = System.currentTimeMillis()
        PipelineStateFile.writeBuildResult(tempFile, "myapp", ["latest"])
        def afterTime = System.currentTimeMillis()
        def result = PipelineStateFile.readBuildResult(tempFile)

        then:
        result.timestamp >= beforeTime
        result.timestamp <= afterTime
    }

    def "readBuildResult reads file correctly"() {
        given:
        PipelineStateFile.writeBuildResult(tempFile, "myapp", ["latest", "1.0.0"], 1234567890L)

        when:
        def result = PipelineStateFile.readBuildResult(tempFile)

        then:
        result.imageName == "myapp"
        result.tags == ["latest", "1.0.0"]
        result.timestamp == 1234567890L
    }

    def "readBuildResult handles empty tags list"() {
        given:
        PipelineStateFile.writeBuildResult(tempFile, "myapp", [], 123L)

        when:
        def result = PipelineStateFile.readBuildResult(tempFile)

        then:
        result.imageName == "myapp"
        result.tags == []
    }

    def "readBuildResult throws exception for non-existent file"() {
        given:
        def nonExistent = tempDir.resolve("non-existent.json").toFile()

        when:
        PipelineStateFile.readBuildResult(nonExistent)

        then:
        thrown(IllegalArgumentException)
    }

    def "readBuildResult handles partial JSON data"() {
        given:
        tempFile.text = '{"imageName": "test"}'

        when:
        def result = PipelineStateFile.readBuildResult(tempFile)

        then:
        result.imageName == "test"
        result.tags == []
        result.timestamp == 0L
    }

    // ===== GENERIC STATE TESTS =====

    def "writeState creates file with map data"() {
        given:
        def data = [key1: "value1", key2: 42, key3: true]

        when:
        PipelineStateFile.writeState(tempFile, data)

        then:
        tempFile.exists()
        def content = tempFile.text
        content.contains('"key1": "value1"')
        content.contains('"key2": 42')
        content.contains('"key3": true')
    }

    def "writeState handles nested maps"() {
        given:
        def data = [outer: [inner: "value"]]

        when:
        PipelineStateFile.writeState(tempFile, data)
        def result = PipelineStateFile.readState(tempFile)

        then:
        result.outer.inner == "value"
    }

    def "writeState handles lists"() {
        given:
        def data = [items: ["a", "b", "c"]]

        when:
        PipelineStateFile.writeState(tempFile, data)
        def result = PipelineStateFile.readState(tempFile)

        then:
        result.items == ["a", "b", "c"]
    }

    def "readState reads map data correctly"() {
        given:
        def data = [key1: "value1", key2: 42]
        PipelineStateFile.writeState(tempFile, data)

        when:
        def result = PipelineStateFile.readState(tempFile)

        then:
        result.key1 == "value1"
        result.key2 == 42
    }

    def "readState throws exception for non-existent file"() {
        given:
        def nonExistent = tempDir.resolve("non-existent.json").toFile()

        when:
        PipelineStateFile.readState(nonExistent)

        then:
        thrown(IllegalArgumentException)
    }

    // ===== DATA CLASS TESTS =====

    def "TestResultData equals works correctly"() {
        given:
        def data1 = new PipelineStateFile.TestResultData(success: true, message: "test", timestamp: 123L)
        def data2 = new PipelineStateFile.TestResultData(success: true, message: "test", timestamp: 123L)
        def data3 = new PipelineStateFile.TestResultData(success: false, message: "test", timestamp: 123L)

        expect:
        data1 == data2
        data1 != data3
        data1 == data1
    }

    def "TestResultData equals handles different types"() {
        given:
        def data = new PipelineStateFile.TestResultData(success: true, message: "test", timestamp: 123L)

        expect:
        data != "string"
        data != null
        data != 123
    }

    def "TestResultData hashCode is consistent"() {
        given:
        def data1 = new PipelineStateFile.TestResultData(success: true, message: "test", timestamp: 123L)
        def data2 = new PipelineStateFile.TestResultData(success: true, message: "test", timestamp: 123L)

        expect:
        data1.hashCode() == data2.hashCode()
    }

    def "TestResultData toString returns readable format"() {
        given:
        def data = new PipelineStateFile.TestResultData(success: true, message: "test", timestamp: 123L)

        when:
        def str = data.toString()

        then:
        str.contains("success=true")
        str.contains("message='test'")
        str.contains("timestamp=123")
    }

    def "BuildResultData equals works correctly"() {
        given:
        def data1 = new PipelineStateFile.BuildResultData(imageName: "app", tags: ["v1"], timestamp: 123L)
        def data2 = new PipelineStateFile.BuildResultData(imageName: "app", tags: ["v1"], timestamp: 123L)
        def data3 = new PipelineStateFile.BuildResultData(imageName: "app", tags: ["v2"], timestamp: 123L)

        expect:
        data1 == data2
        data1 != data3
        data1 == data1
    }

    def "BuildResultData equals handles different types"() {
        given:
        def data = new PipelineStateFile.BuildResultData(imageName: "app", tags: ["v1"], timestamp: 123L)

        expect:
        data != "string"
        data != null
        data != 123
    }

    def "BuildResultData hashCode is consistent"() {
        given:
        def data1 = new PipelineStateFile.BuildResultData(imageName: "app", tags: ["v1"], timestamp: 123L)
        def data2 = new PipelineStateFile.BuildResultData(imageName: "app", tags: ["v1"], timestamp: 123L)

        expect:
        data1.hashCode() == data2.hashCode()
    }

    def "BuildResultData toString returns readable format"() {
        given:
        def data = new PipelineStateFile.BuildResultData(imageName: "app", tags: ["v1", "v2"], timestamp: 123L)

        when:
        def str = data.toString()

        then:
        str.contains("imageName='app'")
        str.contains("tags=[v1, v2]")
        str.contains("timestamp=123")
    }

    // ===== ERROR HANDLING TESTS =====

    def "writeState throws exception when file cannot be written"() {
        given:
        def readOnlyDir = tempDir.resolve("readonly").toFile()
        readOnlyDir.mkdirs()
        readOnlyDir.setWritable(false)
        def file = new File(readOnlyDir, "test.json")

        when:
        PipelineStateFile.writeState(file, [key: "value"])

        then:
        thrown(IllegalStateException)

        cleanup:
        readOnlyDir.setWritable(true)
    }
}
