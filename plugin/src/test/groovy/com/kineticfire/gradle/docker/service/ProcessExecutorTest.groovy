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

import spock.lang.Specification
import spock.lang.TempDir
import java.time.Duration

/**
 * Unit tests for ProcessExecutor interface and implementations
 */
class ProcessExecutorTest extends Specification {

    @TempDir
    File tempDir

    def "ProcessResult constructor sets properties correctly"() {
        when:
        def result = new ProcessResult(exitCode, stdout, stderr)

        then:
        result.exitCode == exitCode
        result.stdout == expectedStdout
        result.stderr == expectedStderr
        result.isSuccess() == expectedSuccess

        where:
        exitCode | stdout  | stderr  | expectedStdout | expectedStderr | expectedSuccess
        0        | "out"   | "err"   | "out"          | "err"          | true
        1        | "out"   | "err"   | "out"          | "err"          | false
        0        | null    | null    | ""             | ""             | true
        127      | ""      | "error" | ""             | "error"        | false
    }

    def "ProcessResult toString works correctly"() {
        given:
        def result = new ProcessResult(0, "output", "error")

        when:
        def string = result.toString()

        then:
        string.contains("exitCode=0")
        string.contains("stdout='output'")
        string.contains("stderr='error'")
    }

    def "DefaultProcessExecutor executes simple commands successfully"() {
        given:
        def executor = new DefaultProcessExecutor()

        when:
        def result = executor.execute(['echo', 'hello'])

        then:
        result.isSuccess()
        result.stdout.trim() == 'hello'
        result.stderr == ""
    }

    def "DefaultProcessExecutor handles working directory"() {
        given:
        def executor = new DefaultProcessExecutor()
        def testFile = new File(tempDir, "test.txt")
        testFile.text = "test content"

        when:
        def result = executor.execute(['cat', 'test.txt'], tempDir)

        then:
        result.isSuccess()
        result.stdout.trim() == 'test content'
    }

    def "DefaultProcessExecutor handles timeout parameter"() {
        given:
        def executor = new DefaultProcessExecutor()

        when:
        def result = executor.execute(['echo', 'timeout test'], null, Duration.ofSeconds(30))

        then:
        result.isSuccess()
        result.stdout.trim() == 'timeout test'
    }

    def "DefaultProcessExecutor handles command with multiple arguments"() {
        given:
        def executor = new DefaultProcessExecutor()

        when:
        def result = executor.execute(['echo', 'hello', 'world'])

        then:
        result.isSuccess()
        result.stdout.trim() == 'hello world'
    }

    def "DefaultProcessExecutor captures stderr for failing commands"() {
        given:
        def executor = new DefaultProcessExecutor()

        when:
        def result = executor.execute(['cat', '/nonexistent/file'])

        then:
        !result.isSuccess()
        result.exitCode != 0
        result.stderr.contains("No such file") || result.stderr.contains("cannot access")
    }

    def "DefaultProcessExecutor throws exception for invalid command"() {
        given:
        def executor = new DefaultProcessExecutor()

        when:
        executor.execute(['nonexistent-command-12345'])

        then:
        thrown(RuntimeException)
    }
}