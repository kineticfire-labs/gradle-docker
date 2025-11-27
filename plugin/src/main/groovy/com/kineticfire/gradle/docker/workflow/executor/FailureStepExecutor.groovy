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

import com.kineticfire.gradle.docker.model.LogsConfig
import com.kineticfire.gradle.docker.service.ComposeService
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.operation.TagOperationExecutor
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Executor for failure path operations when tests fail
 *
 * Executes operations in sequence: failure tags → save logs → afterFailure hook.
 * Uses TagOperationExecutor for applying failure tags to the built image.
 */
class FailureStepExecutor {

    private static final Logger LOGGER = Logging.getLogger(FailureStepExecutor)

    private final TagOperationExecutor tagOperationExecutor
    private DockerService dockerService
    private ComposeService composeService
    private String composeProjectName

    FailureStepExecutor() {
        this.tagOperationExecutor = new TagOperationExecutor()
    }

    /**
     * Constructor for dependency injection (testing)
     */
    FailureStepExecutor(TagOperationExecutor tagOperationExecutor) {
        this.tagOperationExecutor = tagOperationExecutor
    }

    /**
     * Set the DockerService for Docker operations
     */
    void setDockerService(DockerService dockerService) {
        this.dockerService = dockerService
    }

    /**
     * Set the ComposeService for log capture operations
     */
    void setComposeService(ComposeService composeService) {
        this.composeService = composeService
    }

    /**
     * Set the Compose project name for log capture
     */
    void setComposeProjectName(String projectName) {
        this.composeProjectName = projectName
    }

    /**
     * Execute failure path operations
     *
     * @param failureSpec The failure step specification
     * @param context The current pipeline context
     * @return Updated pipeline context after failure operations
     * @throws GradleException if any operation fails
     */
    PipelineContext execute(FailureStepSpec failureSpec, PipelineContext context) {
        if (failureSpec == null) {
            LOGGER.info("No failure spec configured - skipping failure operations")
            return context
        }

        LOGGER.lifecycle("Executing failure path for pipeline: {}", context.pipelineName)

        // Apply failure tags if configured
        context = applyFailureTags(failureSpec, context)

        // Save failure logs if configured
        saveFailureLogs(failureSpec, context)

        // Execute afterFailure hook if configured
        executeAfterFailureHook(failureSpec)

        LOGGER.lifecycle("Failure path completed for pipeline: {}", context.pipelineName)
        return context
    }

    /**
     * Apply failure tags to the built image
     */
    PipelineContext applyFailureTags(FailureStepSpec failureSpec, PipelineContext context) {
        if (!hasAdditionalTags(failureSpec)) {
            LOGGER.debug("No failure tags configured")
            return context
        }

        def tags = failureSpec.additionalTags.get()
        def builtImage = context.builtImage

        if (builtImage == null) {
            LOGGER.warn("Cannot apply failure tags - no built image in context")
            return context
        }

        LOGGER.info("Applying {} failure tag(s) to image", tags.size())

        if (dockerService != null) {
            tagOperationExecutor.execute(builtImage, tags, dockerService)
        } else {
            LOGGER.warn("DockerService not set - tags recorded in context only")
        }

        return context.withAppliedTags(tags)
    }

    /**
     * Check if failure spec has additional tags configured
     */
    boolean hasAdditionalTags(FailureStepSpec failureSpec) {
        return failureSpec.additionalTags.isPresent() && !failureSpec.additionalTags.get().isEmpty()
    }

    /**
     * Save failure logs to the configured directory
     */
    void saveFailureLogs(FailureStepSpec failureSpec, PipelineContext context) {
        if (!hasSaveLogsConfigured(failureSpec)) {
            LOGGER.debug("No failure log saving configured")
            return
        }

        if (composeService == null || composeProjectName == null) {
            LOGGER.warn("ComposeService or project name not set - cannot save failure logs")
            return
        }

        def logsDir = failureSpec.saveFailureLogsDir.get().asFile
        def serviceNames = failureSpec.includeServices.getOrElse([])

        LOGGER.info("Saving failure logs to: {}", logsDir.absolutePath)

        try {
            saveLogsToDirectory(logsDir, serviceNames)
        } catch (Exception e) {
            LOGGER.error("Failed to save failure logs: {}", e.message)
            // Don't fail the build - this is a best-effort operation
        }
    }

    /**
     * Save logs to the specified directory
     */
    void saveLogsToDirectory(File logsDir, List<String> serviceNames) {
        // Ensure directory exists
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }

        def logsConfig = new LogsConfig(
            serviceNames.isEmpty() ? null : serviceNames,
            1000  // Last 1000 lines
        )

        def logsFuture = composeService.captureLogs(composeProjectName, logsConfig)
        def logs = logsFuture.get()

        def logFile = new File(logsDir, "failure-logs-${System.currentTimeMillis()}.log")
        logFile.text = logs

        LOGGER.info("Saved failure logs to: {}", logFile.absolutePath)
    }

    /**
     * Check if log saving is configured
     */
    boolean hasSaveLogsConfigured(FailureStepSpec failureSpec) {
        return failureSpec.saveFailureLogsDir.isPresent()
    }

    /**
     * Execute the afterFailure hook if configured
     */
    void executeAfterFailureHook(FailureStepSpec failureSpec) {
        if (failureSpec.afterFailure.isPresent()) {
            LOGGER.info("Executing afterFailure hook")
            executeHook(failureSpec.afterFailure.get())
        }
    }

    /**
     * Execute a hook action
     * Separated for testability
     */
    void executeHook(Action<Void> hook) {
        hook.execute(null)
    }
}
