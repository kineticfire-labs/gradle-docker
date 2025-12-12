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

import org.gradle.api.Task
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import javax.inject.Inject

/**
 * Build service for task execution within pipelines.
 *
 * This service provides configuration-cache-compatible task execution by holding
 * the TaskContainer reference within a BuildService (which can hold non-serializable state).
 *
 * The service is registered per-project and stores the TaskContainer at configuration time.
 * Tasks can then look up and execute other tasks through this service at execution time.
 *
 * Configuration Cache Compatibility:
 * - BuildService instances are NOT serialized; they're recreated on cache hit
 * - However, the service holds TaskContainer which is available at execution time
 * - Tasks reference the service via Provider<TaskExecutionService> which IS serializable
 *
 * Note: This approach still generates configuration cache warnings because the fundamental
 * problem is that dynamically executing tasks by name is not configuration-cache-friendly.
 * However, using a BuildService is the recommended Gradle pattern for this use case.
 */
abstract class TaskExecutionService implements BuildService<TaskExecutionService.Params> {

    private static final Logger LOGGER = Logging.getLogger(TaskExecutionService)

    /**
     * Parameters for the build service.
     *
     * Note: TaskContainer cannot be stored in parameters (not serializable).
     * Instead, we set it directly on the service instance after creation.
     */
    interface Params extends BuildServiceParameters {
        // No parameters - TaskContainer is set directly on service instance
    }

    // TaskContainer is set after service creation during plugin configuration
    private TaskContainer taskContainer

    @Inject
    TaskExecutionService() {
        LOGGER.debug("TaskExecutionService created")
    }

    /**
     * Set the TaskContainer for this service.
     *
     * Must be called during plugin configuration, after the service is registered.
     *
     * @param taskContainer The project's task container
     */
    void setTaskContainer(TaskContainer taskContainer) {
        this.taskContainer = taskContainer
        LOGGER.debug("TaskContainer set on TaskExecutionService")
    }

    /**
     * Find a task by name.
     *
     * @param taskName The name of the task to find
     * @return The task, or null if not found
     */
    Task findTask(String taskName) {
        if (taskContainer == null) {
            throw new IllegalStateException(
                "TaskContainer not set. Ensure plugin calls setTaskContainer() during configuration.")
        }
        return taskContainer.findByName(taskName)
    }

    /**
     * Execute a task's actions by name.
     *
     * This directly executes the task's actions without going through Gradle's
     * normal task execution machinery. This is appropriate for pipeline orchestration
     * where the pipeline task itself manages execution order.
     *
     * @param taskName The name of the task to execute
     * @throws IllegalStateException if TaskContainer is not set
     * @throws IllegalArgumentException if task is not found
     */
    void executeTask(String taskName) {
        def task = findTask(taskName)
        if (task == null) {
            throw new IllegalArgumentException("Task '${taskName}' not found")
        }
        executeTask(task)
    }

    /**
     * Execute a task's actions.
     *
     * @param task The task to execute
     */
    void executeTask(Task task) {
        LOGGER.info("Executing task: {}", task.name)
        task.actions.each { action ->
            action.execute(task)
        }
        LOGGER.debug("Task {} execution complete", task.name)
    }

    /**
     * Check if a task exists.
     *
     * @param taskName The name of the task
     * @return true if the task exists, false otherwise
     */
    boolean hasTask(String taskName) {
        return findTask(taskName) != null
    }
}
