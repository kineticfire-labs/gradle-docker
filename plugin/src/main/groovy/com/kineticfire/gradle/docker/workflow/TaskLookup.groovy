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

import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer

/**
 * Interface for task lookup operations
 *
 * This abstraction allows executors to look up tasks without holding a reference
 * to the Project, which is required for configuration cache compatibility.
 *
 * The interface is implemented as a simple wrapper around TaskContainer,
 * which can be safely captured during configuration time and used during execution.
 *
 * Use TaskLookupFactory.from() to create instances.
 */
interface TaskLookup {

    /**
     * Find a task by name
     *
     * @param taskName The name of the task to find
     * @return The task if found, null otherwise
     */
    Task findByName(String taskName)
}

/**
 * Factory for creating TaskLookup instances
 */
class TaskLookupFactory {

    private TaskLookupFactory() {}

    /**
     * Create a TaskLookup from a TaskContainer
     *
     * TaskContainer can be safely serialized for configuration cache.
     *
     * @param tasks The TaskContainer to wrap
     * @return A TaskLookup that delegates to the TaskContainer
     */
    static TaskLookup from(TaskContainer tasks) {
        return new TaskContainerLookup(tasks)
    }
}

/**
 * TaskLookup implementation that wraps a TaskContainer
 *
 * TaskContainer is configuration cache compatible when captured
 * at configuration time, unlike the Project reference.
 */
class TaskContainerLookup implements TaskLookup, Serializable {

    private static final long serialVersionUID = 1L

    private final TaskContainer tasks

    TaskContainerLookup(TaskContainer tasks) {
        this.tasks = tasks
    }

    @Override
    Task findByName(String taskName) {
        if (taskName == null) {
            return null
        }
        return tasks.findByName(taskName)
    }
}
