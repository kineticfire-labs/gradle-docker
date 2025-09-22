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
import org.gradle.api.tasks.*
import org.gradle.api.tasks.TaskAction

/**
 * Task for building Docker images
 */
abstract class DockerBuildTask extends DefaultTask {
    
    DockerBuildTask() {
        // Set up default values for Provider API compatibility
        buildArgs.convention([:])
        labels.convention([:])
        tags.convention([])
        registry.convention("")
        namespace.convention("")
        repository.convention("")
    }
    
    @Internal
    abstract Property<DockerService> getDockerService()
    
    // Docker Image Nomenclature Properties
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
    abstract MapProperty<String, String> getLabels()
    
    // Build Configuration
    @InputFile
    abstract RegularFileProperty getDockerfile()
    
    @InputDirectory
    abstract DirectoryProperty getContextPath()
    
    @Input
    @Optional
    abstract MapProperty<String, String> getBuildArgs()

    @OutputFile
    abstract RegularFileProperty getImageId()
    
    @TaskAction
    void buildImage() {
        // Configuration cache compatible implementation
        // Validate required properties
        if (!dockerfile.present) {
            throw new IllegalStateException("dockerfile property must be set")
        }
        
        // Build full image references using new nomenclature
        def imageReferences = buildImageReferences()
        if (imageReferences.isEmpty()) {
            throw new IllegalStateException("No image references could be built from nomenclature")
        }

        // Build Docker image using service
        def service = dockerService.get()
        def context = new com.kineticfire.gradle.docker.model.BuildContext(
            contextPath.get().asFile.toPath(),
            dockerfile.get().asFile.toPath(),
            buildArgs.get(),
            imageReferences,
            labels.get()
        )
        
        def future = service.buildImage(context)
        def actualImageId = future.get()
        
        // Write the image ID to the output file
        def imageIdFile = imageId.get().asFile
        imageIdFile.parentFile.mkdirs()
        imageIdFile.text = actualImageId
    }
    
    /**
     * Build full image references from nomenclature properties
     */
    List<String> buildImageReferences() {
        def references = []
        
        def registryValue = registry.getOrElse("")
        def namespaceValue = namespace.getOrElse("")
        def repositoryValue = repository.getOrElse("")
        def imageNameValue = imageName.getOrElse("")
        def versionValue = version.get()
        def tagsValue = tags.get()
        
        if (!repositoryValue.isEmpty()) {
            // Using repository format
            def baseRef = registryValue.isEmpty() ? repositoryValue : "${registryValue}/${repositoryValue}"
            tagsValue.each { tag ->
                references.add("${baseRef}:${tag}")
            }
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
            
            tagsValue.each { tag ->
                references.add("${baseRef}:${tag}")
            }
        }
        
        return references
    }
}