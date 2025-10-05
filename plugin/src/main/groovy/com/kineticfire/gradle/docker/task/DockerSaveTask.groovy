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
import com.kineticfire.gradle.docker.spec.ImageSpec
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
        effectiveSourceRef.convention("")
        contextTaskName.convention("")
        contextTaskPath.convention("")
    }
    
    @Internal
    abstract Property<DockerService> getDockerService()
    
    // Configuration cache safe alternative to ImageSpec
    @Input
    @Optional
    abstract Property<String> getContextTaskName()

    @Input
    @Optional
    abstract Property<String> getContextTaskPath()

    // Compatibility property for tests (not serialized due to @Internal)
    @Internal
    abstract Property<ImageSpec> getImageSpec()

    // PullIfMissing properties (configuration cache safe alternative to ImageSpec)
    @Input
    @Optional
    abstract Property<Boolean> getPullIfMissing()

    @Input
    @Optional
    abstract Property<String> getEffectiveSourceRef()

    // SourceRef Mode Properties (for existing images)
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    

    
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
        // Pull source image if needed
        pullSourceRefIfNeeded()
        
        // Configuration cache compatible implementation
        if (!outputFile.isPresent()) {
            throw new IllegalStateException("outputFile property must be set")
        }

        // Build the primary image reference for saving
        def imageRef = buildPrimaryImageReference()
        if (!imageRef) {
            throw new IllegalStateException("Unable to build image reference")
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
    
    private void pullSourceRefIfNeeded() {
        // Use individual properties instead of ImageSpec to avoid configuration cache issues
        if (pullIfMissing.getOrElse(false)) {
            def sourceRefValue = effectiveSourceRef.getOrElse("")
            if (sourceRefValue && !sourceRefValue.isEmpty()) {
                // Note: Auth config handling temporarily simplified for configuration cache compatibility
                // TODO: Add configuration cache safe auth config support
                def authConfig = null

                def service = dockerService.get()
                if (!service.imageExists(sourceRefValue).get()) {
                    service.pullImage(sourceRefValue, authConfig).get()
                }
            }
        }
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