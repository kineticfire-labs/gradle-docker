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
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task for tagging Docker images
 */
abstract class DockerTagTask extends DefaultTask {
    
    DockerTagTask() {
        group = 'docker'
        description = 'Tag Docker image'
        
        // Set up default values
        tags.convention([])
        pullIfMissing.convention(false)
    }
    
    @Internal
    abstract Property<DockerService> getDockerService()
    
    @Input
    abstract Property<String> getSourceImage()
    
    @Input
    abstract ListProperty<String> getTags()
    
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    
    @Input
    @Optional
    abstract Property<Boolean> getPullIfMissing()
    
    @TaskAction
    void tagImage() {
        // Validate required properties
        if (!sourceImage.present && !sourceRef.present) {
            throw new IllegalStateException("Either sourceImage or sourceRef property must be set")
        }
        if (!tags.present || tags.get().isEmpty()) {
            throw new IllegalStateException("tags property must be set and not empty")
        }
        
        // Resolve source image (built vs sourceRef)
        String imageToTag = resolveSourceImage()
        
        logger.lifecycle("Tagging image {} with tags: {}", imageToTag, tags.get())
        
        // Call Docker service to tag the image
        dockerService.get().tagImage(imageToTag, tags.get())
        
        logger.lifecycle("Successfully tagged image {} with {} tags", imageToTag, tags.get().size())
    }
    
    private String resolveSourceImage() {
        if (sourceRef.present) {
            String ref = sourceRef.get()
            if (pullIfMissing.get() && !dockerService.get().imageExists(ref).get()) {
                logger.lifecycle("Pulling missing image: {}", ref)
                dockerService.get().pullImage(ref, null).get()
            }
            return ref
        }
        
        // Use sourceImage if sourceRef is not present
        return sourceImage.get()
    }
}