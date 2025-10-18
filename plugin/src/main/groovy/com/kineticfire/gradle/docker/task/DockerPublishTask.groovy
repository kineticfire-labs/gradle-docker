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
import com.kineticfire.gradle.docker.model.EffectiveImageProperties
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.PublishTarget
import com.kineticfire.gradle.docker.spec.ImageSpec
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
        sourceRef.convention("")

        // SourceRef component conventions
        sourceRefRegistry.convention("")
        sourceRefNamespace.convention("")
        sourceRefImageName.convention("")
        sourceRefRepository.convention("")
        sourceRefTag.convention("")

        // Map publishSpec.to to publishTargets
        publishSpec.convention(null)

        // Configuration cache safe property conventions
        contextTaskName.convention("")
        contextTaskPath.convention("")
        pullIfMissing.convention(false)
        effectiveSourceRef.convention("")
    }
    
    @Internal
    abstract Property<DockerService> getDockerService()
    
    // Configuration cache safe alternatives to ImageSpec
    @Input
    @Optional
    abstract Property<String> getContextTaskName()

    @Input
    @Optional
    abstract Property<String> getContextTaskPath()

    @Input
    @Optional
    abstract Property<Boolean> getPullIfMissing()

    @Input
    @Optional
    abstract Property<String> getEffectiveSourceRef()

    // PullIfMissing auth property
    @Nested
    @Optional
    abstract Property<com.kineticfire.gradle.docker.spec.AuthSpec> getPullAuth()
    
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
    
    // SourceRef Mode property (for existing/pre-built images)
    @Input
    @Optional
    abstract Property<String> getSourceRef()

    // SourceRef component properties
    @Input
    @Optional
    abstract Property<String> getSourceRefRegistry()

    @Input
    @Optional
    abstract Property<String> getSourceRefNamespace()

    @Input
    @Optional
    abstract Property<String> getSourceRefImageName()

    @Input
    @Optional
    abstract Property<String> getSourceRefRepository()

    @Input
    @Optional
    abstract Property<String> getSourceRefTag()

    @Input
    abstract ListProperty<PublishTarget> getPublishTargets()

    // Compatibility property for tests (maps to publishTargets)
    @Internal
    abstract Property<Object> getPublishSpec()

    @InputFile
    @Optional
    abstract RegularFileProperty getImageIdFile()
    
    @TaskAction
    void publish() {
        // Pull source image if needed
        pullSourceRefIfNeeded()
        
        // Validate each publish target
        def targets = []
        if (publishSpec.isPresent() && publishSpec.get()?.to) {
            targets = publishSpec.get().to
        } else {
            targets = publishTargets.getOrElse([])
        }
        
        targets.each { target ->
            target.validateRegistry()
            target.validateRegistryConsistency()

            if (target.auth.isPresent()) {
                validateAuthenticationCredentials(target, target.auth.get())
            }
        }

        // Proceed with publish operation
        publishImage()
    }
    
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
        
        // Validate source image exists before attempting publish
        if (!service.imageExists(sourceImageRef).get()) {
            throw new IllegalStateException(
                "Source image '${sourceImageRef}' does not exist. Build the image first or ensure the correct image reference."
            )
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
                    .handle { result, throwable ->
                        if (throwable) {
                            logger.error("Failed to tag {} as {}: {}", sourceImageRef, targetRef, throwable.message)
                            throw throwable
                        } else {
                            logger.debug("Successfully tagged {} as {}", sourceImageRef, targetRef)
                            return result
                        }
                    }
                
                // Then push the registry tag
                def publishFuture = tagFuture.thenCompose { 
                    service.pushImage(targetRef, authConfig) 
                }.handle { result, throwable ->
                    if (throwable) {
                        logger.error("Failed to push {}: {}", targetRef, throwable.message)
                        throw throwable
                    } else {
                        logger.lifecycle("Successfully pushed: {}", targetRef)
                        return result
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
        // Check for SourceRef Mode first (existing/pre-built images)
        def sourceRefValue = sourceRef.getOrElse("")
        if (!sourceRefValue.isEmpty()) {
            return sourceRefValue
        }

        // Check for effectiveSourceRef if available
        def effectiveSourceRefValue = effectiveSourceRef.getOrElse("")
        if (!effectiveSourceRefValue.isEmpty()) {
            return effectiveSourceRefValue
        }

        // Build Mode (building new images) - use nomenclature properties
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
     * Build target image references for a publish target using inheritance
     */
    List<String> buildTargetImageReferences(PublishTarget target) {
        // Step 1: Get effective source properties
        def sourceProps = getEffectiveSourceProperties()

        // Step 2: Apply target overrides with inheritance
        def effectiveProps = sourceProps.applyTargetOverrides(target)

        // Step 3: Validate we have required properties
        def effectiveTags = effectiveProps.tags
        if (effectiveTags.isEmpty()) {
            // Default to ["latest"] if no tags available
            effectiveTags = ["latest"]
        }

        // Step 4: Build target references with effective properties
        def targetRefs = []

        if (!effectiveProps.repository.isEmpty()) {
            // Using repository format
            def baseRef = effectiveProps.registry.isEmpty() ?
                effectiveProps.repository :
                "${effectiveProps.registry}/${effectiveProps.repository}"
            effectiveTags.each { tag ->
                targetRefs.add("${baseRef}:${tag}")
            }
        } else if (!effectiveProps.imageName.isEmpty()) {
            // Using namespace + imageName format
            def baseRef = ""
            if (!effectiveProps.registry.isEmpty()) {
                baseRef += "${effectiveProps.registry}/"
            }
            if (!effectiveProps.namespace.isEmpty()) {
                baseRef += "${effectiveProps.namespace}/"
            }
            baseRef += effectiveProps.imageName

            effectiveTags.each { tag ->
                targetRefs.add("${baseRef}:${tag}")
            }
        } else {
            // No valid image reference can be built
            logger.warn("Cannot build target image reference: missing imageName and repository")
            return []
        }

        return targetRefs
    }

    /**
     * Get effective source properties for inheritance calculations
     */
    private EffectiveImageProperties getEffectiveSourceProperties() {
        return EffectiveImageProperties.fromTaskProperties(this)
    }


    /**
     * Validate authentication credentials with registry-specific suggestions
     */
    void validateAuthenticationCredentials(PublishTarget target, com.kineticfire.gradle.docker.spec.AuthSpec authSpec) {
        if (authSpec == null) return

        def registryName = getEffectiveRegistry(target)
        def exampleVars = getExampleEnvironmentVariables(registryName)

        // Validate username
        if (authSpec.username.isPresent()) {
            try {
                def username = authSpec.username.get()
                if (username == null || username.trim().isEmpty()) {
                    throw new org.gradle.api.GradleException(
                        "Authentication username is empty for registry '${registryName}' in target '${target.name}'. " +
                        "Ensure your username environment variable contains a valid value."
                    )
                }
            } catch (IllegalStateException e) {
                if (e.message?.contains("environment variable") || e.message?.contains("provider")) {
                    throw new org.gradle.api.GradleException(
                        "Authentication username environment variable is not set for registry '${registryName}' in target '${target.name}'. " +
                        "Ensure your username environment variable is set. ${exampleVars.username}"
                    )
                }
                throw e
            }
        }

        // Validate password/token
        if (authSpec.password.isPresent()) {
            try {
                def password = authSpec.password.get()
                if (password == null || password.trim().isEmpty()) {
                    throw new org.gradle.api.GradleException(
                        "Authentication password/token is empty for registry '${registryName}' in target '${target.name}'. " +
                        "Ensure your password/token environment variable contains a valid value."
                    )
                }
            } catch (IllegalStateException e) {
                if (e.message?.contains("environment variable") || e.message?.contains("provider")) {
                    throw new org.gradle.api.GradleException(
                        "Authentication password/token environment variable is not set for registry '${registryName}' in target '${target.name}'. " +
                        "Ensure your password/token environment variable is set. ${exampleVars.password}"
                    )
                }
                throw e
            }
        }
    }

    /**
     * Get effective registry name from target
     */
    String getEffectiveRegistry(PublishTarget target) {
        def registryValue = target.registry.getOrElse("")
        if (!registryValue.isEmpty()) {
            return registryValue
        }

        def repositoryValue = target.repository.getOrElse("")
        if (repositoryValue.contains("/")) {
            def potentialRegistry = repositoryValue.split("/")[0]
            if (potentialRegistry.contains(".") || potentialRegistry.contains(":")) {
                return potentialRegistry
            }
        }

        return "unknown-registry"
    }

    /**
     * Get example environment variables for different registries
     */
    Map<String, String> getExampleEnvironmentVariables(String registryName) {
        switch (registryName.toLowerCase()) {
            case "docker.io":
                return [
                    username: "Common examples: DOCKERHUB_USERNAME, DOCKER_USERNAME",
                    password: "Common examples: DOCKERHUB_TOKEN, DOCKER_TOKEN"
                ]
            case "ghcr.io":
                return [
                    username: "Common examples: GHCR_USERNAME, GITHUB_USERNAME",
                    password: "Common examples: GHCR_TOKEN, GITHUB_TOKEN"
                ]
            case { it.contains("localhost") }:
                return [
                    username: "Common examples: REGISTRY_USERNAME, LOCAL_USERNAME",
                    password: "Common examples: REGISTRY_PASSWORD, LOCAL_PASSWORD"
                ]
            default:
                return [
                    username: "Common examples: REGISTRY_USERNAME, ${registryName.toUpperCase().replaceAll(/[^A-Z0-9]/, '_')}_USERNAME",
                    password: "Common examples: REGISTRY_TOKEN, ${registryName.toUpperCase().replaceAll(/[^A-Z0-9]/, '_')}_TOKEN"
                ]
        }
    }
    
    private void pullSourceRefIfNeeded() {
        if (pullIfMissing.getOrElse(false)) {
            def sourceRefValue = effectiveSourceRef.getOrElse("")
            if (sourceRefValue && !sourceRefValue.isEmpty()) {
                def authConfig = pullAuth.isPresent() ?
                    pullAuth.get().toAuthConfig() : null

                def service = dockerService.get()
                if (!service.imageExists(sourceRefValue).get()) {
                    service.pullImage(sourceRefValue, authConfig).get()
                }
            }
        }
    }
}