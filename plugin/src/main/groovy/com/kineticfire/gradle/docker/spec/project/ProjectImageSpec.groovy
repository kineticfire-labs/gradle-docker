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

package com.kineticfire.gradle.docker.spec.project

import com.kineticfire.gradle.docker.PullPolicy
import com.kineticfire.gradle.docker.spec.AuthSpec
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskProvider

import javax.inject.Inject

/**
 * Simplified image configuration for dockerProject DSL.
 *
 * Supports two modes:
 * - Build Mode: build image from Dockerfile (name, dockerfile, jarFrom/contextDir)
 * - SourceRef Mode: use existing image (sourceRef or component properties)
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses @Inject for Gradle service injection.
 * All properties use Provider API with proper @Input/@Optional annotations.
 *
 * Implements Named for NamedDomainObjectContainer support.
 */
abstract class ProjectImageSpec implements Named {

    private final ObjectFactory objectFactory
    private String name

    @Inject
    ProjectImageSpec(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory
        this.name = ''  // Will be set by container factory

        // Build mode conventions
        dockerfile.convention('src/main/docker/Dockerfile')
        dockerfileName.convention('')
        jarName.convention('app.jar')
        buildArgs.convention([:])
        labels.convention([:])

        // Context task triple-property pattern conventions
        contextTaskName.convention('')
        contextTaskPath.convention('')

        // SourceRef mode conventions
        sourceRef.convention('')
        sourceRefRegistry.convention('')
        sourceRefNamespace.convention('')
        sourceRefImageName.convention('')
        sourceRefTag.convention('')
        sourceRefRepository.convention('')
        pullIfMissing.convention(false)
        pullPolicy.convention(PullPolicy.NEVER)

        // Common conventions
        registry.convention('')
        namespace.convention('')
        repository.convention('')
        tags.convention(['latest'])

        // Multi-image support
        primary.convention(false)

        // Version property - empty by default, derived from first non-'latest' tag if not set
        version.convention('')
    }

    // === NAMED INTERFACE IMPLEMENTATION ===

    /**
     * Returns the name used by NamedDomainObjectContainer for this image spec.
     * This is the DSL block name (e.g., "myApp" from images { myApp { } }).
     *
     * @return The name of this image spec in the container
     */
    @Override
    String getName() {
        return this.name
    }

    /**
     * Sets the name used by NamedDomainObjectContainer.
     * Called by the container factory when creating instances.
     *
     * @param name The name to set
     */
    void setName(String name) {
        this.name = name
    }

    // === BUILD MODE PROPERTIES ===

    /**
     * Block name from DSL (e.g., "myApp" from images { myApp { } }).
     * Set automatically when image is added to the images container.
     * Used internally for task naming when imageName is not explicitly set.
     */
    @Input
    @Optional
    abstract Property<String> getBlockName()

    /**
     * Image name (e.g., 'my-app').
     * Renamed from 'name' for consistency with docker.images.&lt;name&gt; DSL.
     */
    @Input
    @Optional
    abstract Property<String> getImageName()

    /**
     * Whether this is the primary image in a multi-image configuration.
     * The primary image receives onSuccess.additionalTags.
     * If only one image is defined, it's automatically primary.
     * Default: false
     */
    @Input
    abstract Property<Boolean> getPrimary()

    /**
     * Deprecated: Use imageName instead.
     * @deprecated Renamed to imageName for consistency with docker DSL.
     * Note: Renamed from getName() to getLegacyName() because getName() is now used
     * by the Named interface for NamedDomainObjectContainer support.
     */
    @Deprecated
    @Input
    @Optional
    abstract Property<String> getLegacyName()

    /**
     * Tags to apply to the built image (e.g., ['latest', '1.0.0'])
     */
    @Input
    abstract ListProperty<String> getTags()

    /**
     * Image version (e.g., '1.0.0').
     * If not explicitly set, derived from the first non-'latest' tag.
     * Maps to ImageSpec.version in the underlying docker DSL.
     */
    @Input
    @Optional
    abstract Property<String> getVersion()

    /**
     * Path to Dockerfile relative to project root.
     * Default: 'src/main/docker/Dockerfile'
     * Mutually exclusive with dockerfileName.
     */
    @Input
    @Optional
    abstract Property<String> getDockerfile()

