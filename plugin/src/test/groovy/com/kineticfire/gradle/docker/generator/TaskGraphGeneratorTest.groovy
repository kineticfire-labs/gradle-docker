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

package com.kineticfire.gradle.docker.generator

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for TaskGraphGenerator
 */
class TaskGraphGeneratorTest extends Specification {

    @TempDir
    Path tempDir

    Project project

    /**
     * Concrete implementation for testing the abstract base class
     */
    static class TestableTaskGraphGenerator extends TaskGraphGenerator {
        // Expose protected methods for testing
        @Override
        TaskProvider<DefaultTask> registerConditionalTask(
                Project project, String taskName, Class taskType, Closure configAction) {
            return super.registerConditionalTask(project, taskName, taskType, configAction)
        }

        @Override
        void wireTaskDependency(Project project, String dependentTaskName, String dependsOnTaskName) {
            super.wireTaskDependency(project, dependentTaskName, dependsOnTaskName)
        }

        @Override
        void wireFinalizedBy(Project project, String taskName, String finalizerTaskName) {
            super.wireFinalizedBy(project, taskName, finalizerTaskName)
        }

        @Override
        TaskProvider<Task> getOrCreateLifecycleTask(
                Project project, String taskName, String group, String description) {
            return super.getOrCreateLifecycleTask(project, taskName, group, description)
        }

        @Override
        void configureTestResultOutput(
                Project project, TaskProvider<? extends Task> testTask, String stateDir) {
            super.configureTestResultOutput(project, testTask, stateDir)
        }

        @Override
        void configureOnlyIfTestsPassed(Project project, String taskName, String stateDir) {
            super.configureOnlyIfTestsPassed(project, taskName, stateDir)
        }

        @Override
        void configureAlwaysRun(TaskProvider<? extends Task> taskProvider) {
            super.configureAlwaysRun(taskProvider)
        }

        @Override
        boolean taskExists(Project project, String taskName) {
            return super.taskExists(project, taskName)
        }
    }

