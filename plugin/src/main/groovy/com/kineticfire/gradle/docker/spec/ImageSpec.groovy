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

package com.kineticfire.gradle.docker.spec

import org.gradle.api.*
import org.gradle.api.model.ObjectFactory
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Copy

import javax.inject.Inject

/**
 * Specification for a Docker image configuration using Gradle 9 Provider API
 */
abstract class ImageSpec {
    
    private final String name
    private final ObjectFactory objectFactory
    private final ProviderFactory providers
    private final ProjectLayout layout
    private final Project project
    
    @Inject
    ImageSpec(String name, Project project) {
        this.name = name
        this.objectFactory = project.objects
        this.providers = project.providers
        this.layout = project.layout
        this.project = project
        
        // Set conventions for new nomenclature properties
        registry.convention("")
        namespace.convention("")
        repository.convention("")
        version.convention(providers.provider { project.version.toString() })
        // tags.convention(["latest"]) // No default - tags must be explicitly specified
        buildArgs.convention([:])
        labels.convention([:])
        sourceRef.convention("")
        
        // pullIfMissing conventions
        pullIfMissing.convention(false)
        sourceRefRegistry.convention("")
        sourceRefNamespace.convention("")
        sourceRefImageName.convention("")
        sourceRefTag.convention("")
        sourceRefRepository.convention("")
        
        // Set context convention
        context.convention(layout.projectDirectory.dir("src/main/docker"))
    }
    
    String getName() { 
        return name 
    }
    
    // Docker Image Nomenclature Properties (NEW)
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
    
    // Build Configuration Properties
    @Input
    @Optional
    abstract DirectoryProperty getContext()
    
    @Internal
    TaskProvider<Task> contextTask
    
    @Input
    @Optional
    abstract RegularFileProperty getDockerfile()
    
    @Input
    @Optional
    abstract Property<String> getDockerfileName()
    
    @Input
    abstract MapProperty<String, String> getBuildArgs()
    
    @Input
    @Optional
    abstract Property<String> getSourceRef()
    
    // pullIfMissing support properties
    @Input
    abstract Property<Boolean> getPullIfMissing()
    
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
    abstract Property<String> getSourceRefTag()
    
    @Input
    @Optional
    abstract Property<String> getSourceRefRepository()
    
    @Nested
    @Optional
    AuthSpec pullAuth
    
    // Nested Specifications
    @Nested
    @Optional
    abstract Property<SaveSpec> getSave()
    
    @Nested
    @Optional
    abstract Property<PublishSpec> getPublish()
    
    // DSL helper methods for labels (NEW FEATURE)
    void label(String key, String value) {
        if (value != null) {
            labels.put(key, value)
        }
    }
    
    void label(String key, Provider<String> value) {
        if (value != null) {
            labels.put(key, value)
        }
    }
    
    void labels(Map<String, String> labelMap) {
        if (labelMap != null) {
            labels.putAll(labelMap)
        }
    }
    
    void labels(Provider<? extends Map<String, String>> labelMapProvider) {
        if (labelMapProvider != null) {
            labels.putAll(labelMapProvider)
        }
    }
    
    // DSL helper methods for build args
    void buildArg(String key, String value) {
        if (value != null) {
            buildArgs.put(key, value)
        }
    }
    
    void buildArg(String key, Provider<String> value) {
        if (value != null) {
            buildArgs.put(key, value)
        }
    }
    
    void buildArgs(Map<String, String> argMap) {
        if (argMap != null) {
            buildArgs.putAll(argMap)
        }
    }
    
    void buildArgs(Provider<? extends Map<String, String>> argMapProvider) {
        if (argMapProvider != null) {
            buildArgs.putAll(argMapProvider)
        }
    }
    
    // DSL methods for nested configuration
    void save(@DelegatesTo(SaveSpec) Closure closure) {
        def saveSpec = objectFactory.newInstance(SaveSpec, objectFactory, layout)
        closure.delegate = saveSpec
        closure.call()
        save.set(saveSpec)
    }
    
    void save(Action<SaveSpec> action) {
        def saveSpec = objectFactory.newInstance(SaveSpec, objectFactory, layout)
        action.execute(saveSpec)
        save.set(saveSpec)
    }
    
    void publish(@DelegatesTo(PublishSpec) Closure closure) {
        def publishSpec = objectFactory.newInstance(PublishSpec, objectFactory)
        closure.delegate = publishSpec
        closure.call()
        publish.set(publishSpec)
    }
    
    void publish(Action<PublishSpec> action) {
        def publishSpec = objectFactory.newInstance(PublishSpec, objectFactory)
        action.execute(publishSpec)
        publish.set(publishSpec)
    }
    
