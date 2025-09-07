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
            
            // Set default dockerfile if not specified  
            imageSpec.dockerfile.convention(
                imageSpec.context.file("Dockerfile")
            )
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
        // Check if any context configuration is explicitly provided
        def hasExplicitContext = imageSpec.context.isPresent() && !isConventionValue(imageSpec.context)
        def hasContextTask = imageSpec.contextTask.present
        def hasSourceRef = imageSpec.sourceRef.present
        
        // Validate required properties - now supports multiple context types
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
        
        // Validate image tags format - ImageSpec tags are simple tag names only (not full image references)
        if (imageSpec.tags.present && !imageSpec.tags.get().empty) {
            imageSpec.tags.get().each { tag ->
                if (!isValidTagName(tag)) {
                    throw new GradleException(
                        "Invalid Docker tag name: '${tag}' in image '${imageSpec.name}'\n" +
                        "Image tags should be simple names like 'latest', 'v1.0.0', 'dev'.\n" +
                        "For registry publishing, specify the repository in publish configuration."
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
        
        // Validate publish target tags - these should also be simple tag names
        if (publishTarget.tags.present) {
            def tags = publishTarget.tags.get()
            if (!tags.isEmpty()) {
                tags.each { tag ->
                    if (!isValidTagName(tag)) {
                        throw new GradleException(
                            "Invalid tag name: '${tag}' in publish target '${publishTarget.name}' of image '${imageName}'\\n" +
                            "Publish target tags should be simple names like 'latest', 'v1.0.0', 'stable'."
                        )
                    }
                }
            }
        }
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

    /**
     * Check if a property value is a convention (default) value rather than explicitly set
     */
    private boolean isConventionValue(def property) {
        // For Directory/File properties, check if the value matches the default convention
        if (property.present) {
            def value = property.get().asFile
            def conventionPath = project.layout.projectDirectory.dir("src/main/docker").asFile
            return value == conventionPath
        }
        return false
    }

    boolean isValidTagName(String tag) {
        // Docker tag name validation (tag portion only, no repository)
        // Valid examples: "latest", "v1.0.0", "dev-123", "1.0", "stable"
        // Docker allows: letters, digits, underscores, periods, dashes
        // Must not start with period or dash, max 128 characters
        return tag && tag.matches(/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/) && tag.length() <= 128 && !tag.contains(":")
    }
    
    boolean isValidImageReference(String imageRef) {
        // Docker image reference validation (repository:tag format)
        // Valid examples: "myapp:latest", "registry.com/team/app:v1.0.0"
        return imageRef && imageRef.matches(/^[a-zA-Z0-9][a-zA-Z0-9._\\/-]*:[a-zA-Z0-9][a-zA-Z0-9._-]*$/)
    }
    
    @Deprecated
    boolean isValidDockerTag(String tag) {
        // Legacy method - kept for backward compatibility in tests
        // This was the problematic method that required repository:tag format
        return isValidImageReference(tag)
    }
}