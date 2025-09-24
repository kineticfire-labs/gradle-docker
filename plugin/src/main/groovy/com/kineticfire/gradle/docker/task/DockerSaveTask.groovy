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

import com.kineticfire.gradle.docker.model.SaveCompression
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.AuthSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.TaskAction

/**
 * Task for saving Docker images to files
 */
abstract class DockerSaveTask extends DefaultTask {
    
    DockerSaveTask() {
        // Set up default values for Provider API compatibility
        compression.convention(SaveCompression.NONE)
        registry.convention("")
        namespace.convention("")
        imageName.convention("")
        repository.convention("")
        version.convention("")
        tags.convention([])
        sourceRef.convention("")
        pullIfMissing.convention(false)
    }
    
    @Internal
    abstract Property<DockerService> getDockerService()
    
    // SourceRef Mode Properties (for existing images)
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    
    @Input
    @Optional
    abstract Property<Boolean> getPullIfMissing()
    
    @Nested
    @Optional
    abstract Property<AuthSpec> getAuth()
    
    // Docker Image Nomenclature Properties (for building new images)
    @Input
    @Optional
    abstract Property<String> getRegistry()
    
    @Input
    @Optional
    abstract Property<String> getNamespace()
    
    @Input
    @Optional
    abstract Property<String> getImageName()
    
    @Input
    @Optional
    abstract Property<String> getRepository()
    
    @Input
    abstract Property<String> getVersion()
    
    @Input
    abstract ListProperty<String> getTags()
    
    @Input
    abstract Property<SaveCompression> getCompression()
    
    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void saveImage() {
        // Configuration cache compatible implementation
        if (!outputFile.isPresent()) {
            throw new IllegalStateException("outputFile property must be set")
        }

        // Build the primary image reference for saving
        def imageRef = buildPrimaryImageReference()
        if (!imageRef) {
            throw new IllegalStateException("Unable to build image reference")
        }

        // Handle pullIfMissing for sourceRef mode
        def sourceRefValue = sourceRef.getOrElse("")
        if (!sourceRefValue.isEmpty() && pullIfMissing.getOrElse(false)) {
            def service = dockerService.get()
            if (!service) {
                throw new IllegalStateException("dockerService must be provided for pullIfMissing")
            }
            def exists = service.imageExists(imageRef).get()
            if (!exists) {
                // Pull with authentication if configured
                def authConfig = null
                if (auth.isPresent()) {
                    def authSpec = auth.get()
                    authConfig = new com.kineticfire.gradle.docker.model.AuthConfig(
                        authSpec.username.getOrElse(""),
                        authSpec.password.getOrElse(""),
                        authSpec.registryToken.getOrElse("")
                    )
                }
                def future = service.pullImage(imageRef, authConfig)
                future.get()
            }
        }

        // Save Docker image using service
        def service = dockerService.get()
        if (!service) {
            throw new IllegalStateException("dockerService must be provided")
        }
        def compressionType = compression.getOrElse(SaveCompression.NONE)
        def outputFileValue = outputFile.get()
        if (!outputFileValue) {
            throw new IllegalStateException("outputFile must be provided")
        }
        def outputPath = outputFileValue.asFile.toPath()

        def future = service.saveImage(imageRef, outputPath, compressionType)
        if (!future) {
            throw new IllegalStateException("saveImage future cannot be null")
        }
        future.get()
    }
    
    /**
     * Build primary image reference from dual-mode properties (SourceRef vs Build Mode)
     */
    String buildPrimaryImageReference() {
        def sourceRefValue = sourceRef.getOrElse("")
        
        if (!sourceRefValue.isEmpty()) {
            // SourceRef Mode: Use sourceRef directly
            return sourceRefValue
        } else {
            // Build Mode: Use nomenclature to build image reference
            def registryValue = registry.getOrElse("")
            def namespaceValue = namespace.getOrElse("")
            def repositoryValue = repository.getOrElse("")
            def imageNameValue = imageName.getOrElse("")
            def versionValue = version.getOrElse("")
            def tagsValue = tags.getOrElse([])
            
            if (tagsValue.isEmpty()) {
                return null
            }
            
            def primaryTag = tagsValue[0]
            
            if (!repositoryValue.isEmpty()) {
                // Using repository format
                def baseRef = registryValue.isEmpty() ? repositoryValue : "${registryValue}/${repositoryValue}"
                return "${baseRef}:${primaryTag}"
            } else if (!imageNameValue.isEmpty()) {
                // Using namespace + imageName format
                def baseRef = ""
                if (!registryValue.isEmpty()) {
                    baseRef += "${registryValue}/"
                }
                if (!namespaceValue.isEmpty()) {
                    baseRef += "${namespaceValue}/"
                }
                baseRef += imageNameValue
                
                return "${baseRef}:${primaryTag}"
            }
            
            return null
        }
    }
}