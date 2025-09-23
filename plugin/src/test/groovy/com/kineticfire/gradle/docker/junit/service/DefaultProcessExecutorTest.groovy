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

import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/**
 * Unit tests for DefaultProcessExecutor to achieve 100% coverage.
 */
class DefaultProcessExecutorTest extends Specification {

    @Subject
    DefaultProcessExecutor executor

    def setup() {
        executor = new DefaultProcessExecutor()
    }

    def "constructor creates instance successfully"() {
        expect:
        executor != null
    }

    def "execute delegates to executeInDirectory with null directory"() {
        given:
        String[] command = ["echo", "test"]

        when:
        ProcessExecutor.ProcessResult result = executor.execute(command)

        then:
        result != null
        result.exitCode == 0
        result.output.contains("test")
    }

    def "executeWithTimeout delegates to executeInDirectoryWithTimeout with null directory"() {
        given:
        String[] command = ["echo", "timeout-test"]

        when:
        ProcessExecutor.ProcessResult result = executor.executeWithTimeout(5, TimeUnit.SECONDS, command)

        then:
        result != null
        result.exitCode == 0
        result.output.contains("timeout-test")
    }

    def "executeInDirectory executes command successfully with null directory"() {
        given:
        String[] command = ["echo", "hello"]

        when:
        ProcessExecutor.ProcessResult result = executor.executeInDirectory(null, command)

        then:
        result != null
        result.exitCode == 0
        result.output.contains("hello")
    }

    def "executeInDirectory executes command successfully with specified directory"() {
        given:
        File tempDir = File.createTempDir()
        String[] command = ["pwd"]

        when:
        ProcessExecutor.ProcessResult result = executor.executeInDirectory(tempDir, command)

        then:
        result != null
        result.exitCode == 0
        result.output.contains(tempDir.absolutePath)

        cleanup:
        tempDir.deleteDir()
    }

    def "executeInDirectory handles command failure with non-zero exit code"() {
        given:
        String[] command = ["false"]  // Command that always returns exit code 1

        when:
        ProcessExecutor.ProcessResult result = executor.executeInDirectory(null, command)

        then:
        result != null
        result.exitCode == 1
        result.output != null
    }

    def "executeInDirectoryWithTimeout executes command successfully within timeout"() {
        given:
        String[] command = ["echo", "timeout-success"]

        when:
        ProcessExecutor.ProcessResult result = executor.executeInDirectoryWithTimeout(null, 5, TimeUnit.SECONDS, command)

        then:
        result != null
        result.exitCode == 0
        result.output.contains("timeout-success")
    }

    def "executeInDirectoryWithTimeout executes command successfully with specified directory"() {
        given:
        File tempDir = File.createTempDir()
        String[] command = ["pwd"]

        when:
        ProcessExecutor.ProcessResult result = executor.executeInDirectoryWithTimeout(tempDir, 5, TimeUnit.SECONDS, command)

        then:
        result != null
        result.exitCode == 0
        result.output.contains(tempDir.absolutePath)

        cleanup:
        tempDir.deleteDir()
    }

    def "executeInDirectoryWithTimeout throws InterruptedException on timeout"() {
        given:
        String[] command = ["sleep", "10"]  // Command that takes longer than timeout

        when:
        executor.executeInDirectoryWithTimeout(null, 1, TimeUnit.SECONDS, command)

        then:
        InterruptedException ex = thrown()
        ex.message.contains("Process timed out after 1 SECONDS")
    }

    def "executeInDirectoryWithTimeout handles command failure within timeout"() {
        given:
        String[] command = ["false"]  // Command that fails quickly

        when:
        ProcessExecutor.ProcessResult result = executor.executeInDirectoryWithTimeout(null, 5, TimeUnit.SECONDS, command)

        then:
        result != null
        result.exitCode == 1
        result.output != null
    }

    def "readProcessOutput reads multiple lines correctly"() {
        given:
        String[] command = ["echo", "-e", "line1\\nline2\\nline3"]

        when:
        ProcessExecutor.ProcessResult result = executor.executeInDirectory(null, command)

        then:
        result != null
        result.exitCode == 0
        result.output.contains("line1")
        result.output.contains("line2")
        result.output.contains("line3")
    }

    def "readProcessOutput handles empty output"() {
        given:
        String[] command = ["true"]  // Command with no output

        when:
        ProcessExecutor.ProcessResult result = executor.executeInDirectory(null, command)

        then:
        result != null
        result.exitCode == 0
        result.output == ""
    }

    def "readProcessOutput method exists and is accessible via reflection"() {
        when:
        Method readMethod = DefaultProcessExecutor.getDeclaredMethod("readProcessOutput", Process.class)
        readMethod.setAccessible(true)

        then:
        readMethod != null
        readMethod.name == "readProcessOutput"
        readMethod.parameterCount == 1
        readMethod.parameterTypes[0] == Process.class
    }

    def "execute handles various command arguments"() {
        given:
        String[] command = ["echo", "arg1", "arg2", "arg3"]

        when:
        ProcessExecutor.ProcessResult result = executor.execute(command)

        then:
        result != null
        result.exitCode == 0
        result.output.contains("arg1")
        result.output.contains("arg2")
        result.output.contains("arg3")
    }

    def "executeWithTimeout handles various time units"() {
        expect:
        ProcessExecutor.ProcessResult result = executor.executeWithTimeout(timeout, unit, ["echo", "test"] as String[])
        result.exitCode == 0
        result.output.contains("test")

        where:
        timeout | unit
        1000    | TimeUnit.MILLISECONDS
        1       | TimeUnit.SECONDS
        1       | TimeUnit.MINUTES
    }

}