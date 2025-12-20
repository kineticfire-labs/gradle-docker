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

import com.kineticfire.gradle.docker.service.TaskExecutionService
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for TaskLookup interface, TaskLookupFactory, and TaskExecutionServiceLookup implementation
 */
class TaskLookupTest extends Specification {

    Project project
    TaskExecutionService service

    def setup() {
        project = ProjectBuilder.builder().build()
        // Create a mock TaskExecutionService
        service = Mock(TaskExecutionService)
    }

    // ===== FACTORY METHOD TESTS =====

    def "TaskLookupFactory from creates TaskExecutionServiceLookup from TaskExecutionService"() {
        when:
        def lookup = TaskLookupFactory.from(service)

        then:
        lookup != null
        lookup instanceof TaskExecutionServiceLookup
    }

    // ===== TASK EXECUTION SERVICE LOOKUP TESTS =====

    def "TaskExecutionServiceLookup findByName delegates to service"() {
        given:
        def task = Mock(Task)
        service.findTask('myTask') >> task
        def lookup = TaskLookupFactory.from(service)

        when:
        def result = lookup.findByName('myTask')

        then:
        result == task
    }

    def "TaskExecutionServiceLookup findByName returns null when task does not exist"() {
        given:
        service.findTask('nonExistent') >> null
        def lookup = TaskLookupFactory.from(service)

        expect:
        lookup.findByName('nonExistent') == null
    }

    def "TaskExecutionServiceLookup findByName works with multiple tasks"() {
        given:
        def task1 = Mock(Task)
        def task2 = Mock(Task)
        def task3 = Mock(Task)
        service.findTask('task1') >> task1
        service.findTask('task2') >> task2
        service.findTask('task3') >> task3
        def lookup = TaskLookupFactory.from(service)

        expect:
        lookup.findByName('task1') == task1
        lookup.findByName('task2') == task2
        lookup.findByName('task3') == task3
    }

    // ===== EXECUTE METHOD TESTS =====

    def "TaskExecutionServiceLookup execute by name delegates to service"() {
        given:
        def lookup = TaskLookupFactory.from(service)

        when:
        lookup.execute('testTask')

        then:
        1 * service.executeTask('testTask')
    }

    def "TaskExecutionServiceLookup execute task delegates to service"() {
        given:
        def task = Mock(Task)
        def lookup = TaskLookupFactory.from(service)

        when:
        lookup.execute(task)

        then:
        1 * service.executeTask(task)
    }

    // ===== INTERFACE IMPLEMENTATION TESTS =====

    def "can use TaskLookup interface polymorphically"() {
        given:
        def task = Mock(Task)
        service.findTask('testTask') >> task
        TaskLookup lookup = TaskLookupFactory.from(service)

        expect:
        lookup.findByName('testTask') == task
    }

    def "TaskExecutionServiceLookup constructor accepts TaskExecutionService"() {
        when:
        def lookup = new TaskExecutionServiceLookup(service)

        then:
        lookup != null
    }

    // ===== EDGE CASE TESTS =====

    def "TaskExecutionServiceLookup handles null task name"() {
        given:
        service.findTask(null) >> null
        def lookup = TaskLookupFactory.from(service)

        when:
        def result = lookup.findByName(null)

        then:
        result == null
    }

    def "TaskExecutionServiceLookup handles empty string task name"() {
        given:
        service.findTask('') >> null
        def lookup = TaskLookupFactory.from(service)

        expect:
        lookup.findByName('') == null
    }

    // ===== TASK LOOKUP FACTORY - fromTaskContainer TESTS =====

    def "TaskLookupFactory fromTaskContainer creates TaskContainerLookup"() {
        when:
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        then:
        lookup != null
        lookup instanceof TaskContainerLookup
    }

    // ===== TASK CONTAINER LOOKUP TESTS =====

    def "TaskContainerLookup constructor accepts TaskContainer"() {
        when:
        def lookup = new TaskContainerLookup(project.tasks)

        then:
        lookup != null
    }

    def "TaskContainerLookup findByName returns existing task"() {
        given:
        def task = project.tasks.create('testTask')
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        when:
        def result = lookup.findByName('testTask')

        then:
        result == task
    }

    def "TaskContainerLookup findByName returns null for non-existent task"() {
        given:
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        expect:
        lookup.findByName('nonExistent') == null
    }

    def "TaskContainerLookup findByName returns null for null task name"() {
        given:
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        expect:
        lookup.findByName(null) == null
    }

    def "TaskContainerLookup findByName works with multiple tasks"() {
        given:
        def task1 = project.tasks.create('task1')
        def task2 = project.tasks.create('task2')
        def task3 = project.tasks.create('task3')
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        expect:
        lookup.findByName('task1') == task1
        lookup.findByName('task2') == task2
        lookup.findByName('task3') == task3
    }

    def "TaskContainerLookup execute by name executes task actions"() {
        given:
        def actionExecuted = false
        project.tasks.create('testTask') {
            doLast { actionExecuted = true }
        }
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        when:
        lookup.execute('testTask')

        then:
        actionExecuted
    }

    def "TaskContainerLookup execute by name throws IllegalArgumentException for non-existent task"() {
        given:
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        when:
        lookup.execute('nonExistent')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('nonExistent')
        e.message.contains('not found')
    }

    def "TaskContainerLookup execute task runs all actions"() {
        given:
        def action1Executed = false
        def action2Executed = false
        def task = project.tasks.create('testTask') {
            doLast { action1Executed = true }
            doLast { action2Executed = true }
        }
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        when:
        lookup.execute(task)

        then:
        action1Executed
        action2Executed
    }

    def "TaskContainerLookup execute task handles task with no actions"() {
        given:
        def task = project.tasks.create('emptyTask')
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        when:
        lookup.execute(task)

        then:
        noExceptionThrown()
    }

    def "TaskContainerLookup execute by name runs in order"() {
        given:
        def executionOrder = []
        project.tasks.create('orderedTask') {
            doFirst { executionOrder << 'first' }
            doLast { executionOrder << 'last' }
        }
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        when:
        lookup.execute('orderedTask')

        then:
        executionOrder == ['first', 'last']
    }

    def "TaskContainerLookup can be used polymorphically as TaskLookup"() {
        given:
        def task = project.tasks.create('polymorphicTask')
        TaskLookup lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        expect:
        lookup.findByName('polymorphicTask') == task
    }

    def "TaskContainerLookup handles empty string task name for findByName"() {
        given:
        def lookup = TaskLookupFactory.fromTaskContainer(project.tasks)

        expect:
        lookup.findByName('') == null
    }

    def "TaskContainerLookup findByName returns null when null passed"() {
        given:
        def lookup = new TaskContainerLookup(project.tasks)

        when:
        def result = lookup.findByName(null)

        then:
        result == null
    }
}
