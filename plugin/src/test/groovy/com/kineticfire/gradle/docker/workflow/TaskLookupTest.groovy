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

package com.kineticfire.gradle.docker.workflow

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for TaskLookup interface, TaskLookupFactory, and TaskContainerLookup implementation
 */
class TaskLookupTest extends Specification {

    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    // ===== FACTORY METHOD TESTS =====

    def "TaskLookupFactory from creates TaskContainerLookup from TaskContainer"() {
        when:
        def lookup = TaskLookupFactory.from(project.tasks)

        then:
        lookup != null
        lookup instanceof TaskContainerLookup
    }

    // ===== TASK CONTAINER LOOKUP TESTS =====

    def "TaskContainerLookup findByName returns task when exists"() {
        given:
        def task = project.tasks.create('myTask')
        def lookup = TaskLookupFactory.from(project.tasks)

        when:
        def result = lookup.findByName('myTask')

        then:
        result == task
    }

    def "TaskContainerLookup findByName returns null when task does not exist"() {
        given:
        def lookup = TaskLookupFactory.from(project.tasks)

        expect:
        lookup.findByName('nonExistent') == null
    }

    def "TaskContainerLookup findByName works with multiple tasks"() {
        given:
        def task1 = project.tasks.create('task1')
        def task2 = project.tasks.create('task2')
        def task3 = project.tasks.create('task3')
        def lookup = TaskLookupFactory.from(project.tasks)

        expect:
        lookup.findByName('task1') == task1
        lookup.findByName('task2') == task2
        lookup.findByName('task3') == task3
    }

    // ===== SERIALIZATION TESTS =====

    def "TaskContainerLookup is Serializable"() {
        given:
        def lookup = TaskLookupFactory.from(project.tasks)

        expect:
        lookup instanceof Serializable
    }

    def "TaskContainerLookup has serialVersionUID"() {
        expect:
        TaskContainerLookup.serialVersionUID == 1L
    }

    // ===== INTERFACE IMPLEMENTATION TESTS =====

    def "can use TaskLookup interface polymorphically"() {
        given:
        project.tasks.create('testTask')
        TaskLookup lookup = TaskLookupFactory.from(project.tasks)

        expect:
        lookup.findByName('testTask') != null
    }

    def "TaskContainerLookup constructor accepts TaskContainer"() {
        when:
        def lookup = new TaskContainerLookup(project.tasks)

        then:
        lookup != null
    }

    // ===== EDGE CASE TESTS =====

    def "TaskContainerLookup handles empty task container"() {
        given:
        def lookup = TaskLookupFactory.from(project.tasks)

        expect:
        lookup.findByName('anyTask') == null
    }

    def "TaskContainerLookup handles null task name"() {
        given:
        def lookup = TaskLookupFactory.from(project.tasks)

        when:
        def result = lookup.findByName(null)

        then:
        result == null
    }

    def "TaskContainerLookup handles empty string task name"() {
        given:
        def lookup = TaskLookupFactory.from(project.tasks)

        expect:
        lookup.findByName('') == null
    }
}
