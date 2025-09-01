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
        
        // Validate tags format
        if (imageSpec.tags.present && !imageSpec.tags.get().empty) {
            imageSpec.tags.get().each { tag ->
                if (!isValidDockerTag(tag)) {
                    throw new GradleException(
                        "Invalid Docker tag format: '${tag}' in image '${imageSpec.name}'\\n" +
                        "Suggestion: Use format 'repository:tag' (e.g., 'myapp:latest')"
                    )
                }
            }
        }
    }
    
    boolean isValidDockerTag(String tag) {
        // Basic Docker tag validation
        return tag && tag.matches(/^[a-zA-Z0-9][a-zA-Z0-9._\\/-]*:[a-zA-Z0-9][a-zA-Z0-9._-]*$/)
    }
}