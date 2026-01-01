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

package com.kineticfire.gradle.docker.workflow.executor

import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
import com.kineticfire.gradle.docker.workflow.HookContext
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TaskLookup
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Executor for the build step in a pipeline workflow.
 *
 * Orchestrates the execution of beforeBuild hooks, docker build task invocation,
 * and afterBuild hooks. Updates the PipelineContext with the built image.
 *
 * Configuration cache compatible - uses TaskLookup abstraction which is backed
 * by TaskExecutionService (a Gradle BuildService).
 */
class BuildStepExecutor {

    private static final Logger LOGGER = Logging.getLogger(BuildStepExecutor)

    private final TaskLookup taskLookup

    /**
     * Create executor with TaskLookup abstraction.
     *
     * @param taskLookup The task lookup for finding and executing tasks
     */
    BuildStepExecutor(TaskLookup taskLookup) {
        this.taskLookup = taskLookup
    }

    /**
     * Execute the build step.
     *
     * @param buildSpec The build step specification
     * @param context The current pipeline context
     * @return Updated pipeline context with built image
     * @throws GradleException if build fails or required configuration is missing
     */
    PipelineContext execute(BuildStepSpec buildSpec, PipelineContext context) {
        validateBuildSpec(buildSpec)

        def imageSpec = buildSpec.image.get()
        def imageName = imageSpec.name
        def buildTaskName = computeBuildTaskName(imageName)
        LOGGER.lifecycle("Executing build step for image: {}", imageName)

        // Execute beforeBuild hook if configured
        executeBeforeBuildHook(buildSpec, buildTaskName, context.pipelineName)

        // Execute the docker build task
        executeBuildTask(buildTaskName)

        // Execute afterBuild hook if configured
        executeAfterBuildHook(buildSpec, buildTaskName, context.pipelineName)

        // Update context with built image
        return context.withBuiltImage(imageSpec)
    }

    /**
     * Validate the build step specification.
     */
    void validateBuildSpec(BuildStepSpec buildSpec) {
        if (buildSpec == null) {
            throw new GradleException("BuildStepSpec cannot be null")
        }
        if (!buildSpec.image.isPresent()) {
            throw new GradleException("BuildStepSpec.image must be configured")
        }
    }

    /**
     * Compute the docker build task name for an image.
     * Follows the pattern: dockerBuild{ImageName} where ImageName is capitalized.
     */
    String computeBuildTaskName(String imageName) {
        def capitalizedName = capitalizeFirstLetter(imageName)
        return "dockerBuild${capitalizedName}"
    }

    /**
     * Capitalize the first letter of a string.
     */
    String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1)
    }

    /**
     * Execute the docker build task.
     */
    void executeBuildTask(String taskName) {
        LOGGER.info("Looking up build task: {}", taskName)
        def task = lookupTask(taskName)
        if (task == null) {
            throw new GradleException("Build task '${taskName}' not found. " +
                "Ensure the image is configured in docker.images DSL.")
        }

        LOGGER.info("Executing build task: {}", taskName)
        executeTask(task)
        LOGGER.lifecycle("Build task {} completed successfully", taskName)
    }

    /**
     * Look up a task by name.
     * Separated for testability.
     */
    Task lookupTask(String taskName) {
        return taskLookup.findByName(taskName)
    }

    /**
     * Execute a task's actions.
     * Separated for testability.
     */
    void executeTask(Task task) {
        taskLookup.execute(task)
    }

    /**
     * Execute the beforeBuild hook if configured.
     *
     * @param buildSpec The build step specification
     * @param taskName The name of the build task
     * @param pipelineName The name of the pipeline
     */
    void executeBeforeBuildHook(BuildStepSpec buildSpec, String taskName, String pipelineName) {
        if (buildSpec.beforeBuild.isPresent()) {
            LOGGER.info("Executing beforeBuild hook")
            def hookContext = HookContext.before(taskName, pipelineName)
            executeHook(buildSpec.beforeBuild.get(), hookContext)
        }
    }

    /**
     * Execute the afterBuild hook if configured.
     *
     * @param buildSpec The build step specification
     * @param taskName The name of the build task
     * @param pipelineName The name of the pipeline
     */
    void executeAfterBuildHook(BuildStepSpec buildSpec, String taskName, String pipelineName) {
        if (buildSpec.afterBuild.isPresent()) {
            LOGGER.info("Executing afterBuild hook")
            def hookContext = HookContext.after(taskName, pipelineName)
            executeHook(buildSpec.afterBuild.get(), hookContext)
        }
    }

    /**
     * Execute a hook action with context.
     * Separated for testability.
     *
     * @param hook The hook action to execute
     * @param hookContext The context to pass to the hook
     */
    void executeHook(Action<HookContext> hook, HookContext hookContext) {
        hook.execute(hookContext)
    }
}
