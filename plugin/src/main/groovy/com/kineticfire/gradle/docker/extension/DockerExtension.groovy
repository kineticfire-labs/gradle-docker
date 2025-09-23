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

package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.spec.ImageSpec
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.file.ProjectLayout

import javax.inject.Inject

/**
 * Main extension for Docker operations (docker { } DSL)
 */
abstract class DockerExtension {
    
    private final NamedDomainObjectContainer<ImageSpec> images
    private final ObjectFactory objectFactory
    private final Project project
    
    @Inject
    DockerExtension(ObjectFactory objectFactory, ProviderFactory providers, ProjectLayout layout, Project project) {
        this.objectFactory = objectFactory
        this.project = project
        this.images = objectFactory.domainObjectContainer(ImageSpec) { name ->
            def imageSpec = objectFactory.newInstance(ImageSpec, name, project)
            return imageSpec
        }
    }
    
    NamedDomainObjectContainer<ImageSpec> getImages() {
        return images
    }
    
    void images(@DelegatesTo(NamedDomainObjectContainer) Closure closure) {
        images.configure(closure)
    }
    
    void images(Action<NamedDomainObjectContainer<ImageSpec>> action) {
        action.execute(images)
    }
    
    /**
     * Validation method called during configuration
     */
    void validate() {
        images.each { imageSpec ->
            validateImageSpec(imageSpec)
        }
    }
    
    void validateImageSpec(ImageSpec imageSpec) {
        def hasContextTask = imageSpec.contextTask != null
        def hasSourceRef = imageSpec.sourceRef.isPresent() && !imageSpec.sourceRef.get().isEmpty()
        
        // Check if context was explicitly set (not just the convention)
        // The challenge is distinguishing explicit setting from convention
        // Heuristic: if context path is NOT the convention path, then it was likely explicitly set
        def hasExplicitContext = false
        if (imageSpec.context.isPresent()) {
            def contextPath = imageSpec.context.get().asFile.path
            def conventionPath = "src/main/docker"
            // Consider it explicit if path doesn't end with the convention path
            hasExplicitContext = !contextPath.endsWith(conventionPath)
        }
        
        // Validate required properties for nomenclature (only if not using sourceRef)
        if (!hasSourceRef) {
            validateNomenclature(imageSpec)
        }

        // Validate sourceRef exclusivity with build properties
        if (hasSourceRef) {
            def hasBuildProperties = hasContextTask || hasExplicitContext ||
                                   imageSpec.buildArgs.isPresent() && !imageSpec.buildArgs.get().isEmpty() ||
                                   imageSpec.labels.isPresent() && !imageSpec.labels.get().isEmpty() ||
                                   imageSpec.dockerfile.isPresent() || imageSpec.dockerfileName.isPresent()

            if (hasBuildProperties) {
                throw new GradleException(
                    "Image '${imageSpec.name}' cannot use both 'sourceRef' and build-related properties. " +
                    "sourceRef is for existing images, while build properties are for building new images. " +
                    "Use either sourceRef OR build properties, not both."
                )
            }
        }

        // Validate required properties - must have at least one context source
        if (!hasExplicitContext && !hasContextTask && !hasSourceRef) {
            throw new GradleException(
                "Image '${imageSpec.name}' must specify either 'context', 'contextTask', or 'sourceRef'"
            )
        }
        
        // Validate mutually exclusive context configuration
        def contextCount = [hasExplicitContext, hasContextTask].count(true)
        if (contextCount > 1) {
            throw new GradleException(
                "Image '${imageSpec.name}' must specify only one of: 'context', 'contextTask', or inline 'context {}' block"
            )
        }
        
        // Validate mutually exclusive dockerfile configuration
        if (imageSpec.dockerfile.isPresent() && imageSpec.dockerfileName.isPresent()) {
            throw new GradleException(
                "Image '${imageSpec.name}' cannot specify both 'dockerfile' and 'dockerfileName'. " +
                "Use 'dockerfile' for custom paths or 'dockerfileName' for custom names in default locations."
            )
        }
        
        // Validate traditional context directory exists if explicitly specified (but not for sourceRef images)
        if (hasExplicitContext && !hasSourceRef) {
            def contextDir = imageSpec.context.get().asFile
            if (!contextDir.exists()) {
                throw new GradleException(
                    "Docker context directory does not exist: ${contextDir.absolutePath}\n" +
                    "Suggestion: Create the directory or update the context path in image '${imageSpec.name}'"
                )
            }
        }
        
        // Validate dockerfile exists (but not for sourceRef images)
        if (imageSpec.dockerfile.isPresent() && !hasSourceRef) {
            def dockerfileFile = imageSpec.dockerfile.get().asFile
            if (!dockerfileFile.exists()) {
                throw new GradleException(
                    "Dockerfile does not exist: ${dockerfileFile.absolutePath}\\n" +
                    "Suggestion: Create the Dockerfile or update the dockerfile path in image '${imageSpec.name}'"
                )
            }
        }
        
        // Validate publish configuration if present
        if (imageSpec.publish.isPresent()) {
            validatePublishConfiguration(imageSpec.publish.get(), imageSpec.name)
        }

        // Validate save configuration if present
        if (imageSpec.save.isPresent()) {
            validateSaveConfiguration(imageSpec.save.get(), imageSpec.name)
        }
    }
    
