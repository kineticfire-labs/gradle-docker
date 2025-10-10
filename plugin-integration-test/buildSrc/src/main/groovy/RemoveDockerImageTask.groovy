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

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

import javax.inject.Inject

/**
 * Gradle task that removes a Docker image if it exists locally.
 * This is the inverse of EnsureDockerImageTask and is useful for integration tests
 * that need to verify pullIfMissing behavior by ensuring an image is NOT available locally.
 */
abstract class RemoveDockerImageTask extends DefaultTask {

    /**
     * The Docker image reference to remove (e.g., "nginx:latest", "docker.io/library/nginx:latest")
     */
    @Input
    abstract Property<String> getImageRef()

    /**
     * Optional description of why this image needs to be removed (for logging)
     */
    @Input
    abstract Property<String> getReason()

    @Inject
    abstract ExecOperations getExecOperations()

    RemoveDockerImageTask() {
        // Set default values
        reason.convention("Required for integration test")

        // Configure task metadata
        group = 'docker'
        description = 'Remove a Docker image to ensure it is NOT available locally for testing'
    }

    @TaskAction
    void removeImage() {
        def imageRefValue = imageRef.get()
        def reasonValue = reason.get()

        logger.lifecycle("Removing Docker image: ${imageRefValue}")
        logger.info("Reason: ${reasonValue}")

        try {
            DockerImageManager.removeImageIfExists(execOperations, logger, imageRefValue)
            logger.lifecycle("✓ Docker image removed (or did not exist): ${imageRefValue}")
        } catch (Exception e) {
            logger.error("✗ Failed to remove Docker image: ${imageRefValue}")
            throw e
        }
    }
}