    /**
     * Name of Dockerfile in src/main/docker directory.
     * When set, resolves to 'src/main/docker/{dockerfileName}'.
     * Only applies when jarFrom is used (ignored for contextTask and contextDir).
     * Mutually exclusive with dockerfile path.
     * Default: '' (empty means use dockerfile path directly)
     */
    @Input
    @Optional
    abstract Property<String> getDockerfileName()

    /**
     * Name to use for the JAR file in the build context.
     * Only applies when jarFrom is used.
     * Default: 'app.jar'
     */
    @Input
    abstract Property<String> getJarName()

    /**
     * Task path that produces a JAR to include in context (e.g., ':app:jar').
     * When specified, auto-creates a Copy task that:
     * - Copies the Dockerfile to build context
     * - Copies the JAR as 'app.jar' to build context
     */
    @Input
    @Optional
    abstract Property<String> getJarFrom()

    /**
     * Alternative to jarFrom: specify a directory as build context.
     * Mutually exclusive with jarFrom and contextTask.
     */
    @Input
    @Optional
    abstract Property<String> getContextDir()

    // === CONTEXT TASK TRIPLE-PROPERTY PATTERN ===
    // For configuration cache safety, we store both the TaskProvider (for convenient DSL)
    // and flattened string properties (for serialization)

    /**
     * Task that prepares the build context directory.
     * User-facing DSL property for convenient task assignment.
     * When set, auto-populates contextTaskName and contextTaskPath.
     * Mutually exclusive with jarFrom and contextDir.
     *
     * Note: This property is @Internal and not cached directly.
     * The string properties contextTaskName and contextTaskPath are used for caching.
     */
    @Internal
    TaskProvider<Task> contextTask

    /**
     * Name of the context task (auto-populated from contextTask).
     * Used for configuration cache serialization.
     */
    @Input
    @Optional
    abstract Property<String> getContextTaskName()

    /**
     * Full path of the context task (e.g., ':app-image:prepareContext').
     * Auto-populated from contextTask.
     * Used for configuration cache serialization and task lookup during replay.
     */
    @Input
    @Optional
    abstract Property<String> getContextTaskPath()

    /**
     * Set the context task and auto-populate the string properties.
     *
     * @param taskProvider The task provider that produces the build context
     */
    void contextTask(TaskProvider<Task> taskProvider) {
        this.contextTask = taskProvider
        if (taskProvider != null) {
            // Extract and store the serializable string properties
            contextTaskName.set(taskProvider.name)
            // For full path, we need to get it from the task when available
            taskProvider.configure { task ->
                contextTaskPath.set(task.path)
            }
        }
    }

    /**
     * Build arguments to pass to docker build
     */
    @Input
    abstract MapProperty<String, String> getBuildArgs()

    /**
     * Labels to apply to the built image.
     * Maps to ImageSpec.labels in the underlying docker DSL.
     */
    @Input
    abstract MapProperty<String, String> getLabels()

    // === SOURCE REF MODE PROPERTIES ===

    /**
     * Full source image reference (e.g., 'docker.io/library/nginx:1.25').
     * When specified, skips build and uses this existing image.
     */
    @Input
    @Optional
    abstract Property<String> getSourceRef()

    /**
     * SourceRef component: registry (e.g., 'docker.io')
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefRegistry()

    /**
     * SourceRef component: namespace (e.g., 'library')
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefNamespace()

    /**
     * SourceRef component: image name (e.g., 'nginx')
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefImageName()

    /**
     * SourceRef component: tag (e.g., '1.25')
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefTag()

    /**
     * SourceRef component: repository (e.g., 'library/nginx')
     * Alternative to namespace+imageName
     */
    @Input
    @Optional
    abstract Property<String> getSourceRefRepository()

    /**
     * Whether to pull the image if not present locally.
     * Only applies in sourceRef mode.
     * @deprecated Use pullPolicy instead for more control.
     */
    @Deprecated
    @Input
    abstract Property<Boolean> getPullIfMissing()

    /**
     * Image pull policy for sourceRef mode.
     * Provides type-safe control over when to pull images from registry.
     * Only applicable in Source Reference Mode (not Build Mode).
     * Default: NEVER (fail if image not found locally)
     */
    @Input
    abstract Property<PullPolicy> getPullPolicy()

    // === COMMON PROPERTIES ===

    /**
     * Target registry for publishing (e.g., 'docker.io')
     */
    @Input
    @Optional
    abstract Property<String> getRegistry()

