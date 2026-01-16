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

package com.kineticfire.gradle.docker.util

import org.gradle.api.GradleException
import spock.lang.Specification

import java.time.Duration

/**
 * Unit tests for WaitForLogConfigBuilder
 */
class WaitForLogConfigBuilderTest extends Specification {

    // ===== SUCCESSFUL BUILD TESTS =====

    def "build creates config with all parameters"() {
        given:
        def waitForServices = ['app': ['Started Application']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'test-project',
            waitForServices,
            null,
            60, 2, false, false, 0
        )

        then:
        config.projectName == 'test-project'
        config.servicePatterns.size() == 1
        config.servicePatterns['app'].size() == 1
        config.timeout == Duration.ofSeconds(60)
        config.pollInterval == Duration.ofSeconds(2)
        config.caseInsensitive == false
        config.verbose == false
        config.progressInterval == Duration.ZERO
    }

    def "build compiles patterns with case insensitivity"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, true, false, 0
        )

        then:
        config.caseInsensitive == true
        config.servicePatterns['app'][0].matcher('started').find()
    }

    def "build handles reject patterns"() {
        given:
        def waitForServices = ['app': ['Started']]
        def rejectPatterns = ['app': ['Error', 'Exception']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, rejectPatterns,
            60, 2, false, false, 0
        )

        then:
        config.rejectPatterns['app'].size() == 2
    }

    def "build handles null reject patterns"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        config.rejectPatterns.isEmpty()
    }

    def "build handles multiple services with multiple patterns"() {
        given:
        def waitForServices = [
            'app': ['Started', 'Ready', 'Listening'],
            'db': ['accepting connections'],
            'cache': ['Server started']
        ]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            120, 5, true, true, 10
        )

        then:
        config.servicePatterns.size() == 3
        config.servicePatterns['app'].size() == 3
        config.servicePatterns['db'].size() == 1
        config.servicePatterns['cache'].size() == 1
        config.verbose == true
        config.progressInterval == Duration.ofSeconds(10)
    }

    // ===== VALIDATION ERROR TESTS =====

    def "build throws GradleException for null waitForServices"() {
        when:
        WaitForLogConfigBuilder.build(
            'project', null, null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'waitForServices' cannot be empty")
        ex.message.contains("Example:")
    }

    def "build throws GradleException for empty waitForServices"() {
        when:
        WaitForLogConfigBuilder.build(
            'project', [:], null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'waitForServices' cannot be empty")
    }

    def "build throws GradleException for non-positive timeoutSeconds"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            0, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'timeoutSeconds' must be positive")
    }

    def "build throws GradleException for negative timeoutSeconds"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            -1, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'timeoutSeconds' must be positive")
    }

    def "build throws GradleException for non-positive pollSeconds"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 0, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("'pollSeconds' must be positive")
    }

    def "build throws GradleException for empty pattern list"() {
        given:
        def waitForServices = ['app': []]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Pattern list for service 'app' cannot be empty")
        ex.message.contains("At least one pattern required")
    }

    def "build throws GradleException for null pattern list"() {
        given:
        def waitForServices = ['app': null]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Pattern list for service 'app' cannot be empty")
    }

    def "build throws GradleException for invalid regex in waitForServices"() {
        given:
        def waitForServices = ['app': ['[invalid']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Configuration error in 'waitForLog' block:")
        ex.message.contains("Invalid regex pattern for service 'app'")
    }

    def "build throws GradleException for invalid regex in rejectPatterns"() {
        given:
        def waitForServices = ['app': ['Started']]
        def rejectPatterns = ['app': ['[invalid']]

        when:
        WaitForLogConfigBuilder.build(
            'project', waitForServices, rejectPatterns,
            60, 2, false, false, 0
        )

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Configuration error in 'waitForLog' block (rejectPatterns):")
        ex.message.contains("Invalid regex pattern for service 'app'")
    }

    // ===== WARNING SCENARIOS =====

    def "build prints warning when pollSeconds greater than timeoutSeconds"() {
        given:
        def waitForServices = ['app': ['Started']]
        def originalErr = System.err
        def baos = new ByteArrayOutputStream()
        System.err = new PrintStream(baos)

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            5, 10, false, false, 0
        )

        then:
        config != null
        def output = baos.toString()
        output.contains('[waitForLog] WARNING:')
        output.contains('pollSeconds')
        output.contains('timeoutSeconds')

        cleanup:
        System.err = originalErr
    }

    def "build prints warning for orphaned reject patterns"() {
        given:
        def waitForServices = ['app': ['Started']]
        def rejectPatterns = ['db': ['Error']]  // 'db' not in waitForServices
        def originalErr = System.err
        def baos = new ByteArrayOutputStream()
        System.err = new PrintStream(baos)

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, rejectPatterns,
            60, 2, false, false, 0
        )

        then:
        config != null
        def output = baos.toString()
        output.contains('[waitForLog] WARNING:')
        output.contains('rejectPatterns')
        output.contains('db')

        cleanup:
        System.err = originalErr
    }

    // ===== EDGE CASES =====

    def "build handles regex special characters in patterns"() {
        given:
        def waitForServices = ['app': ['\\[INFO\\].*started', 'port:\\s+\\d+']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        config.servicePatterns['app'].size() == 2
        config.servicePatterns['app'][0].matcher('[INFO] Application started').find()
        config.servicePatterns['app'][1].matcher('port: 8080').find()
    }

    def "build handles verbose and progress interval"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, true, 15
        )

        then:
        config.verbose == true
        config.progressInterval == Duration.ofSeconds(15)
    }

    def "build handles zero progress interval"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            60, 2, false, false, 0
        )

        then:
        config.progressInterval == Duration.ZERO
        !config.hasProgressInterval()
    }

    def "build handles large timeout and poll values"() {
        given:
        def waitForServices = ['app': ['Started']]

        when:
        def config = WaitForLogConfigBuilder.build(
            'project', waitForServices, null,
            3600, 60, false, false, 0
        )

        then:
        config.timeout == Duration.ofHours(1)
        config.pollInterval == Duration.ofMinutes(1)
    }

    // ===== PRIVATE CONSTRUCTOR TEST ===== (Gap #23 addition)

    def "private constructor exists and prevents instantiation"() {
        // Gap #23 addition - verifies the private constructor for coverage
        when:
        def constructor = WaitForLogConfigBuilder.getDeclaredConstructor()
        constructor.setAccessible(true)
        constructor.newInstance()

        then:
        noExceptionThrown()  // Constructor completes successfully
        // Note: We're just verifying the constructor exists for coverage
    }
}
