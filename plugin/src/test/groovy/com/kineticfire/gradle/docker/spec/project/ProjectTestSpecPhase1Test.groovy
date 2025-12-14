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
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for Phase 1 additions to ProjectTestSpec (Lifecycle enum)
 */
class ProjectTestSpecPhase1Test extends Specification {

    Project project
    ProjectTestSpec spec

    def setup() {
        project = ProjectBuilder.builder().build()
        spec = project.objects.newInstance(ProjectTestSpec)
    }

    // ===== LIFECYCLE ENUM TESTS =====

    def "lifecycle has convention of Lifecycle.CLASS"() {
        expect:
        spec.lifecycle.get() == Lifecycle.CLASS
    }

    def "lifecycle property can be set to CLASS"() {
        when:
        spec.lifecycle.set(Lifecycle.CLASS)

        then:
        spec.lifecycle.get() == Lifecycle.CLASS
    }

    def "lifecycle property can be set to METHOD"() {
        when:
        spec.lifecycle.set(Lifecycle.METHOD)

        then:
        spec.lifecycle.get() == Lifecycle.METHOD
    }

    def "lifecycle property type is Property<Lifecycle>"() {
        expect:
        spec.lifecycle.getClass().interfaces.any { it.simpleName == 'Property' }
    }

    // ===== INTEGRATION WITH OTHER PROPERTIES TESTS =====

    def "can configure full test spec with Lifecycle enum"() {
        when:
        spec.compose.set('src/integrationTest/resources/compose/app.yml')
        spec.lifecycle.set(Lifecycle.METHOD)
        spec.waitForHealthy.set(['app', 'db'])
        spec.timeoutSeconds.set(120)
        spec.pollSeconds.set(5)
        spec.testTaskName.set('customIntegrationTest')

        then:
        spec.compose.get() == 'src/integrationTest/resources/compose/app.yml'
        spec.lifecycle.get() == Lifecycle.METHOD
        spec.waitForHealthy.get() == ['app', 'db']
        spec.timeoutSeconds.get() == 120
        spec.pollSeconds.get() == 5
        spec.testTaskName.get() == 'customIntegrationTest'
    }

    // ===== EXISTING CONVENTION TESTS (ENSURING NO REGRESSION) =====

    def "timeoutSeconds has convention of 60"() {
        expect:
        spec.timeoutSeconds.get() == 60
    }

    def "pollSeconds has convention of 2"() {
        expect:
        spec.pollSeconds.get() == 2
    }

    def "testTaskName has convention of 'integrationTest'"() {
        expect:
        spec.testTaskName.get() == 'integrationTest'
    }

    def "waitForHealthy has convention of empty list"() {
        expect:
        spec.waitForHealthy.get() == []
    }

    def "waitForRunning has convention of empty list"() {
        expect:
        spec.waitForRunning.get() == []
    }

    def "compose is optional"() {
        expect:
        !spec.compose.isPresent()
    }

    def "projectName is optional"() {
        expect:
        !spec.projectName.isPresent()
    }
}