    /**
     * Configure inline context using Copy task DSL
     * Creates an anonymous Copy task to prepare build context
     */
    void context(@DelegatesTo(Copy) Closure closure) {
        // For testing purposes, create a basic copy task-like configuration
        // In real plugin usage, this would be handled during plugin configuration
        def copyTask = project.tasks.register("prepare${name.capitalize()}Context", Copy) { task ->
            task.group = 'docker'
            task.description = "Prepare build context for Docker image"
            closure.delegate = task
            closure.call()
        }
        this.contextTask = copyTask
    }
    
    /**
     * Configure inline context using Copy task Action
     */
    void context(Action<Copy> action) {
        // For testing purposes, create a basic copy task-like configuration  
        // In real plugin usage, this would be handled during plugin configuration
        def copyTask = project.tasks.register("prepare${name.capitalize()}Context", Copy) { task ->
            task.group = 'docker'
            task.description = "Prepare build context for Docker image"
            action.execute(task)
        }
        this.contextTask = copyTask
    }
    
    // DSL methods for pullAuth configuration
    void pullAuth(@DelegatesTo(AuthSpec) Closure closure) {
        if (!pullAuth) {
            pullAuth = objectFactory.newInstance(AuthSpec)
        }
        closure.delegate = pullAuth
        closure.call()
    }
    
    void pullAuth(Action<AuthSpec> action) {
        if (!pullAuth) {
            pullAuth = objectFactory.newInstance(AuthSpec)
        }
        action.execute(pullAuth)
    }
    
    // SourceRef builder methods
    void sourceRef(String registry, String namespace, String imageName, String tag) {
        sourceRefRegistry.set(registry)
        sourceRefNamespace.set(namespace) 
        sourceRefImageName.set(imageName)
        sourceRefTag.set(tag)
    }
    
    void sourceRef(@DelegatesTo(SourceRefSpec) Closure closure) {
        def sourceRefSpec = new SourceRefSpec(this)
        closure.delegate = sourceRefSpec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }
    
    // Helper method to get effective sourceRef (either direct or assembled from components)
    String getEffectiveSourceRef() {
        if (sourceRef.isPresent() && !sourceRef.get().isEmpty()) {
            return sourceRef.get()
        }

        // Assemble from components
        def registry = sourceRefRegistry.getOrElse("")
        def namespace = sourceRefNamespace.getOrElse("")
        def repository = sourceRefRepository.getOrElse("")
        def imageName = sourceRefImageName.getOrElse("")
        def tag = sourceRefTag.getOrElse("")

        // If tag is empty, default to "latest"
        if (tag.isEmpty()) {
            tag = "latest"
        }

        // Repository approach takes precedence (mirrors build mode logic)
        if (!repository.isEmpty()) {
            def baseRef = registry.isEmpty() ? repository : "${registry}/${repository}"
            return "${baseRef}:${tag}"
        }

        // Fall back to namespace + imageName approach
        if (imageName.isEmpty()) {
            throw new GradleException("Either sourceRef, sourceRefRepository, or sourceRefImageName must be specified for image '${name}'")
        }

        def reference = ""
        if (!registry.isEmpty()) {
            reference += registry + "/"
        }
        if (!namespace.isEmpty()) {
            reference += namespace + "/"
        }
        reference += imageName
        // Always add tag - it defaults to "latest" if empty
        reference += ":" + tag

        return reference
    }
    
    // Validation methods
    void validatePullIfMissingConfiguration() {
        if (pullIfMissing.getOrElse(false) && hasBuildContext()) {
            throw new GradleException(
                "Cannot set pullIfMissing=true when build context is configured for image '${name}'. " +
                "Either build the image (remove pullIfMissing) or reference an existing image (use sourceRef instead of build context)."
            )
        }
    }
    
    private boolean hasBuildContext() {
        return contextTask != null ||
               (context.isPresent() && context.get().asFile.exists())
    }
    
    void validateSourceRefConfiguration() {
        def hasDirectSourceRef = sourceRef.isPresent() && !sourceRef.get().isEmpty()
        def hasRepositorySourceRef = sourceRefRepository.isPresent() && !sourceRefRepository.get().isEmpty()
        def hasImageNameSourceRef = sourceRefImageName.isPresent() && !sourceRefImageName.get().isEmpty()

        if (!hasDirectSourceRef && !hasRepositorySourceRef && !hasImageNameSourceRef && pullIfMissing.getOrElse(false)) {
            throw new GradleException(
                "pullIfMissing=true requires either sourceRef, sourceRefRepository, or sourceRefImageName to be specified for image '${name}'"
            )
        }
    }

