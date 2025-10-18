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

import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.AuthSpec
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.TaskAction

/**
 * Task for tagging Docker images
 *
 * Configuration Cache Compatible: ✅
 * - Uses flattened @Input properties only
 * - No nested object serialization
 * - No Project reference capture
 */
abstract class DockerTagTask extends DefaultTask {

    DockerTagTask() {
        // Set up default values for Provider API compatibility
        registry.convention("")
        namespace.convention("")
        imageName.convention("")
        repository.convention("")
        version.convention("")
        tags.convention([])
        sourceRef.convention("")
        sourceRefRegistry.convention("")
        sourceRefNamespace.convention("")
        sourceRefImageName.convention("")
        sourceRefRepository.convention("")
        sourceRefTag.convention("")
        pullIfMissing.convention(false)
        effectiveSourceRef.convention("")
    }

    @Internal
    abstract Property<DockerService> getDockerService()

    // SourceRef Mode Properties (for existing images)
    @Input
    @Optional
    abstract Property<String> getSourceRef()

    @Input
    @Optional
    abstract Property<String> getSourceRefRegistry()

    @Input
    @Optional
    abstract Property<String> getSourceRefNamespace()

    @Input
    @Optional
    abstract Property<String> getSourceRefImageName()

    @Input
    @Optional
    abstract Property<String> getSourceRefRepository()

    @Input
    @Optional
    abstract Property<String> getSourceRefTag()

    // Docker Image Nomenclature Properties (for building new images)
    @Input
    @Optional
    abstract Property<String> getRegistry()

    @Input
    @Optional
    abstract Property<String> getNamespace()

    @Input
    @Optional
    abstract Property<String> getImageName()

    @Input
    @Optional
    abstract Property<String> getRepository()

    @Input
    @Optional
    abstract Property<String> getVersion()

    @Input
    abstract ListProperty<String> getTags()

    // PullIfMissing properties
    @Input
    @Optional
    abstract Property<Boolean> getPullIfMissing()

    @Input
    @Optional
    abstract Property<String> getEffectiveSourceRef()

    @Nested
    @Optional
    abstract Property<AuthSpec> getPullAuth()

    @TaskAction
    void tagImage() {
        // Pull source image if needed
        pullSourceRefIfNeeded()

        def service = dockerService.get()
        if (!service) {
            throw new IllegalStateException("dockerService must be provided")
        }

        // Use flattened properties instead of imageSpec
        def sourceRefValue = sourceRef.getOrElse("")
        def tagsValue = tags.getOrElse([])
        def isSourceRefMode = isInSourceRefMode()

        if (isSourceRefMode) {
            // SourceRef Mode: Tag existing image with new tags
            if (!tagsValue.isEmpty()) {
                def effectiveSourceRefValue = getEffectiveSourceRefValue()
                def future = service.tagImage(effectiveSourceRefValue, tagsValue)
                future.get()
            } else {
                // If no tags provided in sourceRef mode, it's a no-op
                logger.info("No additional tags specified for sourceRef mode, skipping tag operation")
            }
        } else {
            // Build Mode: Validate tags are provided
            if (tagsValue.isEmpty()) {
                throw new IllegalStateException("At least one tag must be specified")
            }
            // Build Mode: Tag the built image with additional tags
            def imageReferences = buildImageReferences()
            if (imageReferences.size() < 1) {
                throw new IllegalStateException("At least one tag must be specified")
            }

            if (imageReferences.size() == 1) {
                // Single tag: no-op since image already has this tag from build
                logger.info("Image already has tag from build, no additional tagging needed: ${imageReferences[0]}")
                return
            }

            // Multiple tags: use first tag as source, apply remaining as targets
            def sourceImageRef = imageReferences[0]
            def targetRefs = imageReferences.drop(1)
            def future = service.tagImage(sourceImageRef, targetRefs)
            future.get()
        }
    }

    /**
     * Check if task is in sourceRef mode
     */
    private boolean isInSourceRefMode() {
        // Check direct sourceRef
        if (sourceRef.isPresent() && !sourceRef.get().isEmpty()) {
            return true
        }
        // Check sourceRef components
        def hasRepository = sourceRefRepository.isPresent() && !sourceRefRepository.get().isEmpty()
        def hasImageName = sourceRefImageName.isPresent() && !sourceRefImageName.get().isEmpty()
        return hasRepository || hasImageName
    }

