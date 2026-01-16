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
 * Unit tests for WaitForLogSpec
 */
class WaitForLogSpecTest extends Specification {

    def project
    def waitForLogSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        waitForLogSpec = project.objects.newInstance(WaitForLogSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with ObjectFactory"() {
        expect:
        waitForLogSpec != null
    }

    def "constructor creates all properties"() {
        expect:
        waitForLogSpec.waitForServices != null
        waitForLogSpec.rejectPatterns != null
        waitForLogSpec.timeoutSeconds != null
        waitForLogSpec.pollSeconds != null
        waitForLogSpec.caseInsensitive != null
        waitForLogSpec.verbose != null
        waitForLogSpec.progressIntervalSeconds != null
    }

    // ===== DEFAULT CONVENTION TESTS =====

    def "waitForServices has empty map as default"() {
        // Note: MapProperty in Gradle 9 has an implicit empty map default
        // Validation in WaitForLogConfigBuilder catches empty maps at build time
        expect:
        waitForLogSpec.waitForServices.present
        waitForLogSpec.waitForServices.get() == [:]
    }

    def "rejectPatterns defaults to empty map"() {
        expect:
        waitForLogSpec.rejectPatterns.present
        waitForLogSpec.rejectPatterns.get() == [:]
    }

    def "timeoutSeconds defaults to 60"() {
        expect:
        waitForLogSpec.timeoutSeconds.present
        waitForLogSpec.timeoutSeconds.get() == 60
    }

    def "pollSeconds defaults to 2"() {
        expect:
        waitForLogSpec.pollSeconds.present
        waitForLogSpec.pollSeconds.get() == 2
    }

    def "caseInsensitive defaults to false"() {
        expect:
        waitForLogSpec.caseInsensitive.present
        waitForLogSpec.caseInsensitive.get() == false
    }

    def "verbose defaults to false"() {
        expect:
        waitForLogSpec.verbose.present
        waitForLogSpec.verbose.get() == false
    }

    def "progressIntervalSeconds defaults to 0"() {
        expect:
        waitForLogSpec.progressIntervalSeconds.present
        waitForLogSpec.progressIntervalSeconds.get() == 0
    }

    // ===== PROPERTY SETTING TESTS =====

    def "waitForServices can be set with single service"() {
        when:
        waitForLogSpec.waitForServices.set(['app': ['Started Application']])

        then:
        waitForLogSpec.waitForServices.present
        waitForLogSpec.waitForServices.get() == ['app': ['Started Application']]
    }

    def "waitForServices can be set with multiple services"() {
        given:
        def services = [
            'app': ['Started Application', 'Listening on port'],
            'db': ['ready for connections'],
            'cache': ['Server started', 'Accepting connections']
        ]

        when:
        waitForLogSpec.waitForServices.set(services)

        then:
        waitForLogSpec.waitForServices.present
        waitForLogSpec.waitForServices.get() == services
    }

    def "waitForServices can be set with empty map"() {
        when:
        waitForLogSpec.waitForServices.set([:])

        then:
        waitForLogSpec.waitForServices.present
        waitForLogSpec.waitForServices.get() == [:]
    }

    def "rejectPatterns can be set"() {
        given:
        def patterns = [
            'app': ['Exception', 'Error'],
            'db': ['FATAL', 'panic']
        ]

        when:
        waitForLogSpec.rejectPatterns.set(patterns)

        then:
        waitForLogSpec.rejectPatterns.get() == patterns
    }

    def "timeoutSeconds can be overridden"() {
        when:
        waitForLogSpec.timeoutSeconds.set(120)

        then:
        waitForLogSpec.timeoutSeconds.get() == 120
    }

    def "pollSeconds can be overridden"() {
        when:
        waitForLogSpec.pollSeconds.set(5)

        then:
        waitForLogSpec.pollSeconds.get() == 5
    }

    def "caseInsensitive can be set to true"() {
        when:
        waitForLogSpec.caseInsensitive.set(true)

        then:
        waitForLogSpec.caseInsensitive.get() == true
    }

    def "verbose can be set to true"() {
        when:
        waitForLogSpec.verbose.set(true)

        then:
        waitForLogSpec.verbose.get() == true
    }

    def "progressIntervalSeconds can be set"() {
        when:
        waitForLogSpec.progressIntervalSeconds.set(10)

        then:
        waitForLogSpec.progressIntervalSeconds.get() == 10
    }

    // ===== EDGE CASE TESTS =====

    def "waitForServices supports regex special characters in patterns"() {
        given:
        def patterns = [
            'app': ['\\[INFO\\].*started', 'port:\\s+\\d+'],
            'db': ['(?i)ready']
        ]

        when:
        waitForLogSpec.waitForServices.set(patterns)

        then:
        waitForLogSpec.waitForServices.get() == patterns
    }

    def "waitForServices supports empty pattern list for service"() {
        when:
        waitForLogSpec.waitForServices.set(['app': []])

        then:
        waitForLogSpec.waitForServices.get() == ['app': []]
    }

    def "properties are independent and can be set in any order"() {
        when:
        waitForLogSpec.verbose.set(true)
        waitForLogSpec.timeoutSeconds.set(90)
        waitForLogSpec.waitForServices.set(['app': ['ready']])
        waitForLogSpec.pollSeconds.set(3)

        then:
        waitForLogSpec.verbose.get() == true
        waitForLogSpec.timeoutSeconds.get() == 90
        waitForLogSpec.waitForServices.get() == ['app': ['ready']]
        waitForLogSpec.pollSeconds.get() == 3
    }

    // ===== MULTIPLE INSTANCES TESTS =====

    def "multiple WaitForLogSpec instances are independent"() {
        given:
        def spec1 = project.objects.newInstance(WaitForLogSpec)
        def spec2 = project.objects.newInstance(WaitForLogSpec)

        when:
        spec1.timeoutSeconds.set(100)
        spec2.timeoutSeconds.set(200)

        then:
        spec1.timeoutSeconds.get() == 100
        spec2.timeoutSeconds.get() == 200
    }
}
