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

package com.kineticfire.gradle.docker.service

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for TaskExecutionService
 */
class TaskExecutionServiceTest extends Specification {

    @TempDir
    Path tempDir

    Project project
    TaskExecutionService service

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        service = project.objects.newInstance(TestableTaskExecutionService)
    }

    // ============ setTaskContainer Tests ============

    def "setTaskContainer sets the task container"() {
        when:
        service.setTaskContainer(project.tasks)

        then:
        noExceptionThrown()
    }

    // ============ findTask Tests ============

    def "findTask throws exception when TaskContainer not set"() {
        when:
        service.findTask("someTask")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("TaskContainer not set")
    }

    def "findTask returns null for non-existent task"() {
        given:
        service.setTaskContainer(project.tasks)

        when:
        def task = service.findTask("nonExistentTask")

        then:
        task == null
    }

    def "findTask returns task when it exists"() {
        given:
        service.setTaskContainer(project.tasks)
        project.tasks.register("myTask")

        when:
        def task = service.findTask("myTask")

        then:
        task != null
        task.name == "myTask"
    }

    // ============ executeTask(String) Tests ============

    def "executeTask with name throws exception when task not found"() {
        given:
        service.setTaskContainer(project.tasks)

        when:
        service.executeTask("nonExistentTask")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Task 'nonExistentTask' not found")
    }

    def "executeTask with name executes task actions"() {
        given:
        service.setTaskContainer(project.tasks)
        def actionExecuted = false
        project.tasks.register("myTask") { task ->
            task.doLast {
                actionExecuted = true
            }
        }

        when:
        service.executeTask("myTask")

        then:
        actionExecuted
    }

    // ============ executeTask(Task) Tests ============

    def "executeTask with Task object executes all actions"() {
        given:
        service.setTaskContainer(project.tasks)
        def action1Executed = false
        def action2Executed = false
        def task = project.tasks.register("myTask") { t ->
            t.doFirst { action1Executed = true }
            t.doLast { action2Executed = true }
        }.get()

        when:
        service.executeTask(task)

        then:
        action1Executed
        action2Executed
    }

    def "executeTask with Task object handles task with no actions"() {
        given:
        service.setTaskContainer(project.tasks)
        def task = project.tasks.register("emptyTask").get()

        when:
        service.executeTask(task)

        then:
        noExceptionThrown()
    }

    // ============ hasTask Tests ============

    def "hasTask returns false when task does not exist"() {
        given:
        service.setTaskContainer(project.tasks)

        expect:
        !service.hasTask("nonExistentTask")
    }

    def "hasTask returns true when task exists"() {
        given:
        service.setTaskContainer(project.tasks)
        project.tasks.register("existingTask")

        expect:
        service.hasTask("existingTask")
    }

    def "hasTask throws exception when TaskContainer not set"() {
        when:
        service.hasTask("anyTask")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("TaskContainer not set")
    }

    // ============ getParameters Tests ============

    def "getParameters returns null for test implementation"() {
        expect:
        service.getParameters() == null
    }

    /**
     * Concrete test implementation of TaskExecutionService
     */
    static abstract class TestableTaskExecutionService extends TaskExecutionService {
        @Override
        Params getParameters() {
            return null
        }
    }
}
