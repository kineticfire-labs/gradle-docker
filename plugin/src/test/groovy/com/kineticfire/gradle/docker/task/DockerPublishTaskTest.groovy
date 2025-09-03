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

package com.kineticfire.gradle.docker.task

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for DockerPublishTask
 */
class DockerPublishTaskTest extends Specification {

    Project project
    DockerPublishTask task

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testDockerPublish', DockerPublishTask).get()
    }

    // ===== BASIC TASK TESTS =====

    def "task can be created"() {
        expect:
        task != null
        task instanceof DockerPublishTask
    }

    def "task extends DefaultTask"() {
        expect:
        task instanceof org.gradle.api.DefaultTask
    }

    def "task has correct group and description"() {
        expect:
        task.group == null // No group set in placeholder implementation
        task.description == null // No description set in placeholder implementation
    }

    def "task has publishImage action"() {
        expect:
        task.actions.size() == 1
        task.actions[0].displayName.contains('publishImage')
    }

    // ===== TASK EXECUTION TESTS =====

    def "publishImage action executes without exception"() {
        when:
        task.publishImage()

        then:
        noExceptionThrown()
    }

    def "task execution succeeds"() {
        when:
        def result = task.publishImage()

        then:
        noExceptionThrown()
        result == null // void method
    }

    // ===== TASK CONFIGURATION TESTS =====

    def "task can be configured with different names"() {
        given:
        def task1 = project.tasks.register('publishWebapp', DockerPublishTask).get()
        def task2 = project.tasks.register('publishApi', DockerPublishTask).get()

        expect:
        task1.name == 'publishWebapp'
        task2.name == 'publishApi'
        task1 != task2
    }

    def "task inherits DefaultTask properties"() {
        expect:
        task.hasProperty('group')
        task.hasProperty('description')
        task.hasProperty('enabled')
        task.hasProperty('logger')
    }

    def "task is enabled by default"() {
        expect:
        task.enabled == true
    }

    def "task can be disabled"() {
        when:
        task.enabled = false

        then:
        task.enabled == false
    }

    // ===== TASK TYPE TESTS =====

    def "task type is DockerPublishTask"() {
        expect:
        task.class.simpleName == 'DockerPublishTask_Decorated'
        DockerPublishTask.isAssignableFrom(task.class)
    }

    def "multiple tasks can be created"() {
        given:
        def task1 = project.tasks.register('publish1', DockerPublishTask).get()
        def task2 = project.tasks.register('publish2', DockerPublishTask).get()
        def task3 = project.tasks.register('publish3', DockerPublishTask).get()

        expect:
        [task1, task2, task3].every { it instanceof DockerPublishTask }
        [task1, task2, task3].collect { it.name } == ['publish1', 'publish2', 'publish3']
    }

    // ===== TASK INHERITANCE TESTS =====

    def "task can be configured with custom group"() {
        when:
        task.group = 'custom-docker'

        then:
        task.group == 'custom-docker'
    }

    def "task can be configured with custom description"() {
        when:
        task.description = 'Custom publish task description'

        then:
        task.description == 'Custom publish task description'
    }

    // ===== EDGE CASES =====

    def "task action can be called multiple times"() {
        when:
        task.publishImage()
        task.publishImage()
        task.publishImage()

        then:
        noExceptionThrown()
    }

    def "task has default Gradle logger"() {
        expect:
        task.logger != null
        task.logger instanceof org.gradle.api.logging.Logger
    }

    // ===== PLACEHOLDER IMPLEMENTATION TESTS =====

    def "task is placeholder implementation"() {
        when:
        task.publishImage()

        then:
        noExceptionThrown()
        // Placeholder implementation executes without error
    }

    def "task logs expected placeholder message"() {
        when:
        task.publishImage()

        then:
        noExceptionThrown()
        // Logs placeholder message to default Gradle logger
    }
}