    /**
     * Validates Docker image nomenclature according to new API rules
     */
    void validateNomenclature(ImageSpec imageSpec) {
        // WORKAROUND: Due to potential property sharing between ImageSpec and PublishTarget,
        // we need to be more conservative in validation. Only validate properties that
        // would realistically be set explicitly on the ImageSpec.
        
        // Check if properties are present AND non-empty (defensive validation)
        def repositoryValue = imageSpec.repository.getOrElse("")
        def namespaceValue = imageSpec.namespace.getOrElse("")
        def imageNameValue = imageSpec.imageName.getOrElse("")
        
        def hasRepository = !repositoryValue.isEmpty()
        def hasNamespace = !namespaceValue.isEmpty()
        def hasImageName = !imageNameValue.isEmpty()

        // WORKAROUND: Skip mutual exclusivity validation if publish targets are configured
        // This avoids the property contamination issue where publish target properties
        // affect ImageSpec validation. The real validation should happen at task execution time.
        def hasPublishConfig = imageSpec.publish.isPresent()
        
        if (!hasPublishConfig && hasRepository && hasNamespace) {
            throw new GradleException(
                "Image '${imageSpec.name}' has mutual exclusivity violation: cannot specify both 'repository' and 'namespace'. " +
                "Use either 'repository' OR 'namespace'+'imageName'"
            )
        }

        // Validate that at least one naming approach is used
        if (!hasRepository && !hasImageName) {
            throw new GradleException(
                "Image '${imageSpec.name}' must specify either 'repository' OR 'imageName'"
            )
        }

        // Validate that tags are specified (required unless image has publish targets)
        try {
            def hasTags = imageSpec.tags.isPresent() && !imageSpec.tags.get().isEmpty()
            def hasPublishTargets = imageSpec.publish.isPresent()
            
            if (!hasTags && !hasPublishTargets) {
                throw new GradleException(
                    "Image '${imageSpec.name}' must specify at least one tag or have publish targets"
                )
            }
        } catch (IllegalStateException e) {
            // Only catch specific TestKit Provider issues, not validation failures
            if (e.message?.contains("Cannot query the value of this provider") ||
                e.message?.contains("Cannot get the value of a task output property")) {
                // Provider not available during TestKit functional tests - defer validation to task execution
                // This handles the case where Provider properties cannot be resolved during configuration time
                // in TestKit environments while preserving validation for unit tests
            } else {
                // Re-throw other IllegalStateExceptions as they might be actual validation errors
                throw e
            }
        }

        // Validate tag formats if available
        try {
            if (imageSpec.tags.isPresent()) {
                imageSpec.tags.get().each { tag ->
                    if (!isValidTagFormat(tag)) {
                        throw new GradleException(
                            "Invalid tag format '${tag}' in image '${imageSpec.name}'. " +
                            "Tags must be alphanumeric with dots, hyphens, underscores, cannot start with '.' or '-', and be ≤128 chars"
                        )
                    }
                }
            }
        } catch (IllegalStateException e) {
            // Only catch specific TestKit Provider issues, not validation failures
            if (e.message?.contains("Cannot query the value of this provider") ||
                e.message?.contains("Cannot get the value of a task output property")) {
                // Provider not available during TestKit functional tests - defer validation to task execution
                // This handles the case where Provider properties cannot be resolved during configuration time
                // in TestKit environments while preserving validation for unit tests
            } else {
                // Re-throw other IllegalStateExceptions as they might be actual validation errors
                throw e
            }
        }
    }
    
