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

import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.operation.TagOperationExecutor
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Executor for success path operations when tests pass
 *
 * Executes operations in sequence: tags → save → publish → afterSuccess hook.
 * Uses TagOperationExecutor for applying additional tags to the built image.
 */
class SuccessStepExecutor {

    private static final Logger LOGGER = Logging.getLogger(SuccessStepExecutor)

    private final TagOperationExecutor tagOperationExecutor
    private DockerService dockerService

    SuccessStepExecutor() {
        this.tagOperationExecutor = new TagOperationExecutor()
    }

    /**
     * Constructor for dependency injection (testing)
     */
    SuccessStepExecutor(TagOperationExecutor tagOperationExecutor) {
        this.tagOperationExecutor = tagOperationExecutor
    }

    /**
     * Set the DockerService for Docker operations
     */
    void setDockerService(DockerService dockerService) {
        this.dockerService = dockerService
    }

    /**
     * Execute success path operations
     *
     * @param successSpec The success step specification
     * @param context The current pipeline context
     * @return Updated pipeline context after success operations
     * @throws GradleException if any operation fails
     */
    PipelineContext execute(SuccessStepSpec successSpec, PipelineContext context) {
        if (successSpec == null) {
            LOGGER.info("No success spec configured - skipping success operations")
            return context
        }

        LOGGER.lifecycle("Executing success path for pipeline: {}", context.pipelineName)

        // Apply additional tags if configured
        context = applyAdditionalTags(successSpec, context)

        // Save operation placeholder (to be implemented in Step 7)
        executeSaveOperation(successSpec, context)

        // Publish operation placeholder (to be implemented in Step 8)
        executePublishOperation(successSpec, context)

        // Execute afterSuccess hook if configured
        executeAfterSuccessHook(successSpec)

        LOGGER.lifecycle("Success path completed for pipeline: {}", context.pipelineName)
        return context
    }

    /**
     * Apply additional tags to the built image
     */
    PipelineContext applyAdditionalTags(SuccessStepSpec successSpec, PipelineContext context) {
        if (!hasAdditionalTags(successSpec)) {
            LOGGER.debug("No additional tags configured")
            return context
        }

        def tags = successSpec.additionalTags.get()
        def builtImage = context.builtImage

        if (builtImage == null) {
            throw new GradleException("Cannot apply tags - no built image in context")
        }

        LOGGER.info("Applying {} additional tag(s) to image", tags.size())

        if (dockerService != null) {
            tagOperationExecutor.execute(builtImage, tags, dockerService)
        } else {
            LOGGER.warn("DockerService not set - tags recorded in context only")
        }

        return context.withAppliedTags(tags)
    }

    /**
     * Check if success spec has additional tags configured
     */
    boolean hasAdditionalTags(SuccessStepSpec successSpec) {
        return successSpec.additionalTags.isPresent() && !successSpec.additionalTags.get().isEmpty()
    }

    /**
     * Execute save operation if configured
     * Placeholder for Step 7 implementation
     */
    void executeSaveOperation(SuccessStepSpec successSpec, PipelineContext context) {
        if (successSpec.save.isPresent()) {
            def saveSpec = successSpec.save.get()
            LOGGER.info("Save operation configured - will be executed by SaveOperationExecutor (Step 7)")
        }
    }

    /**
     * Execute publish operation if configured
     * Placeholder for Step 8 implementation
     */
    void executePublishOperation(SuccessStepSpec successSpec, PipelineContext context) {
        if (successSpec.publish.isPresent()) {
            def publishSpec = successSpec.publish.get()
            LOGGER.info("Publish operation configured - will be executed by PublishOperationExecutor (Step 8)")
        }
    }

    /**
     * Execute the afterSuccess hook if configured
     */
    void executeAfterSuccessHook(SuccessStepSpec successSpec) {
        if (successSpec.afterSuccess.isPresent()) {
            LOGGER.info("Executing afterSuccess hook")
            executeHook(successSpec.afterSuccess.get())
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
