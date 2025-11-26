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

import com.kineticfire.gradle.docker.model.AuthConfig
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.PublishSpec
import com.kineticfire.gradle.docker.spec.PublishTarget
import com.kineticfire.gradle.docker.util.ImageReferenceBuilder
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Executor for publishing Docker images to registries
 *
 * This operation publishes a built image to one or more Docker registries.
 * Used in the success path after tests pass to distribute images.
 */
class PublishOperationExecutor {

    private static final Logger LOGGER = Logging.getLogger(PublishOperationExecutor)

    /**
     * Execute publish operation for an image
     *
     * @param publishSpec The publish specification containing target registries and tags
     * @param imageSpec The image specification to publish
     * @param dockerService The Docker service for executing publish operations
     * @throws GradleException if publish operation fails or inputs are invalid
     */
    void execute(PublishSpec publishSpec, ImageSpec imageSpec, DockerService dockerService) {
        validateInputs(publishSpec, imageSpec, dockerService)

        def targets = publishSpec.to
        if (targets.isEmpty()) {
            LOGGER.info("No publish targets configured - skipping publish operation")
            return
        }

        def sourceImageRef = buildSourceImageReference(imageSpec)
        LOGGER.lifecycle("Publishing image '{}' to {} target(s)", sourceImageRef, targets.size())

        try {
            for (PublishTarget target : targets) {
                publishToTarget(sourceImageRef, imageSpec, target, publishSpec, dockerService)
            }
            LOGGER.lifecycle("Successfully published image to all targets")
        } catch (Exception e) {
            throw new GradleException(
                "Failed to publish image '${sourceImageRef}': ${e.message}", e)
        }
    }

    /**
     * Validate all required inputs
     */
    void validateInputs(PublishSpec publishSpec, ImageSpec imageSpec, DockerService dockerService) {
        if (publishSpec == null) {
            throw new GradleException("PublishSpec cannot be null for publish operation")
        }
        if (imageSpec == null) {
            throw new GradleException("ImageSpec cannot be null for publish operation")
        }
        if (dockerService == null) {
            throw new GradleException("DockerService cannot be null for publish operation")
        }
    }

    /**
     * Build the source image reference from ImageSpec
     * Uses the first configured tag
     */
    String buildSourceImageReference(ImageSpec imageSpec) {
        def registryValue = imageSpec.registry.getOrElse("")
        def namespaceValue = imageSpec.namespace.getOrElse("")
        def repositoryValue = imageSpec.repository.getOrElse("")
        def imageNameValue = imageSpec.imageName.getOrElse("")
        def tagsValue = imageSpec.tags.getOrElse([])

        if (tagsValue.isEmpty()) {
            throw new GradleException(
                "Image '${imageSpec.name}' has no tags configured - cannot determine image reference for publish")
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
     * Publish to a specific target
     */
    void publishToTarget(String sourceImageRef, ImageSpec imageSpec, PublishTarget target,
                         PublishSpec publishSpec, DockerService dockerService) {
        def targetName = target.name
        LOGGER.info("Publishing to target: {}", targetName)

        def publishTags = resolvePublishTags(target, publishSpec, imageSpec)
        if (publishTags.isEmpty()) {
            LOGGER.warn("No publish tags configured for target '{}' - using source tag", targetName)
            publishTags = [imageSpec.tags.get().first()]
        }

        def targetRefs = buildTargetReferences(target, imageSpec, publishTags)
        def auth = resolveAuth(target)

        for (String targetRef : targetRefs) {
            publishImage(sourceImageRef, targetRef, auth, dockerService)
        }
    }

    /**
     * Resolve the tags to use for publishing
     * Priority: target.publishTags > publishSpec.publishTags > source image first tag
     */
    List<String> resolvePublishTags(PublishTarget target, PublishSpec publishSpec, ImageSpec imageSpec) {
        def targetTags = target.publishTags.getOrElse([])
        if (!targetTags.isEmpty()) {
            return targetTags
        }

        def specTags = publishSpec.publishTags.getOrElse([])
        if (!specTags.isEmpty()) {
            return specTags
        }

        return []
    }

    /**
     * Build target image references for the publish target
     */
    List<String> buildTargetReferences(PublishTarget target, ImageSpec imageSpec, List<String> tags) {
        def targetRegistry = target.registry.getOrElse("")
        def targetNamespace = target.namespace.getOrElse("")
        def targetRepository = target.repository.getOrElse("")
        def targetImageName = target.imageName.getOrElse("")

        // If target has no image name/repository configured, inherit from source
        if (targetRepository.isEmpty() && targetImageName.isEmpty()) {
            targetRepository = imageSpec.repository.getOrElse("")
            targetImageName = imageSpec.imageName.getOrElse("")
        }

        // If target has no namespace and source has one, inherit it
        if (targetNamespace.isEmpty() && targetImageName.isEmpty()) {
            targetNamespace = imageSpec.namespace.getOrElse("")
        }

        return ImageReferenceBuilder.buildImageReferences(
            targetRegistry,
            targetNamespace,
            targetRepository,
            targetImageName,
            tags
        )
    }

    /**
     * Resolve authentication configuration for target
     */
    AuthConfig resolveAuth(PublishTarget target) {
        if (!target.auth.isPresent()) {
            return null
        }

        def authSpec = target.auth.get()
        return authSpec.toAuthConfig()
    }

    /**
     * Publish a single image to a target reference
     */
    void publishImage(String sourceImageRef, String targetRef, AuthConfig auth,
                      DockerService dockerService) {
        LOGGER.info("Publishing {} -> {}", sourceImageRef, targetRef)

        // First tag the source image with the target reference
        tagForPublish(sourceImageRef, targetRef, dockerService)

        // Then push the tagged image
        pushImage(targetRef, auth, dockerService)

        LOGGER.info("Successfully published: {}", targetRef)
    }

    /**
     * Tag source image with target reference for publishing
     */
    void tagForPublish(String sourceImageRef, String targetRef, DockerService dockerService) {
        if (sourceImageRef == targetRef) {
            LOGGER.debug("Source and target are same - skipping tag step")
            return
        }

        LOGGER.debug("Tagging {} as {}", sourceImageRef, targetRef)
        def future = dockerService.tagImage(sourceImageRef, [targetRef])
        future.get()
    }

    /**
     * Push image to registry
     */
    void pushImage(String imageRef, AuthConfig auth, DockerService dockerService) {
        LOGGER.debug("Pushing {} to registry", imageRef)
        def future = dockerService.pushImage(imageRef, auth)
        future.get()
    }
}
