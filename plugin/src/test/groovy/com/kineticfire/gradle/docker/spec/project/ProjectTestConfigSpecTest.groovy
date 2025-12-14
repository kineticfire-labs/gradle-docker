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
 * Unit tests for ProjectTestConfigSpec
 */
class ProjectTestConfigSpecTest extends Specification {

    Project project
    ProjectTestConfigSpec spec

    def setup() {
        project = ProjectBuilder.builder().build()
        spec = project.objects.newInstance(ProjectTestConfigSpec, 'apiTests')
    }

    // ===== NAME TESTS =====

    def "getName returns configured name"() {
        expect:
        spec.getName() == 'apiTests'
    }

    def "spec implements Named interface"() {
        expect:
        spec.name == 'apiTests'
    }

    // ===== CONVENTION TESTS =====

    def "lifecycle has convention of CLASS"() {
        expect:
        spec.lifecycle.get() == Lifecycle.CLASS
    }

    def "timeoutSeconds has convention of 60"() {
        expect:
        spec.timeoutSeconds.get() == 60
    }

    def "pollSeconds has convention of 2"() {
        expect:
        spec.pollSeconds.get() == 2
    }

    def "testClasses has convention of empty list"() {
        expect:
        spec.testClasses.get() == []
    }

    def "waitForHealthy has convention of empty list"() {
        expect:
        spec.waitForHealthy.get() == []
    }

    def "waitForRunning has convention of empty list"() {
        expect:
        spec.waitForRunning.get() == []
    }

    // ===== PROPERTY TESTS =====

    def "compose property can be set"() {
        when:
        spec.compose.set('src/integrationTest/resources/compose/api.yml')

        then:
        spec.compose.get() == 'src/integrationTest/resources/compose/api.yml'
    }

    def "lifecycle property can be set to METHOD"() {
        when:
        spec.lifecycle.set(Lifecycle.METHOD)

        then:
        spec.lifecycle.get() == Lifecycle.METHOD
    }

    def "timeoutSeconds property can be set"() {
        when:
        spec.timeoutSeconds.set(120)

        then:
        spec.timeoutSeconds.get() == 120
    }

    def "pollSeconds property can be set"() {
        when:
        spec.pollSeconds.set(5)

        then:
        spec.pollSeconds.get() == 5
    }

    def "testClasses property can be set"() {
        when:
        spec.testClasses.set(['com.example.api.**', 'com.example.integration.**'])

        then:
        spec.testClasses.get() == ['com.example.api.**', 'com.example.integration.**']
    }

    def "waitForHealthy property can be set"() {
        when:
        spec.waitForHealthy.set(['app', 'db'])

        then:
        spec.waitForHealthy.get() == ['app', 'db']
    }

    def "waitForRunning property can be set"() {
        when:
        spec.waitForRunning.set(['redis', 'nginx'])

        then:
        spec.waitForRunning.get() == ['redis', 'nginx']
    }

    def "projectName property can be set"() {
        when:
        spec.projectName.set('my-project')

        then:
        spec.projectName.get() == 'my-project'
    }

    // ===== getTestTaskName TESTS =====

    def "getTestTaskName generates correct name"() {
        expect:
        spec.getTestTaskName() == 'ApiTestsIntegrationTest'
    }

    def "getTestTaskName handles single-word name"() {
        given:
        def singleWordSpec = project.objects.newInstance(ProjectTestConfigSpec, 'unit')

        expect:
        singleWordSpec.getTestTaskName() == 'UnitIntegrationTest'
    }

    def "getTestTaskName handles camelCase name"() {
        given:
        def camelSpec = project.objects.newInstance(ProjectTestConfigSpec, 'smokeTests')

        expect:
        camelSpec.getTestTaskName() == 'SmokeTestsIntegrationTest'
    }

    // ===== MULTIPLE SPECS IN CONTAINER TEST =====

    def "multiple specs can be created with different names"() {
        given:
        def spec1 = project.objects.newInstance(ProjectTestConfigSpec, 'apiTests')
        def spec2 = project.objects.newInstance(ProjectTestConfigSpec, 'statefulTests')

        when:
        spec1.lifecycle.set(Lifecycle.CLASS)
        spec2.lifecycle.set(Lifecycle.METHOD)

        then:
        spec1.name == 'apiTests'
        spec2.name == 'statefulTests'
        spec1.lifecycle.get() == Lifecycle.CLASS
        spec2.lifecycle.get() == Lifecycle.METHOD
    }
}
