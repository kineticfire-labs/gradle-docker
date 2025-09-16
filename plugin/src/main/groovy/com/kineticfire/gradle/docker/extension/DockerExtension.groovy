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

import javax.inject.Inject

/**
 * Main extension for Docker operations (docker { } DSL)
 */
abstract class DockerExtension {
    
    private final NamedDomainObjectContainer<ImageSpec> images
    private final ObjectFactory objectFactory
    private final Project project
    
    @Inject
    DockerExtension(ObjectFactory objectFactory, Project project) {
        this.objectFactory = objectFactory
        this.project = project
        this.images = objectFactory.domainObjectContainer(ImageSpec) { name ->
            def imageSpec = objectFactory.newInstance(ImageSpec, name, project)
            // Set default context if not specified
            imageSpec.context.convention(
                project.layout.projectDirectory.dir("src/main/docker")
            )
            
            // Set empty convention to prevent implicit defaults  
            imageSpec.tags.convention([])
            
            // Note: dockerfile defaults are now handled in GradleDockerPlugin.configureDockerBuildTask()
            // to properly support both context and contextTask scenarios
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
        def hasSourceRef = imageSpec.sourceRef.present
        
        // Check if context was explicitly set (not just the convention)
        def hasExplicitContext = false
        if (imageSpec.context.isPresent()) {
            def contextFile = imageSpec.context.get().asFile
            def conventionFile = project.layout.projectDirectory.dir("src/main/docker").asFile
            hasExplicitContext = !contextFile.equals(conventionFile)
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
        if (imageSpec.dockerfile.present && imageSpec.dockerfileName.present) {
            throw new GradleException(
                "Image '${imageSpec.name}' cannot specify both 'dockerfile' and 'dockerfileName'. " +
                "Use 'dockerfile' for custom paths or 'dockerfileName' for custom names in default locations."
            )
        }
        
        // Validate traditional context directory exists if explicitly specified
        if (hasExplicitContext) {
            def contextDir = imageSpec.context.get().asFile
            if (!contextDir.exists()) {
                throw new GradleException(
                    "Docker context directory does not exist: ${contextDir.absolutePath}\n" +
                    "Suggestion: Create the directory or update the context path in image '${imageSpec.name}'"
                )
            }
        }
        
        // Validate dockerfile exists (but not for sourceRef images)
        if (imageSpec.dockerfile.present && !hasSourceRef) {
            def dockerfileFile = imageSpec.dockerfile.get().asFile
            if (!dockerfileFile.exists()) {
                throw new GradleException(
                    "Dockerfile does not exist: ${dockerfileFile.absolutePath}\n" +
                    "Suggestion: Create the Dockerfile or update the dockerfile path in image '${imageSpec.name}'"
                )
            }
        }
        
        // Validate image tags - must be required for build contexts and must be full image references
        def hasBuildContext = hasExplicitContext || hasContextTask
        if (hasBuildContext && (!imageSpec.tags.present || imageSpec.tags.get().isEmpty())) {
            throw new GradleException(
                "Image '${imageSpec.name}' must specify at least one tag when building (context or contextTask specified)"
            )
        }
        
        // Validate full image reference format and consistent image names
        if (imageSpec.tags.present && !imageSpec.tags.get().empty) {
            def tags = imageSpec.tags.get()
            String firstImageName = null
            
            tags.each { tag ->
                if (!isValidImageReference(tag)) {
                    throw new GradleException(
                        "Invalid Docker image reference: '${tag}' in image '${imageSpec.name}'. " +
                        "Tags must be full image references like 'myapp:latest', 'registry.com:5000/team/myapp:v1.0.0'"
                    )
                }
                
                // Extract and validate consistent image names
                def imageName = extractImageName(tag)
                if (firstImageName == null) {
                    firstImageName = imageName
                } else if (imageName != firstImageName) {
                    throw new GradleException(
                        "All tags must reference the same image name. Found: '${imageName}' vs '${firstImageName}' in image '${imageSpec.name}'"
                    )
                }
            }
        }
        
        // Validate publish configuration if present
        if (imageSpec.publish.present) {
            validatePublishConfiguration(imageSpec.publish.get(), imageSpec.name)
        }
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
        // Validate that tags are specified
        if (!publishTarget.tags.present || publishTarget.tags.get().isEmpty()) {
            throw new GradleException(
                "Publish target '${publishTarget.name}' in image '${imageName}' must specify at least one tag"
            )
        }
        
        // Validate each tag as a full image reference
        def tags = publishTarget.tags.get()
        tags.each { tag ->
            if (!isValidImageReference(tag)) {
                throw new GradleException(
                    "Invalid image reference: '${tag}' in publish target '${publishTarget.name}' of image '${imageName}'. " +
                    "Tags must be full image references like 'myapp:latest', 'registry.com/team/myapp:v1.0.0', or 'localhost:5000/namespace/myapp:tag'."
                )
            }
        }
    }
    
    /**
     * Validates full Docker image references supporting format: [registry[:port]/]namespace/name:tag
     * Valid examples: "myapp:latest", "registry.com/team/app:v1.0.0", "localhost:5000/namespace/app:tag"
     */
    boolean isValidImageReference(String imageRef) {
        if (!imageRef || !imageRef.contains(':')) {
            return false
        }
        
        // Find the last colon that separates image name from tag
        // We need to be careful about registry:port format
        int lastColon = imageRef.lastIndexOf(':')
        
        // Check if this might be a port number by looking for a slash after the colon
        boolean isTag = true
        for (int i = lastColon + 1; i < imageRef.length(); i++) {
            char c = imageRef.charAt(i)
            if (c == '/') {
                // Found a slash after colon, this is a port number, not a tag
                isTag = false
                break
            }
            if (!Character.isDigit(c)) {
                // Non-digit after colon, this is definitely a tag
                break
            }
        }
        
        if (!isTag) {
            // The last colon is part of a port, find the previous one
            int prevColon = imageRef.lastIndexOf(':', lastColon - 1)
            if (prevColon == -1) {
                return false // No tag separator found
            }
            lastColon = prevColon
        }
        
        if (lastColon <= 0 || lastColon >= imageRef.length() - 1) {
            return false
        }
        
        String imageName = imageRef.substring(0, lastColon)
        String tag = imageRef.substring(lastColon + 1)
        
        // Validate using simpler approach
        return isValidDockerImageName(imageName) && isValidDockerTag(tag)
    }

    /**
     * Validates the image name part of an image reference (before the tag)
     * Handles registry:port/namespace/name format
     */
    private boolean isValidDockerImageName(String imageName) {
        if (!imageName || imageName.length() > 255) {
            return false
        }
        
        // Docker image names can contain:
        // - lowercase letters, digits, periods, hyphens, underscores, slashes, colons (for registry:port)
        // - Must not start or end with special characters (except for registry hostnames)
        
        // Simple regex that matches Docker's liberal approach
        return imageName.matches(/^[a-zA-Z0-9._:-]+([\/][a-zA-Z0-9._-]+)*$/) && 
               !imageName.startsWith('/') && !imageName.endsWith('/') &&
               !imageName.startsWith('-') && !imageName.endsWith('-')
    }
    
    /**
     * Validates a registry hostname (more permissive than regular name components)
     */
    private boolean isValidDockerTag(String tag) {
        if (!tag || tag.length() > 128) {
            return false
        }
        
        // Docker tags can contain letters, digits, periods, dashes, underscores
        // Must not start with period or dash
        return tag.matches(/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/) || tag.matches(/^[a-zA-Z0-9]$/)
    }

    /**
     * Extracts the image name portion from a full image reference
     * Example: "registry.com:5000/team/myapp:v1.0.0" -> "registry.com:5000/team/myapp"
     */
    String extractImageName(String imageRef) {
        if (!imageRef || !imageRef.contains(':')) {
            throw new IllegalArgumentException("Invalid image reference: ${imageRef}")
        }
        
        def lastColon = imageRef.lastIndexOf(':')
        return imageRef.substring(0, lastColon)
    }
    
    /**
     * Validates simple tag names (tag portion only, no repository)
     * Used for publish target tags which are simple names
     */
    boolean isValidSimpleTag(String tag) {
        // Docker tag name validation (tag portion only, no repository)
        // Valid examples: "latest", "v1.0.0", "dev-123", "1.0", "stable"
        // Docker allows: letters, digits, underscores, periods, dashes
        // Must not start with period or dash, max 128 characters
        return tag && tag.matches(/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/) && tag.length() <= 128 && !tag.contains(":")
    }
    
    /**
     * Validates repository name format for publish targets
     */
    boolean isValidRepositoryName(String repository) {
        // Repository name validation for Docker registries
        // Valid examples: "myapp", "docker.io/myapp", "localhost:5000/namespace/app"
        // Basic validation - allow alphanumeric, dots, colons, slashes, dashes
        return repository && repository.matches(/^[a-zA-Z0-9][a-zA-Z0-9._:\/-]*[a-zA-Z0-9]$/) && repository.length() <= 255
    }

}