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

package com.kineticfire.gradle.docker.spec.project

import com.kineticfire.gradle.docker.Lifecycle
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ProjectTestSpec
 */
class ProjectTestSpecTest extends Specification {

    def project
    def testSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        testSpec = project.objects.newInstance(ProjectTestSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        testSpec != null
        testSpec.lifecycle.get() == Lifecycle.CLASS
        testSpec.timeoutSeconds.get() == 60
        testSpec.pollSeconds.get() == 2
        testSpec.testTaskName.get() == 'integrationTest'
        testSpec.waitForHealthy.get() == []
        testSpec.waitForRunning.get() == []
    }

    // ===== PROPERTY TESTS =====

    def "compose property works correctly"() {
        when:
        testSpec.compose.set('src/integrationTest/resources/compose/app.yml')

        then:
        testSpec.compose.present
        testSpec.compose.get() == 'src/integrationTest/resources/compose/app.yml'
    }

    def "waitForHealthy property works correctly"() {
        when:
        testSpec.waitForHealthy.set(['app', 'db', 'redis'])

        then:
        testSpec.waitForHealthy.get() == ['app', 'db', 'redis']
    }

    def "waitForRunning property works correctly"() {
        when:
        testSpec.waitForRunning.set(['worker', 'scheduler'])

        then:
        testSpec.waitForRunning.get() == ['worker', 'scheduler']
    }

    def "lifecycle property works correctly with class value"() {
        when:
        testSpec.lifecycle.set(Lifecycle.CLASS)

        then:
        testSpec.lifecycle.get() == Lifecycle.CLASS
    }

    def "lifecycle property works correctly with method value"() {
        when:
        testSpec.lifecycle.set(Lifecycle.METHOD)

        then:
        testSpec.lifecycle.get() == Lifecycle.METHOD
    }

    def "timeoutSeconds property works correctly"() {
        when:
        testSpec.timeoutSeconds.set(120)

        then:
        testSpec.timeoutSeconds.get() == 120
    }

    def "pollSeconds property works correctly"() {
        when:
        testSpec.pollSeconds.set(5)

        then:
        testSpec.pollSeconds.get() == 5
    }

    def "projectName property works correctly"() {
        when:
        testSpec.projectName.set('my-test-project')

        then:
        testSpec.projectName.present
        testSpec.projectName.get() == 'my-test-project'
    }

    def "testTaskName property works correctly"() {
        when:
        testSpec.testTaskName.set('functionalTest')

        then:
        testSpec.testTaskName.get() == 'functionalTest'
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        when:
        testSpec.compose.set('src/test/resources/compose/stack.yml')
        testSpec.waitForHealthy.set(['app', 'db'])
        testSpec.waitForRunning.set(['worker'])
        testSpec.lifecycle.set(Lifecycle.METHOD)
        testSpec.timeoutSeconds.set(180)
        testSpec.pollSeconds.set(10)
        testSpec.projectName.set('test-stack')
        testSpec.testTaskName.set('integrationTest')

        then:
        testSpec.compose.get() == 'src/test/resources/compose/stack.yml'
        testSpec.waitForHealthy.get() == ['app', 'db']
        testSpec.waitForRunning.get() == ['worker']
        testSpec.lifecycle.get() == Lifecycle.METHOD
        testSpec.timeoutSeconds.get() == 180
        testSpec.pollSeconds.get() == 10
        testSpec.projectName.get() == 'test-stack'
        testSpec.testTaskName.get() == 'integrationTest'
    }

    def "minimal configuration with just compose file"() {
        when:
        testSpec.compose.set('docker-compose.yml')

        then:
        testSpec.compose.get() == 'docker-compose.yml'
        // Defaults are still in place
        testSpec.lifecycle.get() == Lifecycle.CLASS
        testSpec.timeoutSeconds.get() == 60
        testSpec.pollSeconds.get() == 2
        testSpec.testTaskName.get() == 'integrationTest'
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        when:
        testSpec.waitForHealthy.set(['initial'])
        testSpec.timeoutSeconds.set(30)

        then:
        testSpec.waitForHealthy.get() == ['initial']
        testSpec.timeoutSeconds.get() == 30

        when:
        testSpec.waitForHealthy.set(['updated', 'services'])
        testSpec.timeoutSeconds.set(300)

        then:
        testSpec.waitForHealthy.get() == ['updated', 'services']
        testSpec.timeoutSeconds.get() == 300
    }

    // ===== EDGE CASES =====

    def "empty wait lists are valid"() {
        expect:
        testSpec.waitForHealthy.get() == []
        testSpec.waitForRunning.get() == []
    }

    def "single service in wait list"() {
        when:
        testSpec.waitForHealthy.set(['app'])

        then:
        testSpec.waitForHealthy.get() == ['app']
        testSpec.waitForHealthy.get().size() == 1
    }

    def "multiple services in wait lists"() {
        when:
        testSpec.waitForHealthy.set(['app', 'db', 'cache', 'queue'])
        testSpec.waitForRunning.set(['worker1', 'worker2'])

        then:
        testSpec.waitForHealthy.get().size() == 4
        testSpec.waitForRunning.get().size() == 2
    }

    def "various timeout values"() {
        expect:
        [1, 30, 60, 120, 300, 600].each { timeout ->
            testSpec.timeoutSeconds.set(timeout)
            assert testSpec.timeoutSeconds.get() == timeout
        }
    }

    def "various poll intervals"() {
        expect:
        [1, 2, 5, 10, 30].each { poll ->
            testSpec.pollSeconds.set(poll)
            assert testSpec.pollSeconds.get() == poll
        }
    }
}