    /**
     * Get the effective source reference for sourceRef mode
     */
    private String getEffectiveSourceRefValue() {
        // Priority 1: Use pre-computed effectiveSourceRef if provided
        def precomputedValue = effectiveSourceRef.getOrElse("")
        if (!precomputedValue.isEmpty()) {
            return precomputedValue
        }

        // Priority 2: Direct sourceRef
        def sourceRefValue = sourceRef.getOrElse("")
        if (!sourceRefValue.isEmpty()) {
            return sourceRefValue
        }

        // Priority 3: SourceRef components
        def sourceRefRegistryValue = sourceRefRegistry.getOrElse("")
        def sourceRefNamespaceValue = sourceRefNamespace.getOrElse("")
        def sourceRefRepositoryValue = sourceRefRepository.getOrElse("")
        def sourceRefImageNameValue = sourceRefImageName.getOrElse("")
        def sourceRefTagValue = sourceRefTag.getOrElse("latest")

        // Repository approach
        if (!sourceRefRepositoryValue.isEmpty()) {
            def baseRef = sourceRefRegistryValue.isEmpty() ?
                sourceRefRepositoryValue :
                "${sourceRefRegistryValue}/${sourceRefRepositoryValue}"
            return "${baseRef}:${sourceRefTagValue}"
        }

        // Namespace + imageName approach
        if (!sourceRefImageNameValue.isEmpty()) {
            def reference = ""
            if (!sourceRefRegistryValue.isEmpty()) {
                reference += sourceRefRegistryValue + "/"
            }
            if (!sourceRefNamespaceValue.isEmpty()) {
                reference += sourceRefNamespaceValue + "/"
            }
            reference += sourceRefImageNameValue
            reference += ":" + sourceRefTagValue
            return reference
        }

        return ""
    }

    /**
     * Build all image references (dual-mode properties: SourceRef vs Build Mode)
     */
    List<String> buildImageReferences() {
        def references = []

        def sourceRefValue = sourceRef.getOrElse("")
        def tagsValue = tags.getOrElse([])

        if (!sourceRefValue.isEmpty()) {
            // SourceRef Mode: Use sourceRef as source, apply tags as targets
            references.add(sourceRefValue)
            tagsValue.each { tag ->
                references.add(tag)
            }
        } else {
            // Build Mode: Use nomenclature to build image references
            if (tagsValue.isEmpty()) {
                throw new IllegalStateException("At least one tag must be specified")
            }

            def registryValue = registry.getOrElse("")
            def namespaceValue = namespace.getOrElse("")
            def repositoryValue = repository.getOrElse("")
            def imageNameValue = imageName.getOrElse("")

            if (repositoryValue.isEmpty() && imageNameValue.isEmpty()) {
                throw new IllegalStateException("Either repository OR imageName must be specified when not using sourceRef")
            }

            if (!repositoryValue.isEmpty()) {
                // Using repository format
                def baseRef = registryValue.isEmpty() ? repositoryValue : "${registryValue}/${repositoryValue}"
                tagsValue.each { tag ->
                    references.add("${baseRef}:${tag}")
                }
            } else if (!imageNameValue.isEmpty()) {
                // Using namespace + imageName format
                def baseRef = ""
                if (!registryValue.isEmpty()) {
                    baseRef += "${registryValue}/"
                }
                if (!namespaceValue.isEmpty()) {
                    baseRef += "${namespaceValue}/"
                }
                baseRef += imageNameValue

                tagsValue.each { tag ->
                    references.add("${baseRef}:${tag}")
                }
            }
        }

        return references
    }

    private void pullSourceRefIfNeeded() {
        if (pullIfMissing.getOrElse(false)) {
            def sourceRefValue = effectiveSourceRef.getOrElse("")
            if (sourceRefValue && !sourceRefValue.isEmpty()) {
                def authConfig = pullAuth.isPresent() ?
                    pullAuth.get().toAuthConfig() : null

                def service = dockerService.get()
                if (!service.imageExists(sourceRefValue).get()) {
                    service.pullImage(sourceRefValue, authConfig).get()
                }
            }
        }
    }
}
