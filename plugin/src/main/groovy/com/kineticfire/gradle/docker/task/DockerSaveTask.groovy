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

import com.kineticfire.gradle.docker.model.AuthConfig
import com.kineticfire.gradle.docker.model.CompressionType
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.AuthSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task for saving Docker images to files with optional compression
 */
abstract class DockerSaveTask extends DefaultTask {
    
    DockerSaveTask() {
        group = 'docker'
        description = 'Save Docker image to file'
        
        // Set defaults
        pullIfMissing.convention(false)
    }
    
    @Internal
    abstract Property<DockerService> getDockerService()
    
    @Input
    @Optional
    abstract Property<String> getImageName()
    
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    
    @Input
    abstract Property<CompressionType> getCompression()
    
    @OutputFile
    abstract RegularFileProperty getOutputFile()
    
    @Input
    @Optional
    abstract Property<Boolean> getPullIfMissing()

    @Input
    @Optional
    abstract Property<AuthSpec> getAuth()

    @TaskAction
    void saveImage() {
        // Validate required properties
        if (!imageName.present && !sourceRef.present) {
            throw new IllegalStateException("Either imageName or sourceRef property must be set")
        }
        if (!outputFile.present) {
            throw new IllegalStateException("outputFile property must be set")
        }
        if (!compression.present) {
            throw new IllegalStateException("compression property must be set. Available options: 'none', 'gzip', 'bzip2', 'xz', 'zip'")
        }
        
        // Resolve image source (built vs sourceRef)
        String imageToSave = resolveImageSource()
        
        logger.lifecycle("Saving image {} to {} with compression: {}", imageToSave, outputFile.get().asFile, compression.get())
        
        // Call existing service method
        dockerService.get().saveImage(
            imageToSave, 
            outputFile.get().asFile.toPath(),
            compression.get()
        ).get()
        
        logger.lifecycle("Successfully saved image {} to {}", imageToSave, outputFile.get().asFile)
    }
    
    private String resolveImageSource() {
        if (sourceRef.present) {
            String ref = sourceRef.get()
            if (pullIfMissing.get() && !dockerService.get().imageExists(ref).get()) {
                logger.lifecycle("Pulling missing image: {}", ref)

                // NEW: Pass authentication if configured
                AuthConfig authConfig = getAuthConfigFromSaveSpec()
                if (authConfig != null) {
                    logger.lifecycle("Using authentication for pulling image from registry")
                }

                dockerService.get().pullImage(ref, authConfig).get()
            }
            return ref
        }

        // Use imageName if sourceRef is not present
        return imageName.get()
    }

    /**
     * Convert AuthSpec to AuthConfig for DockerService
     */
    private AuthConfig getAuthConfigFromSaveSpec() {
        if (!auth.present) {
            return null
        }

        return auth.get().toAuthConfig()
    }
}