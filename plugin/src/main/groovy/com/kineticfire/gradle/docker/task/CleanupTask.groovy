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

        // Cleanup containers if requested
        if (removeContainers.getOrElse(false)) {
            def containers = containerNames.getOrElse([])
            if (!containers.isEmpty() && dockerService.isPresent()) {
                logger.lifecycle("Removing {} container(s)", containers.size())
                def results = removeContainers(containers)
                successCount += results.successes
                failureCount += results.failures
            }
        }

        // Cleanup networks if requested
        if (removeNetworks.getOrElse(false)) {
            def networks = networkNames.getOrElse([])
            if (!networks.isEmpty() && dockerService.isPresent()) {
                logger.lifecycle("Removing {} network(s)", networks.size())
                def results = removeNetworks(networks)
                successCount += results.successes
                failureCount += results.failures
            }
        }

        // Cleanup images if requested
        if (removeImages.getOrElse(false)) {
            def images = imageNames.getOrElse([])
            if (!images.isEmpty() && dockerService.isPresent()) {
                logger.lifecycle("Removing {} image(s)", images.size())
                def results = removeImages(images)
                successCount += results.successes
                failureCount += results.failures
            }
        }

        logger.lifecycle("Cleanup completed: {} successful, {} failed", successCount, failureCount)

        // Cleanup tasks should not fail the build even if some operations fail.
        // The warnings logged above provide visibility into any issues.
    }
    
    /**
     * Result holder for cleanup operations.
     * Tracks successful and failed cleanup counts.
     */
    protected static class CleanupResult {
        int successes = 0
        int failures = 0
    }
    
    /**
     * Remove containers via DockerService.
     * Best-effort: continues on failure, logs warnings.
     * 
     * @param containers List of container IDs or names to remove
     * @return CleanupResult with success and failure counts
     */
    protected CleanupResult removeContainers(List<String> containers) {
        def result = new CleanupResult()
        def service = dockerService.get()
        
        containers.each { containerId ->
            try {
                def future = service.removeContainer(containerId)
                def success = future.get()
                if (success) {
                    result.successes++
                    logger.lifecycle("Removed container: {}", containerId)
                } else {
                    result.failures++
                    logger.warn("Failed to remove container: {}", containerId)
                }
            } catch (Exception e) {
                result.failures++
                logger.warn("Error removing container '{}': {}", containerId, e.message)
            }
        }
        
        return result
    }
    
    /**
     * Remove networks via DockerService.
     * Best-effort: continues on failure, logs warnings.
     * 
     * @param networks List of network IDs or names to remove
     * @return CleanupResult with success and failure counts
     */
    protected CleanupResult removeNetworks(List<String> networks) {
        def result = new CleanupResult()
        def service = dockerService.get()
        
        networks.each { networkId ->
            try {
                def future = service.removeNetwork(networkId)
                def success = future.get()
                if (success) {
                    result.successes++
                    logger.lifecycle("Removed network: {}", networkId)
                } else {
                    result.failures++
                    logger.warn("Failed to remove network: {}", networkId)
                }
            } catch (Exception e) {
                result.failures++
                logger.warn("Error removing network '{}': {}", networkId, e.message)
            }
        }
        
        return result
    }
    
    /**
     * Remove images via DockerService.
     * Best-effort: continues on failure, logs warnings.
     * 
     * @param images List of image references to remove
     * @return CleanupResult with success and failure counts
     */
    protected CleanupResult removeImages(List<String> images) {
        def result = new CleanupResult()
        def service = dockerService.get()
        
        images.each { imageRef ->
            try {
                def future = service.removeImage(imageRef)
                def success = future.get()
                if (success) {
                    result.successes++
                    logger.lifecycle("Removed image: {}", imageRef)
                } else {
                    result.failures++
                    logger.warn("Failed to remove image: {}", imageRef)
                }
            } catch (Exception e) {
                result.failures++
                logger.warn("Error removing image '{}': {}", imageRef, e.message)
            }
        }
        
        return result
    }
}
