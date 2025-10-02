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

package com.kineticfire.gradle.docker.model

import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.PublishTarget
import com.kineticfire.gradle.docker.task.DockerPublishTask

/**
 * Represents resolved image properties for inheritance calculations
 */
class EffectiveImageProperties {
    final String registry
    final String namespace
    final String imageName
    final String repository
    final List<String> tags

    EffectiveImageProperties(String registry, String namespace, String imageName,
                           String repository, List<String> tags) {
        this.registry = registry ?: ""
        this.namespace = namespace ?: ""
        this.imageName = imageName ?: ""
        this.repository = repository ?: ""
        this.tags = tags ?: []
    }

    /**
     * Factory method to resolve properties from ImageSpec
     */
    static EffectiveImageProperties fromImageSpec(ImageSpec imageSpec) {
        // Step 1: Check for direct sourceRef
        if (imageSpec.sourceRef.isPresent() && !imageSpec.sourceRef.get().isEmpty()) {
            return parseFromDirectSourceRef(imageSpec.sourceRef.get())
        }

        // Step 2: Check for sourceRef components
        if (hasSourceRefComponents(imageSpec)) {
            return buildFromSourceRefComponents(imageSpec)
        }

        // Step 3: Use build mode properties
        return buildFromBuildMode(imageSpec)
    }

    /**
     * Factory method to resolve properties from task properties (fallback)
     */
    static EffectiveImageProperties fromTaskProperties(DockerPublishTask task) {
        // Handle direct sourceRef
        def sourceRefValue = task.sourceRef.getOrElse("")
        if (!sourceRefValue.isEmpty()) {
            return parseFromDirectSourceRef(sourceRefValue)
        }

        // Handle sourceRef components
        if (hasSourceRefComponents(task)) {
            return buildFromSourceRefComponents(task)
        }

        // Use build mode properties
        return new EffectiveImageProperties(
            task.registry.getOrElse(""),
            task.namespace.getOrElse(""),
            task.imageName.getOrElse(""),
            task.repository.getOrElse(""),
            task.tags.getOrElse([])
        )
    }

    /**
     * Apply target overrides with inheritance
     */
    EffectiveImageProperties applyTargetOverrides(PublishTarget target) {
        // Special handling for empty publishTags - inherit source tags
        def effectivePublishTags = target.publishTags.getOrElse([])
        if (effectivePublishTags.isEmpty() && !this.tags.isEmpty()) {
            effectivePublishTags = this.tags
        }

        return new EffectiveImageProperties(
            target.registry.getOrElse("") ?: this.registry,
            target.namespace.getOrElse("") ?: this.namespace,
            target.imageName.getOrElse("") ?: this.imageName,
            target.repository.getOrElse("") ?: this.repository,
            effectivePublishTags
        )
    }

    private static EffectiveImageProperties parseFromDirectSourceRef(String sourceRef) {
        def parts = ImageRefParts.parse(sourceRef)
        return new EffectiveImageProperties(
            parts.registry,
            parts.namespace,
            parts.repository,  // imageName parameter
            "",                // repository parameter (empty - using namespace/imageName mode)
            [parts.tag]
        )
    }

    private static EffectiveImageProperties buildFromSourceRefComponents(imageSpec) {
        try {
            // Use ImageSpec.getEffectiveSourceRef() and parse result
            def effectiveRef = imageSpec.getEffectiveSourceRef()
            return parseFromDirectSourceRef(effectiveRef)
        } catch (Exception e) {
            // If getEffectiveSourceRef fails, fall back to build mode
            // This can happen if hasSourceRefComponents returns true due to conventions
            // but the actual sourceRef components are insufficient
            return buildFromBuildMode(imageSpec)
        }
    }

    private static EffectiveImageProperties buildFromSourceRefComponents(DockerPublishTask task) {
        // Assemble from task sourceRef components using same logic as ImageSpec
        def registry = task.sourceRefRegistry.getOrElse("")
        def namespace = task.sourceRefNamespace.getOrElse("")
        def repository = task.sourceRefRepository.getOrElse("")
        def imageName = task.sourceRefImageName.getOrElse("")
        def tag = task.sourceRefTag.getOrElse("")

        // If tag is empty, default to "latest"
        if (tag.isEmpty()) {
            tag = "latest"
        }

        // Repository approach takes precedence (mirrors build mode logic)
        if (!repository.isEmpty()) {
            def baseRef = registry.isEmpty() ? repository : "${registry}/${repository}"
            def fullRef = "${baseRef}:${tag}"
            return parseFromDirectSourceRef(fullRef)
        }

        // Fall back to namespace + imageName approach
        if (imageName.isEmpty()) {
            throw new IllegalArgumentException("Either sourceRef, sourceRefRepository, or sourceRefImageName must be specified")
        }

        def reference = ""
        if (!registry.isEmpty()) {
            reference += registry + "/"
        }
        if (!namespace.isEmpty()) {
            reference += namespace + "/"
        }
        reference += imageName
        reference += ":" + tag

        return parseFromDirectSourceRef(reference)
    }

    private static EffectiveImageProperties buildFromBuildMode(ImageSpec imageSpec) {
        return new EffectiveImageProperties(
            imageSpec.registry.getOrElse(""),
            imageSpec.namespace.getOrElse(""),
            imageSpec.imageName.getOrElse(""),
            imageSpec.repository.getOrElse(""),
            imageSpec.tags.getOrElse([])
        )
    }

    private static boolean hasSourceRefComponents(imageSpec) {
        // Only consider it to have sourceRef components if at least one meaningful component is present
        def hasRepository = !imageSpec.sourceRefRepository.getOrElse("").isEmpty()
        def hasImageName = !imageSpec.sourceRefImageName.getOrElse("").isEmpty()

        // Repository mode or namespace+imageName mode both require essential components
        return hasRepository || hasImageName
    }

    private static boolean hasSourceRefComponents(DockerPublishTask task) {
        // Only consider it to have sourceRef components if at least one meaningful component is present
        def hasRepository = !task.sourceRefRepository.getOrElse("").isEmpty()
        def hasImageName = !task.sourceRefImageName.getOrElse("").isEmpty()

        // Repository mode or namespace+imageName mode both require essential components
        return hasRepository || hasImageName
    }

    @Override
    String toString() {
        return "EffectiveImageProperties{registry='${registry}', namespace='${namespace}', imageName='${imageName}', repository='${repository}', tags=${tags}}"
    }
}