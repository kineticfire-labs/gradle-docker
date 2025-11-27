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

import com.kineticfire.gradle.docker.service.ComposeService
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.workflow.AlwaysStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Executor for cleanup operations that always run
 *
 * Executes cleanup operations regardless of test success or failure.
 * Handles errors gracefully - logs but doesn't fail the build.
 */
class AlwaysStepExecutor {

    private static final Logger LOGGER = Logging.getLogger(AlwaysStepExecutor)

    private ComposeService composeService
    private DockerService dockerService
    private String composeProjectName

    AlwaysStepExecutor() {
    }

    /**
     * Set the ComposeService for container operations
     */
    void setComposeService(ComposeService composeService) {
        this.composeService = composeService
    }

    /**
     * Set the DockerService for image operations
     */
    void setDockerService(DockerService dockerService) {
        this.dockerService = dockerService
    }

    /**
     * Set the Compose project name for container cleanup
     */
    void setComposeProjectName(String projectName) {
        this.composeProjectName = projectName
    }

    /**
     * Execute cleanup operations
     *
     * @param alwaysSpec The always step specification
     * @param context The current pipeline context
     * @param testsPassed Whether the tests passed (affects keepFailedContainers logic)
     * @return Updated pipeline context after cleanup operations
     */
    PipelineContext execute(AlwaysStepSpec alwaysSpec, PipelineContext context, boolean testsPassed) {
        if (alwaysSpec == null) {
            LOGGER.info("No always spec configured - skipping cleanup operations")
            return context
        }

        LOGGER.lifecycle("Executing cleanup for pipeline: {}", context.pipelineName)

        // Remove test containers if configured
        removeTestContainersIfConfigured(alwaysSpec, testsPassed)

        // Clean up images if configured
        cleanupImagesIfConfigured(alwaysSpec, context)

        LOGGER.lifecycle("Cleanup completed for pipeline: {}", context.pipelineName)
        return context
    }

    /**
     * Remove test containers if configured and appropriate
     */
    void removeTestContainersIfConfigured(AlwaysStepSpec alwaysSpec, boolean testsPassed) {
        if (!shouldRemoveContainers(alwaysSpec, testsPassed)) {
            LOGGER.debug("Skipping container removal based on configuration")
            return
        }

        if (composeService == null || composeProjectName == null) {
            LOGGER.debug("ComposeService or project name not set - skipping container cleanup")
            return
        }

        LOGGER.info("Removing test containers for project: {}", composeProjectName)

        try {
            removeTestContainers()
            LOGGER.info("Successfully removed test containers")
        } catch (Exception e) {
            LOGGER.warn("Failed to remove test containers: {} - continuing anyway", e.message)
            // Don't fail the build - cleanup is best-effort
        }
    }

    /**
     * Determine if containers should be removed based on configuration
     */
    boolean shouldRemoveContainers(AlwaysStepSpec alwaysSpec, boolean testsPassed) {
        def removeContainers = alwaysSpec.removeTestContainers.getOrElse(true)
        def keepOnFailure = alwaysSpec.keepFailedContainers.getOrElse(false)

        if (!removeContainers) {
            LOGGER.debug("Container removal disabled")
            return false
        }

        if (!testsPassed && keepOnFailure) {
            LOGGER.info("Keeping containers for debugging - tests failed and keepFailedContainers=true")
            return false
        }

        return true
    }

    /**
     * Remove test containers using Compose
     */
    void removeTestContainers() {
        def future = composeService.downStack(composeProjectName)
        future.get()
    }

    /**
     * Clean up images if configured
     */
    void cleanupImagesIfConfigured(AlwaysStepSpec alwaysSpec, PipelineContext context) {
        if (!shouldCleanupImages(alwaysSpec)) {
            LOGGER.debug("Skipping image cleanup")
            return
        }

        if (dockerService == null) {
            LOGGER.debug("DockerService not set - skipping image cleanup")
            return
        }

        def builtImage = context.builtImage
        if (builtImage == null) {
            LOGGER.debug("No built image in context - skipping image cleanup")
            return
        }

        LOGGER.info("Cleaning up built images")

        try {
            cleanupImages(context)
            LOGGER.info("Successfully cleaned up images")
        } catch (Exception e) {
            LOGGER.warn("Failed to clean up images: {} - continuing anyway", e.message)
            // Don't fail the build - cleanup is best-effort
        }
    }

    /**
     * Check if image cleanup is configured
     */
    boolean shouldCleanupImages(AlwaysStepSpec alwaysSpec) {
        return alwaysSpec.cleanupImages.getOrElse(false)
    }

    /**
     * Clean up built images
     * Note: DockerService.removeImage() not yet implemented - this is a placeholder
     */
    void cleanupImages(PipelineContext context) {
        // TODO: Implement when DockerService.removeImage() is available
        // For now, just log what would be cleaned up
        def imageSpec = context.builtImage
        if (imageSpec != null) {
            def imageName = imageSpec.imageName.getOrElse("unknown")
            LOGGER.info("Would clean up image: {} (not yet implemented)", imageName)
        }
    }
}
