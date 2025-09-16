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
 * Task for publishing Docker images to registries
 */
abstract class DockerPublishTask extends DefaultTask {
    
    @Input
    abstract Property<String> getImageName()
    
    @Input
    abstract ListProperty<PublishTarget> getPublishTargets()
    
    @InputFile
    @Optional
    abstract RegularFileProperty getImageIdFile()
    
    @Nested
    abstract Property<DockerService> getDockerService()
    
    @Inject
    DockerPublishTask() {
        group = 'docker'
        description = 'Publishes Docker image to configured registries'
    }
    
    @TaskAction
    void publishImage() {
        def service = dockerService.get()
        def imageName = getImageNameToPublish()
        def targets = publishTargets.get()
        
        if (targets.empty) {
            logger.lifecycle("No publish targets configured, skipping publish")
            return
        }
        
        logger.lifecycle("Publishing image '{}' to {} targets", imageName, targets.size())
        
        def publishFutures = []
        
        targets.each { target ->
            def tags = target.tags.getOrElse([])
            def authSpec = target.auth.orNull
            
            if (tags.empty) {
                logger.warn("No tags specified for publish target '${target.name}', skipping")
                return
            }
            
            // Create auth config once per target (outside the inner loop)
            def authConfig = authSpec?.toAuthConfig()
            
            tags.each { fullImageRef ->
                logger.info("Publishing {} as {}", imageName, fullImageRef)
                
                def publishFuture = service.pushImage(fullImageRef, authConfig)
                    .whenComplete { result, throwable ->
                        if (throwable) {
                            logger.error("Failed to push {}: {}", fullImageRef, throwable.message)
                        } else {
                            logger.lifecycle("Successfully pushed: {}", fullImageRef)
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
    
    private String getImageNameToPublish() {
        def name = imageName.getOrNull()
        if (name) {
            return name
        }
        
        def imageIdFile = this.imageIdFile.orNull
        if (imageIdFile && imageIdFile.asFile.exists()) {
            def imageId = imageIdFile.asFile.text.trim()
            logger.debug("Read image ID from file: {}", imageId)
            return imageId
        }
        
        throw new org.gradle.api.GradleException(
            "No image name specified and no image ID file found. Set imageName property or ensure imageIdFile exists."
        )
    }
    
}