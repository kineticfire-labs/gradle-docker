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

import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.util.ImageReferenceBuilder
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Executor for applying additional tags to Docker images
 *
 * This operation applies additional tags to an already-built image based on
 * test results (e.g., adding 'stable' or 'production' tags after tests pass).
 */
class TagOperationExecutor {

    private static final Logger LOGGER = Logging.getLogger(TagOperationExecutor)

    /**
     * Apply additional tags to an image
     *
     * @param imageSpec The image specification containing the source image configuration
     * @param additionalTags The additional tags to apply
     * @param dockerService The Docker service for executing tag operations
     * @throws GradleException if tagging fails
     */
    void execute(ImageSpec imageSpec, List<String> additionalTags, DockerService dockerService) {
        if (imageSpec == null) {
            throw new GradleException("ImageSpec cannot be null for tag operation")
        }
        if (additionalTags == null || additionalTags.isEmpty()) {
            LOGGER.info("No additional tags to apply")
            return
        }
        if (dockerService == null) {
            throw new GradleException("DockerService cannot be null for tag operation")
        }

        def sourceImage = buildSourceImageReference(imageSpec)
        def targetTags = buildTargetImageReferences(imageSpec, additionalTags)

        LOGGER.lifecycle("Applying {} additional tag(s) to image: {}", additionalTags.size(), sourceImage)

        try {
            applyTags(sourceImage, targetTags, dockerService)
            LOGGER.lifecycle("Successfully applied tags: {}", additionalTags)
        } catch (Exception e) {
            throw new GradleException("Failed to apply tags to image '${sourceImage}': ${e.message}", e)
        }
    }

    /**
     * Build the source image reference from the ImageSpec
     * Uses the first tag from the image's configured tags
     */
    String buildSourceImageReference(ImageSpec imageSpec) {
        def registryValue = imageSpec.registry.getOrElse("")
        def namespaceValue = imageSpec.namespace.getOrElse("")
        def repositoryValue = imageSpec.repository.getOrElse("")
        def imageNameValue = imageSpec.imageName.getOrElse("")
        def tagsValue = imageSpec.tags.getOrElse([])

        if (tagsValue.isEmpty()) {
            throw new GradleException("Image '${imageSpec.name}' has no tags configured - cannot determine source image")
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
                "either repository or imageName must be configured"
            )
        }

        return references.first()
    }

    /**
     * Build target image references for the additional tags
     */
    List<String> buildTargetImageReferences(ImageSpec imageSpec, List<String> additionalTags) {
        def registryValue = imageSpec.registry.getOrElse("")
        def namespaceValue = imageSpec.namespace.getOrElse("")
        def repositoryValue = imageSpec.repository.getOrElse("")
        def imageNameValue = imageSpec.imageName.getOrElse("")

        return ImageReferenceBuilder.buildImageReferences(
            registryValue,
            namespaceValue,
            repositoryValue,
            imageNameValue,
            additionalTags
        )
    }

    /**
     * Apply tags to the source image using the Docker service
     */
    void applyTags(String sourceImage, List<String> targetTags, DockerService dockerService) {
        LOGGER.info("Tagging {} with {} new tag(s)", sourceImage, targetTags.size())

        for (String targetTag : targetTags) {
            LOGGER.debug("Applying tag: {} -> {}", sourceImage, targetTag)
        }

        def future = dockerService.tagImage(sourceImage, targetTags)
        future.get()
    }
}
