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
import org.gradle.api.GradleException
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
        imageName.convention("")
        repository.convention("")
        version.convention("")
        sourceRefMode.convention(false)
        
        // Skip execution in sourceRef mode
        onlyIf { task -> !task.sourceRefMode.get() }
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
    @Optional
    abstract RegularFileProperty getDockerfile()

    @InputDirectory
    @Optional
    abstract DirectoryProperty getContextPath()

    @Input
    @Optional
    abstract MapProperty<String, String> getBuildArgs()

    // SourceRef mode indicator
    @Input
    abstract Property<Boolean> getSourceRefMode()

    @OutputFile
    @Optional
    abstract RegularFileProperty getImageId()
    
    @TaskAction
    void buildImage() {
        // Configuration cache compatible implementation
        // Validate required properties
        if (!dockerfile.isPresent()) {
            throw new IllegalStateException("dockerfile property must be set")
        }

        // Validate mutual exclusivity between repository and namespace+imageName
        def repositoryValue = repository.getOrElse("")
        def namespaceValue = namespace.getOrElse("")
        def imageNameValue = imageName.getOrElse("")

        if (!repositoryValue.isEmpty() && (!namespaceValue.isEmpty() || !imageNameValue.isEmpty())) {
            throw new IllegalStateException("Cannot use both 'repository' and 'namespace/imageName' nomenclature simultaneously")
        }
        
        // Build full image references using new nomenclature
        def imageReferences = buildImageReferences()
        if (imageReferences.isEmpty()) {
            throw new IllegalStateException("No image references could be built from nomenclature")
        }

        // Build Docker image using service
        def service = dockerService.get()
        if (!service) {
            throw new IllegalStateException("dockerService must be provided")
        }
        def contextFile = contextPath.get()?.asFile
        def dockerfileFile = dockerfile.get()?.asFile
        if (!contextFile || !dockerfileFile) {
            throw new IllegalStateException("contextPath and dockerfile must be provided")
        }

        // Note: Dockerfile existence validation now handled by BuildContext constructor

        def context = new com.kineticfire.gradle.docker.model.BuildContext(
            contextFile.toPath(),
            dockerfileFile.toPath(),
            buildArgs.getOrElse([:]),
            imageReferences,
            labels.getOrElse([:])
        )
        
        def future = service.buildImage(context)
        def actualImageId = future.get()
        
        // Write the image ID to the output file
        def imageIdRegularFile = imageId.get()
        if (!imageIdRegularFile) {
            throw new IllegalStateException("imageId must be provided")
        }
        def imageIdFile = imageIdRegularFile.asFile
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
        def versionValue = version.getOrElse("")
        def tagsValue = tags.getOrElse([])
        
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