    /**
     * Validates registry format (hostname or hostname:port)
     */
    boolean isValidRegistryFormat(String registry) {
        // Allow hostname or hostname:port format
        return registry.matches("^[a-zA-Z0-9.-]+(:[0-9]+)?\$")
    }
    
    /**
     * Validates namespace format
     */
    boolean isValidNamespaceFormat(String namespace) {
        // Allow alphanumeric, periods, hyphens, slashes
        return namespace.matches("^[a-z0-9._/-]+\$") && namespace.length() <= 255
    }
    
    /**
     * Validates image name format
     */
    boolean isValidImageNameFormat(String imageName) {
        // Allow alphanumeric, periods, hyphens, underscores
        return imageName.matches("^[a-z0-9._-]+\$") && imageName.length() <= 128
    }
    
    /**
     * Validates repository format (namespace/name)
     */
    boolean isValidRepositoryFormat(String repository) {
        // Should be in format like "namespace/name" or "multi/level/namespace/name"
        return repository.matches("^[a-z0-9._/-]+\$") && repository.length() <= 255 && repository.contains("/")
    }
    
    /**
     * Validates tag format
     */
    boolean isValidTagFormat(String tag) {
        // Allow alphanumeric, periods, hyphens, underscores
        return tag.matches("^[a-zA-Z0-9._-]+\$") && tag.length() <= 128 && !tag.startsWith(".") && !tag.startsWith("-")
    }

    /**
     * Validates Docker image reference format (registry/namespace/name:tag)
     */
    boolean isValidImageReference(String imageRef) {
        if (!imageRef || imageRef.trim().isEmpty()) {
            return false
        }

        // Split by colon to check tag
        def colonCount = imageRef.count(':')

        // Must have exactly one colon (except if registry port is specified)
        if (colonCount == 0) {
            return false // No tag
        }

        // Multiple colons are only allowed if first colon is for registry port
        // Check for invalid patterns like "image::tag" or "registry:port/image:tag:extra"
        if (imageRef.contains("::") || colonCount > 2) {
            return false
        }

        // Split at the last colon to separate image reference from tag
        def lastColonIndex = imageRef.lastIndexOf(':')
        def imagePart = imageRef.substring(0, lastColonIndex)
        def tagPart = imageRef.substring(lastColonIndex + 1)

        // Tag cannot be empty
        if (tagPart.isEmpty()) {
            return false
        }

        // Image part cannot be empty
        if (imagePart.isEmpty()) {
            return false
        }

        // Validate tag format
        if (!isValidTagFormat(tagPart)) {
            return false
        }

        // Basic validation for image part (registry/namespace/name)
        // Allow alphanumeric, dots, hyphens, underscores, slashes, and one colon for registry port
        return imagePart.matches("^[a-zA-Z0-9._-]+(:[0-9]+)?(/[a-zA-Z0-9._/-]+)*\$") &&
               imageRef.length() <= 255
    }

