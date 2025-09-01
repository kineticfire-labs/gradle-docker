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
    }
    
    @Input
    abstract Property<DockerService> getDockerService()
    
    @Input
    abstract Property<String> getSourceImage()
    
    @Input
    abstract ListProperty<String> getTags()
    
    @TaskAction
    void tagImage() {
        // Validate required properties
        if (!sourceImage.present) {
            throw new IllegalStateException("sourceImage property must be set")
        }
        if (!tags.present || tags.get().isEmpty()) {
            throw new IllegalStateException("tags property must be set and not empty")
        }
        
        logger.lifecycle("DockerTagTask: Tagging image (placeholder implementation)")
        logger.lifecycle("  Source Image: ${sourceImage.get()}")
        logger.lifecycle("  Tags: ${tags.get()}")
        
        // Call Docker service to tag the image
        dockerService.get().tagImage(sourceImage.get(), tags.get())
    }
}