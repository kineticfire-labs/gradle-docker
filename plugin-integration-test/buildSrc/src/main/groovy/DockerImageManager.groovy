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

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations

/**
 * Helper class for managing Docker images in integration tests
 */
class DockerImageManager {

    /**
     * Ensures a Docker image exists locally, pulling it if necessary
     */
    static void ensureImageExists(ExecOperations execOps, Logger logger, String imageRef) {
        logger.info("Checking if Docker image exists locally: ${imageRef}")

        if (imageExists(execOps, logger, imageRef)) {
            logger.info("✓ Docker image already exists locally: ${imageRef}")
            return
        }

        logger.lifecycle("Pulling Docker image: ${imageRef}")
        pullImage(execOps, logger, imageRef)

        // Verify the image was pulled successfully
        if (imageExists(execOps, logger, imageRef)) {
            logger.lifecycle("✓ Successfully pulled Docker image: ${imageRef}")
        } else {
            throw new GradleException("Failed to pull Docker image: ${imageRef}")
        }
    }

    /**
     * Checks if a Docker image exists locally
     */
    static boolean imageExists(ExecOperations execOps, Logger logger, String imageRef) {
        try {
            def stdout = new ByteArrayOutputStream()
            def stderr = new ByteArrayOutputStream()

            def result = execOps.exec {
                commandLine 'docker', 'images', '-q', imageRef
                standardOutput = stdout
                errorOutput = stderr
                ignoreExitValue = true
            }

            if (result.exitValue != 0) {
                logger.warn("Failed to check if image exists: ${imageRef}")
                return false
            }

            def output = stdout.toString().trim()

            // If full reference didn't work, try the short name (Docker stores images with short names)
            if (output.isEmpty() && imageRef.contains('/')) {
                def shortRef = imageRef.replaceFirst(/^[^\/]+\//, '').replaceFirst(/^library\//, '')
                logger.debug("Trying short reference: ${shortRef}")
                return imageExists(execOps, logger, shortRef)
            }

            return !output.isEmpty()

        } catch (Exception e) {
            logger.warn("Error checking if Docker image exists: ${e.message}")
            return false
        }
    }

    /**
     * Pulls a Docker image from a registry
     */
    static void pullImage(ExecOperations execOps, Logger logger, String imageRef) {
        try {
            def result = execOps.exec {
                commandLine 'docker', 'pull', imageRef
                ignoreExitValue = true
            }

            if (result.exitValue != 0) {
                throw new GradleException("Docker pull failed for image: ${imageRef}")
            }

        } catch (Exception e) {
            throw new GradleException("Failed to pull Docker image '${imageRef}': ${e.message}", e)
        }
    }

    /**
     * Removes a Docker image if it exists locally
     */
    static void removeImageIfExists(ExecOperations execOps, Logger logger, String imageRef) {
        if (!imageExists(execOps, logger, imageRef)) {
            logger.info("Image does not exist locally, skipping removal: ${imageRef}")
            return
        }

        try {
            logger.info("Removing Docker image: ${imageRef}")
            def result = execOps.exec {
                commandLine 'docker', 'rmi', imageRef
                ignoreExitValue = true
            }

            if (result.exitValue == 0) {
                logger.info("✓ Successfully removed Docker image: ${imageRef}")
            } else {
                logger.warn("Failed to remove Docker image: ${imageRef}")
            }

        } catch (Exception e) {
            logger.warn("Error removing Docker image '${imageRef}': ${e.message}")
        }
    }
}