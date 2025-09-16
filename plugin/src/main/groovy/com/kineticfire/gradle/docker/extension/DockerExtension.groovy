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
        // Validate repository format
        if (!publishTarget.repository.present) {
            throw new GradleException(
                "Publish target '${publishTarget.name}' in image '${imageName}' must specify a repository"
            )
        }
        
        def repository = publishTarget.repository.get()
        if (!isValidRepositoryName(repository)) {
            throw new GradleException(
                "Invalid repository name: '${repository}' in publish target '${publishTarget.name}' of image '${imageName}'\\n" +
                "Repository should be in format: [registry/]namespace/name (e.g., 'docker.io/myapp', 'localhost:5000/test')"
            )
        }
        
        // Validate publish target tags - these should be simple tag names
        if (publishTarget.publishTags.present) {
            def tags = publishTarget.publishTags.get()
            if (!tags.isEmpty()) {
                tags.each { tag ->
                    if (!isValidSimpleTag(tag)) {
                        throw new GradleException(
                            "Invalid tag name: '${tag}' in publish target '${publishTarget.name}' of image '${imageName}'. " +
                            "Publish target tags should be simple names like 'latest', 'v1.0.0', 'stable'."
                        )
                    }
                }
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
        
        // Split on last colon to separate image name from tag
        def lastColon = imageRef.lastIndexOf(':')
        if (lastColon <= 0 || lastColon >= imageRef.length() - 1) {
            return false
        }
        
        def imageName = imageRef.substring(0, lastColon)
        def tag = imageRef.substring(lastColon + 1)
        
        // Validate image name part (can include registry/namespace)
        // Allow single character names and handle hyphens properly
        if (imageName.length() == 1) {
            if (!imageName.matches(/^[a-zA-Z0-9]$/)) {
                return false
            }
        } else {
            // Allow hyphens in middle, start/end with alphanumeric
            if (!imageName.matches(/^[a-zA-Z0-9][a-zA-Z0-9._\-\/]*[a-zA-Z0-9]$/) && !imageName.matches(/^[a-zA-Z0-9]$/)) {
                return false
            }
        }
        if (imageName.length() > 255) {
            return false
        }
        
        // Validate tag part - allow single character tags and hyphens
        if (tag.length() == 1) {
            if (!tag.matches(/^[a-zA-Z0-9]$/)) {
                return false
            }
        } else {
            if (!tag.matches(/^[a-zA-Z0-9][a-zA-Z0-9._\-]*$/)) {
                return false
            }
        }
        if (tag.length() > 128) {
            return false
        }
        
        return true
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