    void validateModeConsistency() {
        // Detect build mode properties (only consider properties that are definitely build-mode)
        // Avoid false positives from property sharing with SourceRef components and conventions
        def hasExplicitContext = false
        if (context.isPresent()) {
            def contextPath = context.get().asFile.path
            def conventionPath = "src/main/docker"
            // Consider it explicit if path doesn't end with the convention path
            hasExplicitContext = !contextPath.endsWith(conventionPath)
        }

        def hasBuildMode = contextTask != null ||
            (hasExplicitContext && context.get().asFile.exists()) ||
            !buildArgs.get().isEmpty() ||
            !labels.get().isEmpty()

        // Only consider traditional naming properties as build mode if SourceRef components are NOT used
        def hasSourceRefComponents = !sourceRefRegistry.getOrElse("").isEmpty() ||
            !sourceRefNamespace.getOrElse("").isEmpty() ||
            !sourceRefImageName.getOrElse("").isEmpty() ||
            !sourceRefRepository.getOrElse("").isEmpty() ||
            !sourceRefTag.getOrElse("").isEmpty()

        if (!hasSourceRefComponents) {
            // Only count traditional properties as build mode if no SourceRef components are present
            hasBuildMode = hasBuildMode ||
                !registry.getOrElse("").isEmpty() ||
                !namespace.getOrElse("").isEmpty() ||
                !imageName.getOrElse("").isEmpty() ||
                !repository.getOrElse("").isEmpty()
        }

        // Detect sourceRef mode properties
        def hasDirectSourceRef = !sourceRef.getOrElse("").isEmpty()
        // hasSourceRefComponents is already defined above

        // Rule 2a: Build mode excludes all sourceRef properties
        if (hasBuildMode && (hasDirectSourceRef || hasSourceRefComponents)) {
            throw new GradleException(
                "Cannot mix Build Mode and SourceRef Mode"
            )
        }

        // Rule 2b: Direct sourceRef excludes component assembly and build mode
        if (hasDirectSourceRef && (hasSourceRefComponents || hasBuildMode)) {
            throw new GradleException(
                "Cannot use direct sourceRef with component assembly properties or build mode properties for image '${name}'. " +
                "Use sourceRef alone for complete image references."
            )
        }

        // Rule 2c & 2d: Component assembly mutual exclusions
        def hasNamespaceMode = !sourceRefNamespace.getOrElse("").isEmpty() || !sourceRefImageName.getOrElse("").isEmpty()
        def hasRepositoryMode = !sourceRefRepository.getOrElse("").isEmpty()

        if (hasNamespaceMode && hasRepositoryMode) {
            throw new GradleException(
                "Cannot use both repository approach and namespace+imageName approach"
            )
        }

        // Ensure repository mode doesn't mix with namespace/imageName
        if (hasRepositoryMode && (hasNamespaceMode || hasBuildMode)) {
            throw new GradleException(
                "When using sourceRefRepository, cannot use sourceRef, sourceRefNamespace, sourceRefImageName, or build mode properties " +
                "for image '${name}'. Repository mode should use: sourceRefRegistry + sourceRefRepository + sourceRefTag only."
            )
        }

        // Validate incomplete namespace approach
        def hasOnlyNamespace = !sourceRefNamespace.getOrElse("").isEmpty() && sourceRefImageName.getOrElse("").isEmpty()
        def hasOnlyImageName = sourceRefNamespace.getOrElse("").isEmpty() && !sourceRefImageName.getOrElse("").isEmpty()
        
        if (hasOnlyNamespace || hasOnlyImageName) {
            throw new GradleException(
                "When using namespace+imageName approach, both namespace and imageName are required for image '${name}'. " +
                "Either specify both sourceRefNamespace and sourceRefImageName, or use sourceRefRepository instead."
            )
        }
    }

/**
 * DSL specification for sourceRef component configuration
 */
class SourceRefSpec {
    private final ImageSpec parent

    SourceRefSpec(ImageSpec parent) {
        this.parent = parent
    }

    void registry(String registry) {
        parent.sourceRefRegistry.set(registry)
    }

    void namespace(String namespace) {
        parent.sourceRefNamespace.set(namespace)
    }

    void imageName(String imageName) {
        parent.sourceRefImageName.set(imageName)
    }

    void tag(String tag) {
        parent.sourceRefTag.set(tag)
    }

    void repository(String repository) {
        parent.sourceRefRepository.set(repository)
    }
}
}