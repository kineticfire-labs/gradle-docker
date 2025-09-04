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
        // Validate required properties
        if (!imageSpec.context.present && !imageSpec.sourceRef.present) {
            throw new GradleException(
                "Image '${imageSpec.name}' must specify either 'context' for building or 'sourceRef' for referencing existing image"
            )
        }
        
        // Validate context exists if specified
        if (imageSpec.context.present) {
            def contextDir = imageSpec.context.get().asFile
            if (!contextDir.exists()) {
                throw new GradleException(
                    "Docker context directory does not exist: ${contextDir.absolutePath}\\n" +
                    "Suggestion: Create the directory or update the context path in image '${imageSpec.name}'"
                )
            }
        }
        
        // Validate dockerfile exists
        if (imageSpec.dockerfile.present) {
            def dockerfileFile = imageSpec.dockerfile.get().asFile
            if (!dockerfileFile.exists()) {
                throw new GradleException(
                    "Dockerfile does not exist: ${dockerfileFile.absolutePath}\\n" +
                    "Suggestion: Create the Dockerfile or update the dockerfile path in image '${imageSpec.name}'"
                )
            }
        }
        
        // Validate tags format - ImageSpec tags are tag names only (not full image references)
        if (imageSpec.tags.present && !imageSpec.tags.get().empty) {
            imageSpec.tags.get().each { tag ->
                if (!isValidTagName(tag)) {
                    throw new GradleException(
                        "Invalid Docker tag name: '${tag}' in image '${imageSpec.name}'\\n" +
                        "Suggestion: Use valid tag names (e.g., 'latest', 'v1.0.0', 'dev')"
                    )
                }
            }
        }
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