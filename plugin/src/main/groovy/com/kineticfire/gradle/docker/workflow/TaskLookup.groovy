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
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer

/**
 * Interface for task lookup and execution operations.
 *
 * This abstraction allows executors to look up and execute tasks without holding
 * direct references to TaskContainer or Project, which supports configuration cache
 * compatibility patterns.
 *
 * Implementations:
 * - TaskExecutionServiceLookup: Uses TaskExecutionService BuildService (production)
 * - Test implementations can mock this interface for unit testing
 */
interface TaskLookup {

    /**
     * Find a task by name.
     *
     * @param taskName The name of the task to find
     * @return The task if found, null otherwise
     */
    Task findByName(String taskName)

    /**
     * Execute a task by name.
     *
     * @param taskName The name of the task to execute
     * @throws IllegalArgumentException if task is not found
     */
    void execute(String taskName)

    /**
     * Execute a task.
     *
     * @param task The task to execute
     */
    void execute(Task task)
}

/**
 * Factory for creating TaskLookup instances.
 */
class TaskLookupFactory {

    private TaskLookupFactory() {}

    /**
     * Create a TaskLookup from a TaskExecutionService.
     *
     * This is the preferred production method as it uses Gradle's BuildService
     * pattern for configuration cache compatibility.
     *
     * @param service The TaskExecutionService
     * @return A TaskLookup that delegates to the service
     */
    static TaskLookup from(TaskExecutionService service) {
        return new TaskExecutionServiceLookup(service)
    }

    /**
     * Create a TaskLookup from a TaskContainer.
     *
     * This method is provided for testing and non-configuration-cache scenarios.
     * For production use with configuration cache, prefer using the TaskExecutionService
     * variant via {@link #from(TaskExecutionService)}.
     *
     * @param taskContainer The TaskContainer to wrap
     * @return A TaskLookup that delegates to the TaskContainer
     */
    static TaskLookup fromTaskContainer(TaskContainer taskContainer) {
        return new TaskContainerLookup(taskContainer)
    }
}

/**
 * TaskLookup implementation that wraps a TaskExecutionService.
 *
 * This implementation is configuration-cache friendly because:
 * 1. It doesn't store TaskContainer directly
 * 2. TaskExecutionService is a BuildService which handles its own lifecycle
 * 3. The executors receive this lookup at execution time, not configuration time
 */
class TaskExecutionServiceLookup implements TaskLookup {

    private final TaskExecutionService service

    TaskExecutionServiceLookup(TaskExecutionService service) {
        this.service = service
    }

    @Override
    Task findByName(String taskName) {
        if (taskName == null) {
            return null
        }
        return service.findTask(taskName)
    }

    @Override
    void execute(String taskName) {
        service.executeTask(taskName)
    }

    @Override
    void execute(Task task) {
        service.executeTask(task)
    }
}

/**
 * TaskLookup implementation that wraps a TaskContainer.
 *
 * This implementation is provided for testing and non-configuration-cache scenarios.
 * For production use with configuration cache, use TaskExecutionServiceLookup instead.
 */
class TaskContainerLookup implements TaskLookup {

    private final TaskContainer taskContainer

    TaskContainerLookup(TaskContainer taskContainer) {
        this.taskContainer = taskContainer
    }

    @Override
    Task findByName(String taskName) {
        if (taskName == null) {
            return null
        }
        return taskContainer.findByName(taskName)
    }

    @Override
    void execute(String taskName) {
        Task task = findByName(taskName)
        if (task == null) {
            throw new IllegalArgumentException("Task '${taskName}' not found")
        }
        execute(task)
    }

    @Override
    void execute(Task task) {
        task.actions.each { action ->
            action.execute(task)
        }
    }
}