    /**
     * Target namespace for publishing (e.g., 'myorg')
     */
    @Input
    @Optional
    abstract Property<String> getNamespace()

    /**
     * Target repository for publishing (e.g., 'myorg/myapp').
     * Alternative to namespace + imageName.
     * Mutually exclusive with imageName - cannot set both.
     */
    @Input
    @Optional
    abstract Property<String> getRepository()

    /**
     * Authentication for pulling images (sourceRef mode with private registries)
     */
    @Nested
    @Optional
    AuthSpec pullAuth

    // === DSL METHODS ===

    void pullAuth(@DelegatesTo(AuthSpec) Closure closure) {
        if (!pullAuth) {
            pullAuth = objectFactory.newInstance(AuthSpec)
        }
        closure.delegate = pullAuth
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void pullAuth(Action<AuthSpec> action) {
        if (!pullAuth) {
            pullAuth = objectFactory.newInstance(AuthSpec)
        }
        action.execute(pullAuth)
    }

    /**
     * Check if this spec is in sourceRef mode
     */
    boolean isSourceRefMode() {
        return (sourceRef.isPresent() && !sourceRef.get().isEmpty()) ||
               (sourceRefRepository.isPresent() && !sourceRefRepository.get().isEmpty()) ||
               (sourceRefImageName.isPresent() && !sourceRefImageName.get().isEmpty())
    }

    /**
     * Check if this spec is in build mode (has build properties set)
     * Note: This only checks for build properties, not mutual exclusivity with sourceRef mode.
     * The translator is responsible for validating that both modes are not configured simultaneously.
     */
    boolean isBuildMode() {
        return (jarFrom.isPresent() && !jarFrom.get().isEmpty()) ||
               (contextDir.isPresent() && !contextDir.get().isEmpty()) ||
               (contextTaskName.isPresent() && !contextTaskName.get().isEmpty())
    }

    /**
     * Derive version from tags if not explicitly set.
     * Returns the first non-'latest' tag, or empty string if none found.
     */
    String deriveVersion() {
        if (version.isPresent() && !version.get().isEmpty()) {
            return version.get()
        }
        def tagList = tags.getOrElse([])
        def nonLatestTag = tagList.find { it != 'latest' }
        return nonLatestTag ?: ''
    }

    // === DSL HELPER METHODS ===

    /**
     * Add a build argument for the Docker build.
     * Convenience method matching docker DSL API.
     *
     * @param key The build argument name
     * @param value The build argument value
     */
    void buildArg(String key, String value) {
        buildArgs.put(key, value)
    }

    /**
     * Add a label to the built image.
     * Convenience method matching docker DSL API.
     *
     * @param key The label name
     * @param value The label value
     */
    void label(String key, String value) {
        labels.put(key, value)
    }

    /**
     * Configure source reference using component-based DSL.
     * Alternative to setting sourceRef string directly.
     *
     * Example:
     * <pre>
     * sourceRef {
     *     registry = 'docker.io'
     *     namespace = 'library'
     *     name = 'nginx'
     *     tag = 'latest'
     * }
     * </pre>
     *
     * @param closure Configuration closure for SourceRefSpec
     */
    void sourceRef(@DelegatesTo(SourceRefSpec) Closure closure) {
        def spec = new SourceRefSpec()
        closure.delegate = spec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()

        // Compute and store the full reference string
        sourceRef.set(spec.computeReference())

        // Also store component properties for reference
        if (spec.registry) sourceRefRegistry.set(spec.registry)
        if (spec.namespace) sourceRefNamespace.set(spec.namespace)
        if (spec.name) sourceRefImageName.set(spec.name)
        if (spec.tag) sourceRefTag.set(spec.tag)
    }

    /**
     * Get the effective source reference string.
     * Computes from component properties if sourceRef is not explicitly set.
     *
     * @return The full source reference string, or empty string if not configured
     */
    String getEffectiveSourceRef() {
        // If sourceRef is explicitly set, use it
        if (sourceRef.isPresent() && !sourceRef.get().isEmpty()) {
            return sourceRef.get()
        }

        // Otherwise, try to compute from component properties
        def spec = new SourceRefSpec()
        spec.registry = sourceRefRegistry.getOrElse('')
        spec.namespace = sourceRefNamespace.getOrElse('')
        spec.name = sourceRefImageName.getOrElse('')
        spec.tag = sourceRefTag.getOrElse('')

        // If repository is set, use it instead of namespace+name
        if (sourceRefRepository.isPresent() && !sourceRefRepository.get().isEmpty()) {
            spec.repository = sourceRefRepository.get()
        }

        return spec.computeReference()
    }

    // === VALIDATION ===

    /**
     * Validates the image spec configuration for mutual exclusivity rules.
     *
     * Validation rules:
     * 1. imageName and repository are mutually exclusive
     * 2. jarFrom, contextTask, contextDir, and sourceRef mode are mutually exclusive
     * 3. pullPolicy is only valid in Source Reference Mode
     *
     * @throws org.gradle.api.GradleException if validation fails
     */
    void validate() {
        validateImageNameAndRepository()
        validateContextSourceExclusivity()
        validatePullPolicyMode()
    }

    /**
     * Validates that imageName and repository are not both set.
     * These are mutually exclusive ways to specify the image name.
     */
    private void validateImageNameAndRepository() {
        boolean hasImageName = imageName.isPresent() && !imageName.get().isEmpty()
        boolean hasRepository = repository.isPresent() && !repository.get().isEmpty()

        if (hasImageName && hasRepository) {
            throw new org.gradle.api.GradleException(
                "Configuration error in image '${name}': " +
                "'imageName' and 'repository' are mutually exclusive. " +
                "Use 'imageName' for the simple image name (e.g., 'myapp'), or " +
                "'repository' for namespace/imageName format (e.g., 'myorg/myapp'). " +
                "Remove one of these properties."
            )
        }
    }

    /**
     * Validates that only one context/source method is configured.
     * jarFrom, contextTask, contextDir, and sourceRef mode are mutually exclusive.
     */
    private void validateContextSourceExclusivity() {
        List<String> configuredSources = []

        if (jarFrom.isPresent() && !jarFrom.get().isEmpty()) {
            configuredSources << 'jarFrom'
        }
        if (contextTaskName.isPresent() && !contextTaskName.get().isEmpty()) {
            configuredSources << 'contextTask'
        }
        if (contextDir.isPresent() && !contextDir.get().isEmpty()) {
            configuredSources << 'contextDir'
        }
        if (isSourceRefMode()) {
            configuredSources << 'sourceRef (or sourceRef components)'
        }

        if (configuredSources.size() > 1) {
            throw new org.gradle.api.GradleException(
                "Configuration error in image '${name}': " +
                "Multiple source/context methods configured: ${configuredSources.join(', ')}. " +
                "These are mutually exclusive. Choose exactly one: " +
                "'jarFrom' (copy JAR to context), " +
                "'contextTask' (use task output as context), " +
                "'contextDir' (use directory as context), or " +
                "'sourceRef' (use existing image, skip build). " +
                "Remove all but one of these properties."
            )
        }
    }

    /**
     * Validates that pullPolicy is only configured in Source Reference Mode.
     * pullPolicy has no effect in Build Mode since the image is built locally.
     */
    private void validatePullPolicyMode() {
        // Check if pullPolicy has been explicitly changed from the default NEVER
        boolean hasPullPolicy = pullPolicy.isPresent() && pullPolicy.get() != PullPolicy.NEVER

        if (hasPullPolicy && !isSourceRefMode()) {
            throw new org.gradle.api.GradleException(
                "Configuration error in image '${name}': " +
                "'pullPolicy' is only applicable in Source Reference Mode. " +
                "Currently configured as '${pullPolicy.get()}' but no sourceRef is configured. " +
                "Either remove 'pullPolicy' or configure a 'sourceRef' to use an existing image."
            )
        }
    }

    /**
     * Inner class for component-based source reference configuration.
     */
    static class SourceRefSpec {
        String registry = ''
        String namespace = ''
        String name = ''
        String tag = ''
        String repository = ''

        /**
         * Compute the full reference string from component properties.
         *
         * @return The computed reference string (e.g., 'docker.io/library/nginx:latest')
         */
        String computeReference() {
            StringBuilder ref = new StringBuilder()

            // Add registry if present
            if (registry) {
                ref.append(registry).append('/')
            }

            // Add repository or namespace/name
            if (repository) {
                ref.append(repository)
            } else {
                if (namespace) {
                    ref.append(namespace).append('/')
                }
                if (name) {
                    ref.append(name)
                }
            }

            // Add tag if present
            if (tag && ref.length() > 0) {
                ref.append(':').append(tag)
            }

            return ref.toString()
        }
    }
}
