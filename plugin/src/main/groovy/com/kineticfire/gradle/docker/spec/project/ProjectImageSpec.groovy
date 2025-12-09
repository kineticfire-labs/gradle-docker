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

import com.kineticfire.gradle.docker.spec.AuthSpec
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

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
 */
abstract class ProjectImageSpec {

    private final ObjectFactory objectFactory

    @Inject
    ProjectImageSpec(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory

        // Build mode conventions
        dockerfile.convention('src/main/docker/Dockerfile')
        buildArgs.convention([:])
        labels.convention([:])

        // SourceRef mode conventions
        sourceRef.convention('')
        sourceRefRegistry.convention('')
        sourceRefNamespace.convention('')
        sourceRefImageName.convention('')
        sourceRefTag.convention('')
        sourceRefRepository.convention('')
        pullIfMissing.convention(false)

        // Common conventions
        registry.convention('')
        namespace.convention('')
        tags.convention(['latest'])

        // Version property - empty by default, derived from first non-'latest' tag if not set
        version.convention('')
    }

    // === BUILD MODE PROPERTIES ===

    /**
     * Image name (e.g., 'my-app')
     */
    @Input
    @Optional
    abstract Property<String> getName()

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
     */
    @Input
    @Optional
    abstract Property<String> getDockerfile()

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
     * Mutually exclusive with jarFrom.
     */
    @Input
    @Optional
    abstract Property<String> getContextDir()

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
     */
    @Input
    abstract Property<Boolean> getPullIfMissing()

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
               (contextDir.isPresent() && !contextDir.get().isEmpty())
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
}