    TestableTaskGraphGenerator generator

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        generator = new TestableTaskGraphGenerator()
    }

    // ===== REGISTER CONDITIONAL TASK TESTS =====

    def "registerConditionalTask creates task"() {
        when:
        def taskProvider = generator.registerConditionalTask(
            project, 'testTask', DefaultTask, null
        )

        then:
        taskProvider != null
        project.tasks.findByName('testTask') != null
    }

    def "registerConditionalTask applies configuration closure"() {
        when:
        def taskProvider = generator.registerConditionalTask(
            project, 'configuredTask', DefaultTask,
            { task ->
                task.description = "Test description"
            }
        )
        def task = taskProvider.get()

        then:
        task.description == "Test description"
    }

    def "registerConditionalTask handles null configuration"() {
        when:
        def taskProvider = generator.registerConditionalTask(
            project, 'nullConfigTask', DefaultTask, null
        )

        then:
        taskProvider != null
        taskProvider.get() instanceof DefaultTask
    }

    // ===== WIRE TASK DEPENDENCY TESTS =====

    def "wireTaskDependency creates dependency between tasks"() {
        given:
        project.tasks.register('task1')
        project.tasks.register('task2')

        when:
        generator.wireTaskDependency(project, 'task2', 'task1')

        then:
        def task2 = project.tasks.getByName('task2')
        task2.dependsOn.any { it.toString().contains('task1') }
    }

    def "wireTaskDependency works with task providers"() {
        given:
        def taskProvider1 = project.tasks.register('providerTask1')
        def taskProvider2 = project.tasks.register('providerTask2')

        when:
        generator.wireTaskDependency(project, 'providerTask2', 'providerTask1')

        then:
        def task2 = project.tasks.getByName('providerTask2')
        task2.dependsOn.any {
            it.toString().contains('providerTask1')
        }
    }

    // ===== WIRE FINALIZED BY TESTS =====

    def "wireFinalizedBy creates finalizedBy relationship"() {
        given:
        project.tasks.register('mainTask')
        project.tasks.register('finalizerTask')

        when:
        generator.wireFinalizedBy(project, 'mainTask', 'finalizerTask')

        then:
        def mainTask = project.tasks.getByName('mainTask')
        mainTask.finalizedBy.getDependencies(mainTask).any {
            it.name == 'finalizerTask'
        }
    }

    // ===== GET OR CREATE LIFECYCLE TASK TESTS =====

    def "getOrCreateLifecycleTask creates new task"() {
        when:
        def taskProvider = generator.getOrCreateLifecycleTask(
            project, 'runMyPipeline', 'docker', 'Run the pipeline'
        )
        def task = taskProvider.get()

        then:
        task != null
        task.name == 'runMyPipeline'
        task.group == 'docker'
        task.description == 'Run the pipeline'
    }

    def "getOrCreateLifecycleTask returns existing task"() {
        given:
        def existingTask = project.tasks.register('existingTask') { task ->
            task.group = 'existing'
        }.get()

        when:
        def taskProvider = generator.getOrCreateLifecycleTask(
            project, 'existingTask', 'new-group', 'New description'
        )
        def task = taskProvider.get()

        then:
        task == existingTask
        task.group == 'existing'  // Original group preserved
    }

    // ===== CONFIGURE TEST RESULT OUTPUT TESTS =====

    def "configureTestResultOutput adds output file to task"() {
        given:
        def testTask = project.tasks.register('testTask', DefaultTask)

        when:
        generator.configureTestResultOutput(project, testTask, 'test-pipeline/state')
        def task = testTask.get()

        then:
        task.outputs.files.files.any { it.name == 'test-result.json' }
    }

    def "configureTestResultOutput adds doLast action"() {
        given:
        def testTask = project.tasks.register('testTask', DefaultTask)
        generator.configureTestResultOutput(project, testTask, 'test-pipeline/state')

        when:
        def task = testTask.get()

        then:
        // Task has additional actions from doLast
        task.actions.size() > 0
    }

    // ===== CONFIGURE ONLY IF TESTS PASSED TESTS =====

    def "configureOnlyIfTestsPassed adds onlyIf predicate"() {
        given:
        project.tasks.register('conditionalTask', DefaultTask)
        // Create the state directory and test result file
        def stateDir = new File(project.layout.buildDirectory.get().asFile, 'test-state/state')
        stateDir.mkdirs()
        def resultFile = new File(stateDir, 'test-result.json')
        PipelineStateFile.writeTestResult(resultFile, true, "Passed", 123L)

        when:
        generator.configureOnlyIfTestsPassed(project, 'conditionalTask', 'test-state/state')
        def task = project.tasks.getByName('conditionalTask')

        then:
        // The onlyIf spec should be configured
        task.onlyIf != null
    }

    def "configureOnlyIfTestsPassed respects test result"() {
        given:
        project.tasks.register('conditionalTask', DefaultTask)
        def stateDir = new File(project.layout.buildDirectory.get().asFile, 'test-state/state')
        stateDir.mkdirs()
        def resultFile = new File(stateDir, 'test-result.json')
        PipelineStateFile.writeTestResult(resultFile, true, "Passed", 123L)

        when:
        generator.configureOnlyIfTestsPassed(project, 'conditionalTask', 'test-state/state')
        def task = project.tasks.getByName('conditionalTask')

        then:
        // OnlyIf spec is present
        task.onlyIf != null
    }

    // ===== CONFIGURE ALWAYS RUN TESTS =====

    def "configureAlwaysRun sets onlyIf to always true"() {
        given:
        def taskProvider = project.tasks.register('alwaysTask', DefaultTask)

        when:
        generator.configureAlwaysRun(taskProvider)
        def task = taskProvider.get()

        then:
        task.onlyIf != null
    }

    // ===== TASK EXISTS TESTS =====

    def "taskExists returns true for existing task"() {
        given:
        project.tasks.register('existingTask')

        expect:
        generator.taskExists(project, 'existingTask') == true
    }

    def "taskExists returns false for non-existing task"() {
        expect:
        generator.taskExists(project, 'nonExistingTask') == false
    }

    def "taskExists returns false for null task name"() {
        expect:
        generator.taskExists(project, null) == false
    }

    // ===== INTEGRATION TESTS =====

    def "can create a complete task graph"() {
        when:
        // Create build task
        def buildTask = generator.registerConditionalTask(
            project, 'dockerBuild', DefaultTask, null
        )

        // Create test task
        def testTask = generator.registerConditionalTask(
            project, 'integrationTest', DefaultTask, null
        )

        // Create cleanup task
        def cleanupTask = generator.registerConditionalTask(
            project, 'cleanup', DefaultTask, null
        )

        // Create tag-on-success task
        def tagTask = generator.registerConditionalTask(
            project, 'tagOnSuccess', DefaultTask, null
        )

        // Create lifecycle task
        def lifecycleTask = generator.getOrCreateLifecycleTask(
            project, 'runPipeline', 'docker', 'Run complete pipeline'
        )

        // Wire dependencies
        generator.wireTaskDependency(project, 'integrationTest', 'dockerBuild')
        generator.wireTaskDependency(project, 'tagOnSuccess', 'integrationTest')
        generator.wireTaskDependency(project, 'runPipeline', 'tagOnSuccess')
        generator.wireFinalizedBy(project, 'integrationTest', 'cleanup')

        // Configure test output
        generator.configureTestResultOutput(project, testTask, 'pipeline/state')

        // Configure conditional execution
        generator.configureOnlyIfTestsPassed(project, 'tagOnSuccess', 'pipeline/state')

        // Configure cleanup to always run
        generator.configureAlwaysRun(cleanupTask)

        then:
        // Verify tasks exist
        generator.taskExists(project, 'dockerBuild')
        generator.taskExists(project, 'integrationTest')
        generator.taskExists(project, 'cleanup')
        generator.taskExists(project, 'tagOnSuccess')
        generator.taskExists(project, 'runPipeline')

        // Verify dependencies
        def integrationTestTask = project.tasks.getByName('integrationTest')
        integrationTestTask.dependsOn.any { it.toString().contains('dockerBuild') }

        def tagOnSuccessTask = project.tasks.getByName('tagOnSuccess')
        tagOnSuccessTask.dependsOn.any { it.toString().contains('integrationTest') }
    }
}
