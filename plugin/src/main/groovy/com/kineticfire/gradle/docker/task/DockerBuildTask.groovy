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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task for building Docker images
 */
abstract class DockerBuildTask extends DefaultTask {
    
    DockerBuildTask() {
        group = 'docker'
        description = 'Build Docker image'
        
        // Set up default values
        buildArgs.convention([:])
        tags.convention([])
    }
    
    @Internal
    abstract Property<DockerService> getDockerService()
    
    @InputFile
    abstract RegularFileProperty getDockerfile()
    
    @InputDirectory
    abstract DirectoryProperty getContextPath()
    
    @Input
    abstract ListProperty<String> getTags()
    
    @Input
    @Optional
    abstract MapProperty<String, String> getBuildArgs()
    
    @OutputFile
    abstract RegularFileProperty getImageId()
    
    @TaskAction
    void buildImage() {
        // Validate required properties
        if (!dockerfile.present) {
            throw new IllegalStateException("dockerfile property must be set")
        }
        if (!tags.present || tags.get().isEmpty()) {
            throw new IllegalStateException("tags property must be set and not empty")
        }
        
        logger.lifecycle("DockerBuildTask: Building image (placeholder implementation)")
        logger.lifecycle("  Dockerfile: ${dockerfile.get().asFile}")
        logger.lifecycle("  Context: ${contextPath.get().asFile}")
        logger.lifecycle("  Tags: ${tags.get()}")
        logger.lifecycle("  Build Args: ${buildArgs.get()}")
        
        // TODO: Implement actual Docker build logic with service integration
        // Create a build context and call the service
        def context = new com.kineticfire.gradle.docker.model.BuildContext(
            contextPath.get().asFile.toPath(),
            dockerfile.get().asFile.toPath(),
            buildArgs.get(),
            tags.get()
        )
        
        def future = dockerService.get().buildImage(context)
        def actualImageId = future.get()
        
        // Write the image ID to the output file
        def imageIdFile = imageId.get().asFile
        imageIdFile.parentFile.mkdirs()
        imageIdFile.text = actualImageId
    }
}