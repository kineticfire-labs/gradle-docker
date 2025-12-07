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
 * Unit tests for WaitSpec
 */
class WaitSpecTest extends Specification {

    def project
    def waitSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        waitSpec = project.objects.newInstance(WaitSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        waitSpec != null
        waitSpec.timeoutSeconds.get() == 60
        waitSpec.pollSeconds.get() == 2
    }

    // ===== PROPERTY TESTS =====

    def "services property works correctly"() {
        when:
        waitSpec.waitForServices.set(['web', 'database', 'redis'])

        then:
        waitSpec.waitForServices.present
        waitSpec.waitForServices.get() == ['web', 'database', 'redis']
    }

    def "timeoutSeconds property works correctly"() {
        when:
        waitSpec.timeoutSeconds.set(120)

        then:
        waitSpec.timeoutSeconds.present
        waitSpec.timeoutSeconds.get() == 120
    }

    def "timeoutSeconds has default value"() {
        expect:
        waitSpec.timeoutSeconds.present
        waitSpec.timeoutSeconds.get() == 60
    }

    def "pollSeconds property works correctly"() {
        when:
        waitSpec.pollSeconds.set(5)

        then:
        waitSpec.pollSeconds.present
        waitSpec.pollSeconds.get() == 5
    }

    def "pollSeconds has default value"() {
        expect:
        waitSpec.pollSeconds.present
        waitSpec.pollSeconds.get() == 2
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        when:
        waitSpec.waitForServices.set(['app', 'db', 'cache'])
        waitSpec.timeoutSeconds.set(180)
        waitSpec.pollSeconds.set(10)

        then:
        waitSpec.waitForServices.get() == ['app', 'db', 'cache']
        waitSpec.timeoutSeconds.get() == 180
        waitSpec.pollSeconds.get() == 10
    }

    def "configuration with single service"() {
        when:
        waitSpec.waitForServices.set(['web'])
        waitSpec.timeoutSeconds.set(30)
        waitSpec.pollSeconds.set(1)

        then:
        waitSpec.waitForServices.get() == ['web']
        waitSpec.timeoutSeconds.get() == 30
        waitSpec.pollSeconds.get() == 1
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        when:
        waitSpec.waitForServices.set(['initial'])
        waitSpec.timeoutSeconds.set(60)
        waitSpec.pollSeconds.set(2)

        then:
        waitSpec.waitForServices.get() == ['initial']
        waitSpec.timeoutSeconds.get() == 60
        waitSpec.pollSeconds.get() == 2

        when:
        waitSpec.waitForServices.set(['updated', 'service2'])
        waitSpec.timeoutSeconds.set(300)
        waitSpec.pollSeconds.set(15)

        then:
        waitSpec.waitForServices.get() == ['updated', 'service2']
        waitSpec.timeoutSeconds.get() == 300
        waitSpec.pollSeconds.get() == 15
    }

    // ===== DEFAULT BEHAVIOR TESTS =====

    def "defaults remain after property access"() {
        given:
        def initialTimeout = waitSpec.timeoutSeconds.get()
        def initialPoll = waitSpec.pollSeconds.get()

        expect:
        initialTimeout == 60
        initialPoll == 2
        
        // Verify defaults persist
        waitSpec.timeoutSeconds.get() == 60
        waitSpec.pollSeconds.get() == 2
    }

    def "convention values can be overridden"() {
        when:
        waitSpec.timeoutSeconds.set(240)
        waitSpec.pollSeconds.set(5)

        then:
        waitSpec.timeoutSeconds.get() == 240
        waitSpec.pollSeconds.get() == 5
    }

    // ===== EDGE CASES =====

    def "waitForServices has convention of empty list"() {
        expect:
        // Verify the convention is set to empty list
        waitSpec.waitForServices.present
        waitSpec.waitForServices.get() == []
        waitSpec.waitForServices.get().isEmpty()
    }

    def "services is initially empty when not configured"() {
        expect:
        // ListProperty is typically present but empty by default
        waitSpec.waitForServices.get().isEmpty()
    }

    def "empty services list is supported"() {
        when:
        waitSpec.waitForServices.set([])

        then:
        waitSpec.waitForServices.present
        waitSpec.waitForServices.get().isEmpty()
    }

    def "various timeout values can be set"() {
        expect:
        [1, 30, 60, 120, 300, 600, 3600].each { timeout ->
            waitSpec.timeoutSeconds.set(timeout)
            assert waitSpec.timeoutSeconds.get() == timeout
        }
    }

    def "various poll intervals can be set"() {
        expect:
        [1, 2, 5, 10, 15, 30, 60].each { pollInterval ->
            waitSpec.pollSeconds.set(pollInterval)
            assert waitSpec.pollSeconds.get() == pollInterval
        }
    }

    def "multiple services can be configured"() {
        given:
        def servicesList = ['web', 'api', 'database', 'redis', 'elasticsearch', 'rabbitmq']

        when:
        waitSpec.waitForServices.set(servicesList)

        then:
        waitSpec.waitForServices.get() == servicesList
        waitSpec.waitForServices.get().size() == 6
    }

    def "realistic waiting configurations"() {
        expect:
        // Fast polling for quick services
        waitSpec.timeoutSeconds.set(30)
        waitSpec.pollSeconds.set(1)
        waitSpec.timeoutSeconds.get() == 30
        waitSpec.pollSeconds.get() == 1

        // Slow polling for heavy services
        waitSpec.timeoutSeconds.set(600)
        waitSpec.pollSeconds.set(30)
        waitSpec.timeoutSeconds.get() == 600
        waitSpec.pollSeconds.get() == 30
    }
}