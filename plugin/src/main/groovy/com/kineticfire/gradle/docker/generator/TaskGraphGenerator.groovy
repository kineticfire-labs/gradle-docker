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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Abstract base class for task graph generation.
 *
 * Provides common utilities for generating task dependency graphs at configuration time,
 * ensuring configuration cache compatibility for both dockerProject and dockerWorkflows DSLs.
 *
 * Key Design Principles:
 * - All task relationships are established during configuration, not execution
 * - File-based state communication between pipeline steps
 * - Flattened input properties for configuration cache compatibility
 * - No Project access at execution time
 */
abstract class TaskGraphGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskGraphGenerator)

    /**
     * Register a conditional task that executes based on a file-based predicate.
     *
     * @param project The Gradle project
     * @param taskName The name of the task to register
     * @param taskType The class of the task type
     * @param configAction Closure to configure the task
     * @return TaskProvider for the registered task
     */
    protected <T extends DefaultTask> TaskProvider<T> registerConditionalTask(
            Project project,
            String taskName,
            Class<T> taskType,
            Closure configAction) {

        LOGGER.debug("Registering conditional task: {}", taskName)

        return project.tasks.register(taskName, taskType) { task ->
            if (configAction != null) {
                configAction.delegate = task
                configAction.resolveStrategy = Closure.DELEGATE_FIRST
                configAction.call(task)
            }
        }
    }

    /**
     * Wire a task dependency between two tasks.
     * Safe to call even if dependent task doesn't exist yet (lazy wiring).
     *
     * @param project The Gradle project
     * @param dependentTaskName The task that depends on another
     * @param dependsOnTaskName The task that must run first
     */
    protected void wireTaskDependency(Project project, String dependentTaskName, String dependsOnTaskName) {
        LOGGER.debug("Wiring dependency: {} -> {}", dependentTaskName, dependsOnTaskName)

        project.tasks.named(dependentTaskName).configure { task ->
            task.dependsOn(dependsOnTaskName)
        }
    }

    /**
     * Wire a finalizedBy relationship between two tasks.
     * The finalizer task will run after the main task, regardless of success or failure.
     *
     * @param project The Gradle project
     * @param taskName The main task
     * @param finalizerTaskName The task to run after the main task
     */
    protected void wireFinalizedBy(Project project, String taskName, String finalizerTaskName) {
        LOGGER.debug("Wiring finalizedBy: {} -> {}", taskName, finalizerTaskName)

        project.tasks.named(taskName).configure { task ->
            task.finalizedBy(finalizerTaskName)
        }
    }

    /**
     * Get or create a lifecycle task.
     * Lifecycle tasks serve as entry points that aggregate multiple tasks.
     *
     * @param project The Gradle project
     * @param taskName The name of the lifecycle task
     * @param group The task group for display in gradle tasks
     * @param description The task description
     * @return TaskProvider for the lifecycle task
     */
    protected TaskProvider<Task> getOrCreateLifecycleTask(
            Project project,
            String taskName,
            String group,
            String description) {

        def existingTask = project.tasks.findByName(taskName)
        if (existingTask != null) {
            LOGGER.debug("Found existing lifecycle task: {}", taskName)
            return project.tasks.named(taskName)
        }

        LOGGER.debug("Creating lifecycle task: {}", taskName)
        return project.tasks.register(taskName) { task ->
            task.group = group
            task.description = description
        }
    }

    /**
     * Configure a test task to write its result to a state file.
     * The doLast action captures the test outcome for downstream conditional tasks.
     *
     * @param project The Gradle project
     * @param testTask The test task provider to configure
     * @param stateDir The directory for state files (relative to build directory)
     */
    protected void configureTestResultOutput(
            Project project,
            TaskProvider<? extends Task> testTask,
            String stateDir) {

        LOGGER.debug("Configuring test result output for task: {}", testTask.name)

        def resultFile = project.layout.buildDirectory.file("${stateDir}/test-result.json")

        testTask.configure { task ->
            task.outputs.file(resultFile)

            task.doLast {
                def success = task.state.failure == null
                def message = success ? "Tests passed" : task.state.failure?.message ?: "Tests failed"
                def timestamp = System.currentTimeMillis()

                PipelineStateFile.writeTestResult(
                    resultFile.get().asFile,
                    success,
                    message,
                    timestamp
                )

                LOGGER.info("Test result written to {}: success={}", resultFile.get().asFile, success)
            }
        }
    }

    /**
     * Configure a task to only run when tests have passed.
     * Uses the test result file as input for the onlyIf predicate.
     *
     * @param project The Gradle project
     * @param taskName The name of the conditional task
     * @param stateDir The directory containing state files
     */
    protected void configureOnlyIfTestsPassed(Project project, String taskName, String stateDir) {
        def resultFile = project.layout.buildDirectory.file("${stateDir}/test-result.json")

        project.tasks.named(taskName).configure { task ->
            task.onlyIf { t ->
                def file = resultFile.get().asFile
                if (!file.exists()) {
                    LOGGER.debug("Test result file not found, skipping task: {}", t.name)
                    return false
                }
                def success = PipelineStateFile.isTestSuccessful(file)
                if (!success) {
                    LOGGER.info("Tests did not pass, skipping task: {}", t.name)
                }
                return success
            }
        }
    }

    /**
     * Configure a task to always run (for cleanup tasks).
     * Cleanup tasks should run regardless of whether previous tasks succeeded or failed.
     *
     * @param taskProvider The task to configure
     */
    protected void configureAlwaysRun(TaskProvider<? extends Task> taskProvider) {
        taskProvider.configure { task ->
            task.onlyIf { true }
        }
    }

    /**
     * Check if a task exists in the project.
     *
     * @param project The Gradle project
     * @param taskName The task name to check
     * @return true if the task exists, false if task doesn't exist or taskName is null
     */
    protected boolean taskExists(Project project, String taskName) {
        if (taskName == null) {
            return false
        }
        return project.tasks.findByName(taskName) != null
    }
}
