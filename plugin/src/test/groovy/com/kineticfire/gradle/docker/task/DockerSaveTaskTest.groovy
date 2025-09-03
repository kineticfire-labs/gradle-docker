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
 * Unit tests for DockerSaveTask
 */
class DockerSaveTaskTest extends Specification {

    Project project
    DockerSaveTask task

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testDockerSave', DockerSaveTask).get()
    }

    // ===== BASIC TASK TESTS =====

    def "task can be created"() {
        expect:
        task != null
        task instanceof DockerSaveTask
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

    def "task has saveImage action"() {
        expect:
        task.actions.size() == 1
        task.actions[0].displayName.contains('saveImage')
    }

    // ===== TASK EXECUTION TESTS =====

    def "saveImage action executes without exception"() {
        when:
        task.saveImage()

        then:
        noExceptionThrown()
    }

    def "task execution succeeds"() {
        when:
        def result = task.saveImage()

        then:
        noExceptionThrown()
        result == null // void method
    }

    // ===== TASK CONFIGURATION TESTS =====

    def "task can be configured with different names"() {
        given:
        def task1 = project.tasks.register('saveWebapp', DockerSaveTask).get()
        def task2 = project.tasks.register('saveApi', DockerSaveTask).get()

        expect:
        task1.name == 'saveWebapp'
        task2.name == 'saveApi'
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

    def "task type is DockerSaveTask"() {
        expect:
        task.class.simpleName == 'DockerSaveTask_Decorated'
        DockerSaveTask.isAssignableFrom(task.class)
    }

    def "multiple tasks can be created"() {
        given:
        def task1 = project.tasks.register('save1', DockerSaveTask).get()
        def task2 = project.tasks.register('save2', DockerSaveTask).get()
        def task3 = project.tasks.register('save3', DockerSaveTask).get()

        expect:
        [task1, task2, task3].every { it instanceof DockerSaveTask }
        [task1, task2, task3].collect { it.name } == ['save1', 'save2', 'save3']
    }

    // ===== EDGE CASES =====

    def "task action can be called multiple times"() {
        when:
        task.saveImage()
        task.saveImage()
        task.saveImage()

        then:
        noExceptionThrown()
    }
}