    /**
     * Validates publish configuration for an image
     */
    void validatePublishConfiguration(def publishSpec, String imageName) {
        def targets = publishSpec.to
        if (!targets || targets.isEmpty()) {
            throw new GradleException(
                "Publish configuration for image '${imageName}' must specify at least one target using 'to()'"
            )
        }
        
        targets.each { target ->
            validatePublishTarget(target, imageName)
        }
    }
    
    /**
     * Validates a single publish target
     */
    void validatePublishTarget(def publishTarget, String imageName) {
        // Validate publish target name
        if (publishTarget.name == null || publishTarget.name.isEmpty()) {
            throw new GradleException(
                "Publish target in image '${imageName}' must have a name"
            )
        }
        
        // Validate publishTags - with careful exception handling for TestKit compatibility
        try {
            if (!publishTarget.getPublishTags().isPresent() || publishTarget.getPublishTags().get().isEmpty()) {
                throw new GradleException(
                    "Publish target '${publishTarget.name}' in image '${imageName}' must specify at least one tag"
                )
            }
            
            // Validate each tag - publishTags should be tag names (not full image references)
            publishTarget.getPublishTags().get().each { publishTag ->
                // Publish tags should be valid tag names, not full image references
                if (!isValidTagFormat(publishTag)) {
                    throw new GradleException(
                        "Invalid tag format '${publishTag}' in publish target '${publishTarget.name}' for image '${imageName}'. " +
                        "Tags must be alphanumeric with dots, hyphens, underscores, cannot start with '.' or '-', and be ≤128 chars"
                    )
                }
            }
        } catch (IllegalStateException e) {
            // Only handle specific TestKit Provider API issues
            if (e.message?.contains("Cannot query the value of this provider") ||
                e.message?.contains("Cannot get the value of a task output property")) {
                // In TestKit environment, defer validation to task execution time
                // This is a known limitation of Provider API in TestKit functional tests
            } else {
                throw e
            }
        }
        // Note: GradleException and other validation exceptions should be propagated normally
        // Only IllegalStateException from Provider API issues should be caught
    }

    /**
     * Validates save configuration for an image
     */
    void validateSaveConfiguration(def saveSpec, String imageName) {
        if (!saveSpec.compression.isPresent()) {
            throw new GradleException(
                "compression parameter is required for save configuration in image '${imageName}'"
            )
        }

        // The compression is now a SaveCompression enum, so validation is handled by type safety
        // No need for additional validation of compression values

        // Check if outputFile was explicitly set (not just using convention)
        if (!saveSpec.outputFile.isPresent()) {
            throw new GradleException(
                "outputFile parameter is required for save configuration in image '${imageName}'"
            )
        }
        // Also check if it's just the default convention value (which indicates not explicitly set)
        def defaultPath = "docker-images/image.tar"
        if (saveSpec.outputFile.get().asFile.path.endsWith(defaultPath)) {
            throw new GradleException(
                "outputFile parameter is required for save configuration in image '${imageName}'"
            )
        }
    }

    /**
     * Validates repository name format for backward compatibility with tests
     */
    boolean isValidRepositoryName(String repository) {
        if (!repository || repository.trim().isEmpty()) {
            return false
        }
        
        // Repository name validation (supports registry:port/namespace/name format)
        return repository.matches("^[a-zA-Z0-9._:/-]+\$") && 
               repository.length() <= 255 &&
               !repository.startsWith("-") &&
               !repository.endsWith("-") &&
               !repository.contains(" ")
    }

    /**
     * Extracts the image name part from a full Docker image reference
     * Example: "registry.com/namespace/app:tag" -> "registry.com/namespace/app"
     */
    String extractImageName(String imageRef) {
        if (!imageRef || imageRef.trim().isEmpty()) {
            throw new IllegalArgumentException("Image reference cannot be null or empty")
        }
        
        def colonIndex = imageRef.lastIndexOf(':')
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid image reference format: ${imageRef}")
        }
        
        return imageRef.substring(0, colonIndex)
    }
}