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

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

import javax.inject.Inject

/**
 * Named publish target configuration for dockerProject DSL.
 *
 * Allows defining multiple publish targets with different registries,
 * namespaces, and authentication. Each target generates a separate
 * publish task named dockerProjectPublish{TargetName}.
 *
 * Example:
 * <pre>
 * dockerProject {
 *     onSuccess {
 *         publish {
 *             dockerHub {
 *                 registry = 'docker.io'
 *                 namespace = 'myorg'
 *                 tags = ['latest', '1.0.0']
 *                 auth {
 *                     username = project.findProperty('dockerUsername') ?: ''
 *                     password = project.findProperty('dockerPassword') ?: ''
 *                 }
 *             }
 *             privateRegistry {
 *                 registry = 'registry.example.com'
 *                 namespace = 'myproject'
 *                 tags = ['latest']
 *                 auth {
 *                     username = System.getenv('REGISTRY_USER') ?: ''
 *                     password = System.getenv('REGISTRY_TOKEN') ?: ''
 *                 }
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Implements Named interface for NamedDomainObjectContainer.
 * Uses @Inject for ObjectFactory injection.
 * All properties use Provider API with proper @Input/@Optional annotations.
 */
abstract class PublishTargetSpec implements Named {

    private final String name
    private final ObjectFactory objectFactory

    @Inject
    PublishTargetSpec(String name, ObjectFactory objectFactory) {
        this.name = name
        this.objectFactory = objectFactory
        registry.convention('')
        namespace.convention('')
        repository.convention('')
        tags.convention([])
    }

    /**
     * The name of this publish target (used as part of task name).
     * Auto-generates publish task named 'dockerProjectPublish{Name}'.
     */
    @Override
    @Input
    String getName() {
        return name
    }

    /**
     * Target registry for publishing (e.g., 'docker.io', 'registry.example.com').
     */
    @Input
    @Optional
    abstract Property<String> getRegistry()

    /**
     * Target namespace for publishing (e.g., 'myorg', 'library').
     */
    @Input
    @Optional
    abstract Property<String> getNamespace()

    /**
     * Target repository for publishing (e.g., 'myorg/myapp').
     * Alternative to namespace - use one or the other.
     */
    @Input
    @Optional
    abstract Property<String> getRepository()

    /**
     * Tags to publish to this target.
     * If empty, uses the image's configured tags.
     */
    @Input
    abstract ListProperty<String> getTags()

    /**
     * Authentication credentials for this publish target.
     */
    @Nested
    @Optional
    PublishTargetAuthSpec auth

    // === DSL METHODS ===

    /**
     * Configure authentication using a closure.
     *
     * @param closure Configuration closure for PublishTargetAuthSpec
     */
    void auth(@DelegatesTo(PublishTargetAuthSpec) Closure closure) {
        if (!auth) {
            auth = objectFactory.newInstance(PublishTargetAuthSpec)
        }
        closure.delegate = auth
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call(auth)  // Pass auth to support both delegate and parameter styles
    }

    /**
     * Configure authentication using an action.
     *
     * @param action Configuration action for PublishTargetAuthSpec
     */
    void auth(Action<PublishTargetAuthSpec> action) {
        if (!auth) {
            auth = objectFactory.newInstance(PublishTargetAuthSpec)
        }
        action.execute(auth)
    }

    /**
     * Generate the publish task name for this target.
     * Format: dockerProjectPublish{TargetName}
     *
     * @return The generated publish task name
     */
    String getPublishTaskName() {
        return "dockerProjectPublish${name.capitalize()}"
    }

    /**
     * Check if this publish target is configured with at least a registry.
     *
     * @return true if registry is set and non-empty
     */
    boolean isConfigured() {
        return registry.isPresent() && !registry.get().isEmpty()
    }
}
