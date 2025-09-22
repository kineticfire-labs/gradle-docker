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
import org.gradle.api.tasks.*
import org.gradle.api.tasks.TaskAction

/**
 * Task for tagging Docker images
 */
abstract class DockerTagTask extends DefaultTask {
    
    DockerTagTask() {
        // Set up default values for Provider API compatibility
        registry.convention("")
        namespace.convention("")
        repository.convention("")
        sourceRef.convention("")
    }
    
    @Internal
    abstract Property<DockerService> getDockerService()
    
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
    @Optional
    abstract Property<String> getVersion()
    
    @Input
    abstract ListProperty<String> getTags()

    @TaskAction
    void tagImage() {
        // Configuration cache compatible implementation
        def imageReferences = buildImageReferences()
        if (imageReferences.size() < 2) {
            throw new IllegalStateException("At least 2 image references needed for tagging (source + target tags)")
        }
        
        def sourceImageRef = imageReferences[0]
        def targetRefs = imageReferences.drop(1)

        // Tag Docker images using service
        def service = dockerService.get()
        targetRefs.each { targetRef ->
            def future = service.tagImage(sourceImageRef, [targetRef])
            future.get()
        }
    }
    
    /**
     * Build all image references from dual-mode properties (SourceRef vs Build Mode)
     */
    List<String> buildImageReferences() {
        def references = []
        def sourceRefValue = sourceRef.getOrElse("")
        def tagsValue = tags.get()
        
        if (tagsValue.isEmpty()) {
            throw new IllegalStateException("At least one tag must be specified")
        }
        
        if (!sourceRefValue.isEmpty()) {
            // SourceRef Mode: Use sourceRef as source, apply tags as targets
            references.add(sourceRefValue)
            tagsValue.each { tag ->
                references.add(tag)
            }
        } else {
            // Build Mode: Use nomenclature to build image references
            def registryValue = registry.getOrElse("")
            def namespaceValue = namespace.getOrElse("")
            def repositoryValue = repository.getOrElse("")
            def imageNameValue = imageName.getOrElse("")
            def versionValue = version.getOrElse("")
            
            if (repositoryValue.isEmpty() && imageNameValue.isEmpty()) {
                throw new IllegalStateException("Either repository OR imageName must be specified when not using sourceRef")
            }
            
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
        }
        
        return references
    }
}