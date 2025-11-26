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
import com.kineticfire.gradle.docker.workflow.operation.PublishOperationExecutor
import com.kineticfire.gradle.docker.workflow.operation.SaveOperationExecutor
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
    private final SaveOperationExecutor saveOperationExecutor
    private final PublishOperationExecutor publishOperationExecutor
    private DockerService dockerService

    SuccessStepExecutor() {
        this.tagOperationExecutor = new TagOperationExecutor()
        this.saveOperationExecutor = new SaveOperationExecutor()
        this.publishOperationExecutor = new PublishOperationExecutor()
    }

    /**
     * Constructor for dependency injection (testing)
     */
    SuccessStepExecutor(TagOperationExecutor tagOperationExecutor) {
        this.tagOperationExecutor = tagOperationExecutor
        this.saveOperationExecutor = new SaveOperationExecutor()
        this.publishOperationExecutor = new PublishOperationExecutor()
    }

    /**
     * Constructor for dependency injection with save executor (testing)
     */
    SuccessStepExecutor(TagOperationExecutor tagOperationExecutor,
                        SaveOperationExecutor saveOperationExecutor) {
        this.tagOperationExecutor = tagOperationExecutor
        this.saveOperationExecutor = saveOperationExecutor
        this.publishOperationExecutor = new PublishOperationExecutor()
    }

    /**
     * Constructor for full dependency injection (testing)
     */
    SuccessStepExecutor(TagOperationExecutor tagOperationExecutor,
                        SaveOperationExecutor saveOperationExecutor,
                        PublishOperationExecutor publishOperationExecutor) {
        this.tagOperationExecutor = tagOperationExecutor
        this.saveOperationExecutor = saveOperationExecutor
        this.publishOperationExecutor = publishOperationExecutor
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
     */
    void executeSaveOperation(SuccessStepSpec successSpec, PipelineContext context) {
        if (!hasSaveConfigured(successSpec)) {
            LOGGER.debug("No save operation configured")
            return
        }

        def saveSpec = successSpec.save.get()
        def builtImage = context.builtImage

        if (builtImage == null) {
            throw new GradleException("Cannot save image - no built image in context")
        }

        LOGGER.info("Executing save operation for image")

        if (dockerService != null) {
            saveOperationExecutor.execute(saveSpec, builtImage, dockerService)
        } else {
            LOGGER.warn("DockerService not set - save operation skipped")
        }
    }

    /**
     * Check if save operation is configured
     */
    boolean hasSaveConfigured(SuccessStepSpec successSpec) {
        return successSpec.save.isPresent()
    }

    /**
     * Execute publish operation if configured
     */
    void executePublishOperation(SuccessStepSpec successSpec, PipelineContext context) {
        if (!hasPublishConfigured(successSpec)) {
            LOGGER.debug("No publish operation configured")
            return
        }

        def publishSpec = successSpec.publish.get()
        def builtImage = context.builtImage

        if (builtImage == null) {
            throw new GradleException("Cannot publish image - no built image in context")
        }

        LOGGER.info("Executing publish operation for image")

        if (dockerService != null) {
            publishOperationExecutor.execute(publishSpec, builtImage, dockerService)
        } else {
            LOGGER.warn("DockerService not set - publish operation skipped")
        }
    }

    /**
     * Check if publish operation is configured
     */
    boolean hasPublishConfigured(SuccessStepSpec successSpec) {
        return successSpec.publish.isPresent()
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
