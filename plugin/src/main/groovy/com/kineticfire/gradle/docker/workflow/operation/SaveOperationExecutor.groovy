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

package com.kineticfire.gradle.docker.workflow.operation

import com.kineticfire.gradle.docker.model.SaveCompression
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.SaveSpec
import com.kineticfire.gradle.docker.util.ImageReferenceBuilder
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.nio.file.Path

/**
 * Executor for saving Docker images to tar files
 *
 * This operation saves a built image to a tar file with optional compression.
 * Used in the success path after tests pass to persist images for distribution.
 */
class SaveOperationExecutor {

    private static final Logger LOGGER = Logging.getLogger(SaveOperationExecutor)

    /**
     * Execute save operation for an image
     *
     * @param saveSpec The save specification containing output file and compression settings
     * @param imageSpec The image specification to save
     * @param dockerService The Docker service for executing save operations
     * @throws GradleException if save operation fails or inputs are invalid
     */
    void execute(SaveSpec saveSpec, ImageSpec imageSpec, DockerService dockerService) {
        validateInputs(saveSpec, imageSpec, dockerService)

        def imageReference = buildImageReference(imageSpec)
        def outputFile = resolveOutputFile(saveSpec)
        def compression = resolveCompression(saveSpec)

        LOGGER.lifecycle("Saving image '{}' to '{}' with compression: {}",
            imageReference, outputFile, compression)

        try {
            executeSave(imageReference, outputFile, compression, dockerService)
            LOGGER.lifecycle("Successfully saved image to: {}", outputFile)
        } catch (Exception e) {
            throw new GradleException(
                "Failed to save image '${imageReference}' to '${outputFile}': ${e.message}", e)
        }
    }

    /**
     * Validate all required inputs
     */
    void validateInputs(SaveSpec saveSpec, ImageSpec imageSpec, DockerService dockerService) {
        if (saveSpec == null) {
            throw new GradleException("SaveSpec cannot be null for save operation")
        }
        if (imageSpec == null) {
            throw new GradleException("ImageSpec cannot be null for save operation")
        }
        if (dockerService == null) {
            throw new GradleException("DockerService cannot be null for save operation")
        }
    }

    /**
     * Build the image reference from ImageSpec
     * Uses the first configured tag
     */
    String buildImageReference(ImageSpec imageSpec) {
        def registryValue = imageSpec.registry.getOrElse("")
        def namespaceValue = imageSpec.namespace.getOrElse("")
        def repositoryValue = imageSpec.repository.getOrElse("")
        def imageNameValue = imageSpec.imageName.getOrElse("")
        def tagsValue = imageSpec.tags.getOrElse([])

        if (tagsValue.isEmpty()) {
            throw new GradleException(
                "Image '${imageSpec.name}' has no tags configured - cannot determine image reference for save")
        }

        def references = ImageReferenceBuilder.buildImageReferences(
            registryValue,
            namespaceValue,
            repositoryValue,
            imageNameValue,
            [tagsValue.first()]
        )

        if (references.isEmpty()) {
            throw new GradleException(
                "Cannot build image reference for '${imageSpec.name}' - " +
                "either repository or imageName must be configured")
        }

        return references.first()
    }

    /**
     * Resolve the output file path from SaveSpec
     */
    Path resolveOutputFile(SaveSpec saveSpec) {
        if (!saveSpec.outputFile.isPresent()) {
            throw new GradleException("Output file must be configured for save operation")
        }
        return saveSpec.outputFile.get().asFile.toPath()
    }

    /**
     * Resolve compression setting from SaveSpec
     * Defaults to NONE if not configured
     */
    SaveCompression resolveCompression(SaveSpec saveSpec) {
        return saveSpec.compression.getOrElse(SaveCompression.NONE)
    }

    /**
     * Execute the save operation via DockerService
     */
    void executeSave(String imageReference, Path outputFile, SaveCompression compression,
                     DockerService dockerService) {
        LOGGER.info("Executing save: {} -> {} (compression: {})",
            imageReference, outputFile, compression)

        def future = dockerService.saveImage(imageReference, outputFile, compression)
        future.get()
    }
}
