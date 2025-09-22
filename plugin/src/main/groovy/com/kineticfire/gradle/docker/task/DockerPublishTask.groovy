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

import com.kineticfire.gradle.docker.exception.DockerServiceException
import com.kineticfire.gradle.docker.model.AuthConfig
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.PublishTarget
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

import javax.inject.Inject
import java.util.concurrent.CompletableFuture

/**
 * Task for publishing Docker images to registries using new nomenclature
 */
abstract class DockerPublishTask extends DefaultTask {
    
    DockerPublishTask() {
        // Set up default values for Provider API compatibility
        group = 'docker'
        description = 'Publishes Docker image to configured registries'

        registry.convention("")
        namespace.convention("")
        imageName.convention("")
        repository.convention("")
        version.convention("")
        tags.convention([])
        publishTargets.convention([])

        // Map publishSpec.to to publishTargets
        publishSpec.convention(null)
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
    @Optional
    abstract Property<String> getVersion()
    
    @Input
    abstract ListProperty<String> getTags()
    
    @Input
    abstract ListProperty<PublishTarget> getPublishTargets()

    // Compatibility property for tests (maps to publishTargets)
    @Internal
    abstract Property<Object> getPublishSpec()

    @InputFile
    @Optional
    abstract RegularFileProperty getImageIdFile()
    
    @TaskAction
    void publishImage() {
        def service = dockerService.get()
        def sourceImageRef = buildSourceImageReference()

        // Handle both publishSpec and publishTargets
        def targets = []
        if (publishSpec.isPresent() && publishSpec.get()?.to) {
            targets = publishSpec.get().to
        } else {
            targets = publishTargets.getOrElse([])
        }
        
        if (!sourceImageRef) {
            throw new IllegalStateException("Unable to build source image reference from nomenclature")
        }
        
        if (targets.empty) {
            logger.lifecycle("No publish targets configured, skipping publish")
            return
        }
        
        logger.lifecycle("Publishing image '{}' to {} targets", sourceImageRef, targets.size())
        
        def publishFutures = []
        
        targets.each { target ->
            def targetRefs = buildTargetImageReferences(target)
            def authSpec = target.auth.orNull
            
            if (targetRefs.empty) {
                logger.warn("No target references for publish target '${target.name}', skipping")
                return
            }
            
            // Create auth config once per target
            def authConfig = authSpec?.toAuthConfig()
            
            targetRefs.each { targetRef ->
                logger.info("Publishing {} as {}", sourceImageRef, targetRef)
                
                // First tag the local image with the target registry tag
                def tagFuture = service.tagImage(sourceImageRef, [targetRef])
                    .whenComplete { result, throwable ->
                        if (throwable) {
                            logger.error("Failed to tag {} as {}: {}", sourceImageRef, targetRef, throwable.message)
                        } else {
                            logger.debug("Successfully tagged {} as {}", sourceImageRef, targetRef)
                        }
                    }
                
                // Then push the registry tag
                def publishFuture = tagFuture.thenCompose { 
                    service.pushImage(targetRef, authConfig) 
                }.whenComplete { result, throwable ->
                    if (throwable) {
                        logger.error("Failed to push {}: {}", targetRef, throwable.message)
                    } else {
                        logger.lifecycle("Successfully pushed: {}", targetRef)
                    }
                }
                publishFutures << publishFuture
            }
        }
        
        try {
            CompletableFuture.allOf(publishFutures as CompletableFuture[]).join()
            logger.lifecycle("All publish operations completed successfully")
        } catch (Exception e) {
            def cause = e.cause
            if (cause instanceof DockerServiceException) {
                throw new org.gradle.api.GradleException(
                    "Docker publish failed: ${cause.message}${cause.suggestion ? " - ${cause.suggestion}" : ""}", 
                    cause
                )
            } else {
                def rootCause = e.cause ?: e
                throw new org.gradle.api.GradleException("Docker publish failed: ${rootCause.message}", rootCause)
            }
        }
    }
    
    /**
     * Build source image reference from nomenclature properties (use first tag)
     */
    String buildSourceImageReference() {
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
    
    /**
     * Build target image references for a publish target
     */
    List<String> buildTargetImageReferences(PublishTarget target) {
        def targetRefs = []
        
        def targetRegistryValue = target.registry.getOrElse("")
        def targetNamespaceValue = target.namespace.getOrElse("")
        def targetRepositoryValue = target.repository.getOrElse("")
        def targetImageNameValue = target.imageName.getOrElse("")
        def targetPublishTags = target.publishTags.getOrElse([])
        
        if (targetPublishTags.isEmpty()) {
            return targetRefs
        }
        
        if (!targetRepositoryValue.isEmpty()) {
            // Using repository format
            def baseRef = targetRegistryValue.isEmpty() ? targetRepositoryValue : "${targetRegistryValue}/${targetRepositoryValue}"
            targetPublishTags.each { tag ->
                targetRefs.add("${baseRef}:${tag}")
            }
        } else {
            // Using namespace + imageName format
            def baseRef = ""
            if (!targetRegistryValue.isEmpty()) {
                baseRef += "${targetRegistryValue}/"
            }
            if (!targetNamespaceValue.isEmpty()) {
                baseRef += "${targetNamespaceValue}/"
            }
            
            // Use target imageName if provided, otherwise fall back to source imageName
            def targetImageName = targetImageNameValue.isEmpty() ? imageName.getOrElse("") : targetImageNameValue
            if (targetImageName.isEmpty()) {
                return targetRefs
            }
            baseRef += targetImageName
            
            targetPublishTags.each { tag ->
                targetRefs.add("${baseRef}:${tag}")
            }
        }
        
        return targetRefs
    }
}