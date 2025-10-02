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
 * Gradle task that ensures a Docker image exists locally, pulling it if necessary.
 * This is useful for integration tests that need specific base images to be available.
 */
abstract class EnsureDockerImageTask extends DefaultTask {

    /**
     * The Docker image reference to ensure exists locally (e.g., "nginx:1.21", "docker.io/library/nginx:1.21")
     */
    @Input
    abstract Property<String> getImageRef()

    /**
     * Optional description of why this image is needed (for logging)
     */
    @Input
    abstract Property<String> getReason()

    @Inject
    abstract ExecOperations getExecOperations()

    EnsureDockerImageTask() {
        // Set default values
        reason.convention("Required for integration test")

        // Configure task metadata
        group = 'docker'
        description = 'Ensure a Docker image exists locally for testing'
    }

    @TaskAction
    void ensureImage() {
        def imageRefValue = imageRef.get()
        def reasonValue = reason.get()

        logger.lifecycle("Ensuring Docker image exists: ${imageRefValue}")
        logger.info("Reason: ${reasonValue}")

        try {
            DockerImageManager.ensureImageExists(execOperations, logger, imageRefValue)
            logger.lifecycle("✓ Docker image ready: ${imageRefValue}")
        } catch (Exception e) {
            logger.error("✗ Failed to ensure Docker image: ${imageRefValue}")
            throw e
        }
    }
}