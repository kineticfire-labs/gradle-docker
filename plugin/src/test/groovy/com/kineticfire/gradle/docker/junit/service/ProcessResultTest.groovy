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

package com.kineticfire.gradle.docker.junit.service

import spock.lang.Specification
import spock.lang.Subject

/**
 * Unit tests for ProcessExecutor.ProcessResult inner class to achieve 100% coverage.
 */
class ProcessResultTest extends Specification {

    def "constructor creates ProcessResult with exit code and output"() {
        given:
        int exitCode = 0
        String output = "Process executed successfully"

        when:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        result != null
        result.getExitCode() == exitCode
        result.getOutput() == output
    }

    def "constructor handles non-zero exit code"() {
        given:
        int exitCode = 1
        String output = "Process failed with error"

        when:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        result != null
        result.getExitCode() == exitCode
        result.getOutput() == output
    }

    def "constructor handles negative exit code"() {
        given:
        int exitCode = -1
        String output = "Process terminated abnormally"

        when:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        result != null
        result.getExitCode() == exitCode
        result.getOutput() == output
    }

    def "constructor handles null output"() {
        given:
        int exitCode = 0
        String output = null

        when:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        result != null
        result.getExitCode() == exitCode
        result.getOutput() == null
    }

    def "constructor handles empty output"() {
        given:
        int exitCode = 0
        String output = ""

        when:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        result != null
        result.getExitCode() == exitCode
        result.getOutput() == ""
    }

    def "constructor handles multiline output"() {
        given:
        int exitCode = 0
        String output = "Line 1\nLine 2\nLine 3\n"

        when:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        result != null
        result.getExitCode() == exitCode
        result.getOutput() == output
        result.getOutput().contains("\n")
    }

    def "constructor handles large output"() {
        given:
        int exitCode = 0
        String output = "x" * 10000  // Large string

        when:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        result != null
        result.getExitCode() == exitCode
        result.getOutput() == output
        result.getOutput().length() == 10000
    }

    def "constructor handles unicode output"() {
        given:
        int exitCode = 0
        String output = "Unicode test: 测试 🚀 αβγ"

        when:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        result != null
        result.getExitCode() == exitCode
        result.getOutput() == output
    }

    def "getExitCode returns correct value for various exit codes"() {
        expect:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, "output")
        result.getExitCode() == exitCode

        where:
        exitCode << [0, 1, 2, 127, 128, 255, -1, -128, Integer.MAX_VALUE, Integer.MIN_VALUE]
    }

    def "getOutput returns correct value for various outputs"() {
        expect:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(0, output)
        result.getOutput() == output

        where:
        output << [
            "simple output",
            "",
            null,
            "multi\nline\noutput",
            "output with spaces and special chars !@#\$%^&*()",
            "unicode 测试 🚀",
            "very long output" * 100
        ]
    }

    def "two ProcessResult instances with same values are independent"() {
        given:
        int exitCode = 42
        String output = "test output"

        when:
        ProcessExecutor.ProcessResult result1 = new ProcessExecutor.ProcessResult(exitCode, output)
        ProcessExecutor.ProcessResult result2 = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        result1 != result2  // Different instances
        result1.getExitCode() == result2.getExitCode()
        result1.getOutput() == result2.getOutput()
    }

    def "ProcessResult is immutable after construction"() {
        given:
        int exitCode = 0
        String output = "immutable test"

        when:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)

        then:
        // Values should remain the same on multiple calls
        result.getExitCode() == exitCode
        result.getExitCode() == exitCode  // Second call
        result.getOutput() == output
        result.getOutput() == output  // Second call
    }

    def "ProcessResult works with typical success scenarios"() {
        expect:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(0, "Command executed successfully")
        result.getExitCode() == 0
        result.getOutput().contains("success")
    }

    def "ProcessResult works with typical failure scenarios"() {
        expect:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(1, "Command failed: File not found")
        result.getExitCode() == 1
        result.getOutput().contains("failed")
    }

    def "ProcessResult handles edge case combinations"() {
        expect:
        ProcessExecutor.ProcessResult result = new ProcessExecutor.ProcessResult(exitCode, output)
        result.getExitCode() == exitCode
        result.getOutput() == output

        where:
        exitCode | output
        0        | null
        1        | ""
        -1       | "error output"
        255      | "multi\nline\nerror"
        0        | "success with unicode 🎉"
    }
}