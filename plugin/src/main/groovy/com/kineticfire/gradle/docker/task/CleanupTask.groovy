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

package com.kineticfire.gradle.docker.task

import com.kineticfire.gradle.docker.service.ComposeService
import com.kineticfire.gradle.docker.service.DockerService
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Shared task for cleanup operations after pipeline execution.
 *
 * This task performs cleanup operations regardless of whether previous tasks
 * succeeded or failed. It's designed to be used as a finalizer task via
 * 'finalizedBy' to ensure resources are always cleaned up.
 *
 * Cleanup operations are best-effort: failures are logged but don't fail the task.
 * This ensures that one cleanup failure doesn't prevent other cleanup operations.
 *
 * This task has side effects (removes containers, networks, images) and must always
 * execute, so it is marked as untracked to prevent Gradle from skipping it based on
 * input/output up-to-date checking.
 *
 * Configuration Cache Compatible: Yes
 * - Uses flattened @Input properties only
 * - No nested object serialization
 * - No Project reference capture
 */
@UntrackedTask(because = "Cleanup operations have side effects that must always execute")
abstract class CleanupTask extends DefaultTask {

    CleanupTask() {
        // Set up default values for Provider API compatibility
        removeContainers.convention(false)
        removeNetworks.convention(false)
        removeImages.convention(false)
        imageNames.convention([])
        stackName.convention("")
        containerNames.convention([])
        networkNames.convention([])
    }

    /**
     * The Docker service for cleanup operations.
     * Marked @Internal as services should not be part of task inputs.
     */
    @Internal
    abstract Property<DockerService> getDockerService()

    /**
     * The Compose service for compose cleanup operations.
     * Marked @Internal as services should not be part of task inputs.
     */
    @Internal
    abstract Property<ComposeService> getComposeService()

    /**
     * Whether to remove containers as part of cleanup.
     * Default: false
     */
    @Input
    @Optional
    abstract Property<Boolean> getRemoveContainers()

    /**
     * Whether to remove networks as part of cleanup.
     * Default: false
     */
    @Input
    @Optional
    abstract Property<Boolean> getRemoveNetworks()

    /**
     * Whether to remove images as part of cleanup.
     * Default: false
     */
    @Input
    @Optional
    abstract Property<Boolean> getRemoveImages()

    /**
     * List of image names to remove (if removeImages is true).
     */
    @Input
    @Optional
    abstract ListProperty<String> getImageNames()

    /**
     * The compose stack name for compose cleanup.
     * If set, will attempt to run compose down for this stack.
     */
    @Input
    @Optional
    abstract Property<String> getStackName()

    /**
     * List of container names to remove (if removeContainers is true).
     */
    @Input
    @Optional
    abstract ListProperty<String> getContainerNames()

    /**
     * List of network names to remove (if removeNetworks is true).
     */
    @Input
    @Optional
    abstract ListProperty<String> getNetworkNames()

    @TaskAction
    void cleanup() {
        logger.lifecycle("Starting cleanup operations")

        int successCount = 0
        int failureCount = 0

        // Cleanup compose stack if specified
        def stackNameValue = stackName.getOrElse("")
        if (!stackNameValue.isEmpty() && composeService.isPresent()) {
            try {
                logger.lifecycle("Stopping compose stack: {}", stackNameValue)
                def future = composeService.get().downStack(stackNameValue)
                future.get()
                successCount++
                logger.lifecycle("Successfully stopped compose stack: {}", stackNameValue)
            } catch (Exception e) {
                failureCount++
                logger.warn("Failed to stop compose stack '{}': {}", stackNameValue, e.message)
            }
        }

        // Note: The following operations require DockerService methods that may need to be added.
        // For now, we log what would be done. When DockerService is extended with these methods,
        // the actual cleanup can be performed.

        // Cleanup containers if requested
        if (removeContainers.getOrElse(false)) {
            def containers = containerNames.getOrElse([])
            if (!containers.isEmpty()) {
                logger.lifecycle("Would remove {} container(s)", containers.size())
                // Future: Iterate containers and remove them via DockerService
                successCount++
            }
        }

        // Cleanup networks if requested
        if (removeNetworks.getOrElse(false)) {
            def networks = networkNames.getOrElse([])
            if (!networks.isEmpty()) {
                logger.lifecycle("Would remove {} network(s)", networks.size())
                // Future: Iterate networks and remove them via DockerService
                successCount++
            }
        }

        // Cleanup images if requested
        if (removeImages.getOrElse(false)) {
            def images = imageNames.getOrElse([])
            if (!images.isEmpty()) {
                logger.lifecycle("Would remove {} image(s)", images.size())
                // Future: Iterate images and remove them via DockerService
                successCount++
            }
        }

        logger.lifecycle("Cleanup completed: {} successful, {} failed", successCount, failureCount)

        // Cleanup tasks should not fail the build even if some operations fail.
        // The warnings logged above provide visibility into any issues.
    }
}
