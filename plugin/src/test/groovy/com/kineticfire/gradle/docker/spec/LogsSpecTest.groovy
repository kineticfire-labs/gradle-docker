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

package com.kineticfire.gradle.docker.spec

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for LogsSpec
 */
class LogsSpecTest extends Specification {

    def project
    def logsSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        logsSpec = project.objects.newInstance(LogsSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        logsSpec != null
        logsSpec.tailLines.get() == 100
    }

    // ===== PROPERTY TESTS =====

    def "writeTo property works correctly"() {
        given:
        def logFile = project.file('compose-logs.txt')

        when:
        logsSpec.writeTo.set(logFile)

        then:
        logsSpec.writeTo.present
        logsSpec.writeTo.get().asFile == logFile
    }

    def "tailLines property works correctly"() {
        when:
        logsSpec.tailLines.set(500)

        then:
        logsSpec.tailLines.present
        logsSpec.tailLines.get() == 500
    }

    def "tailLines has default value"() {
        expect:
        logsSpec.tailLines.present
        logsSpec.tailLines.get() == 100
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def logFile = project.file('application.log')

        when:
        logsSpec.writeTo.set(logFile)
        logsSpec.tailLines.set(1000)

        then:
        logsSpec.writeTo.get().asFile == logFile
        logsSpec.tailLines.get() == 1000
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        given:
        def initialFile = project.file('initial.log')
        def updatedFile = project.file('updated.log')

        when:
        logsSpec.writeTo.set(initialFile)
        logsSpec.tailLines.set(50)

        then:
        logsSpec.writeTo.get().asFile == initialFile
        logsSpec.tailLines.get() == 50

        when:
        logsSpec.writeTo.set(updatedFile)
        logsSpec.tailLines.set(200)

        then:
        logsSpec.writeTo.get().asFile == updatedFile
        logsSpec.tailLines.get() == 200
    }

    // ===== DEFAULT BEHAVIOR TESTS =====

    def "default remains after property access"() {
        given:
        def initialTailLines = logsSpec.tailLines.get()

        expect:
        initialTailLines == 100
        
        // Verify default persists
        logsSpec.tailLines.get() == 100
    }

    def "convention value can be overridden"() {
        when:
        logsSpec.tailLines.set(250)

        then:
        logsSpec.tailLines.get() == 250
    }

    // ===== EDGE CASES =====

    def "writeTo is initially not present"() {
        expect:
        !logsSpec.writeTo.present
    }

    def "various log file types can be set"() {
        given:
        def txtFile = project.file('logs.txt')
        def logFile = project.file('app.log')
        def outFile = project.file('output.out')

        expect:
        [txtFile, logFile, outFile].each { file ->
            logsSpec.writeTo.set(file)
            assert logsSpec.writeTo.get().asFile == file
        }
    }

    def "various tail line values can be set"() {
        expect:
        [0, 1, 10, 50, 100, 500, 1000, 5000].each { lines ->
            logsSpec.tailLines.set(lines)
            assert logsSpec.tailLines.get() == lines
        }
    }

    def "realistic log configurations"() {
        given:
        def debugLogFile = project.file('debug.log')
        def productionLogFile = project.file('prod.log')

        expect:
        // Debug configuration with more lines
        logsSpec.writeTo.set(debugLogFile)
        logsSpec.tailLines.set(1000)
        logsSpec.writeTo.get().asFile == debugLogFile
        logsSpec.tailLines.get() == 1000

        // Production configuration with fewer lines
        logsSpec.writeTo.set(productionLogFile)
        logsSpec.tailLines.set(50)
        logsSpec.writeTo.get().asFile == productionLogFile
        logsSpec.tailLines.get() == 50
    }

    def "zero tail lines is supported"() {
        when:
        logsSpec.tailLines.set(0)

        then:
        logsSpec.tailLines.get() == 0
    }

    def "large tail line values are supported"() {
        when:
        logsSpec.tailLines.set(100000)

        then:
        logsSpec.tailLines.get() == 100000
    }
}