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
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.file.ProjectLayout

import javax.inject.Inject

/**
 * Top-level specification for the dockerProject { } simplified DSL.
 *
 * This spec collects simplified configuration and is later translated
 * into docker, dockerTest, and dockerWorkflows configurations.
 *
 * Supports multiple images via the images { } container:
 * <pre>
 * dockerProject {
 *     images {
 *         myApp {
 *             imageName.set('my-app')
 *             jarFrom.set(':app:jar')
 *             primary.set(true)  // receives onSuccess.additionalTags
 *         }
 *         testDb {
 *             imageName.set('test-db')
 *             contextDir.set('src/test/docker/db')
 *         }
 *     }
 *     test { ... }
 *     onSuccess { ... }
 * }
 * </pre>
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: This class uses @Inject for service injection.
 * Gradle's ObjectFactory will inject ObjectFactory, ProviderFactory, and ProjectLayout
 * automatically when using objectFactory.newInstance(DockerProjectSpec).
 */
abstract class DockerProjectSpec {

    private final ObjectFactory objectFactory
    private final ProviderFactory providers
    private final ProjectLayout layout

    // Images container for multi-image support
    private NamedDomainObjectContainer<ProjectImageSpec> imagesContainer

    // Lazy-initialized nested specs to avoid calling .convention() on uninitialized abstract properties
    private ProjectTestSpec testSpec
    private ProjectSuccessSpec successSpec
    private ProjectFailureSpec failureSpec

    @Inject
    DockerProjectSpec(ObjectFactory objectFactory, ProviderFactory providers, ProjectLayout layout) {
        this.objectFactory = objectFactory
        this.providers = providers
        this.layout = layout
    }

    /**
     * Initialize nested specs lazily. This must be called after construction
     * because abstract Property fields are not available in the constructor.
     * Called automatically by DockerProjectExtension after instantiation.
     */
    void initializeNestedSpecs() {
        if (imagesContainer == null) {
            // Capture objectFactory in local variable for closure access
            def factory = this.objectFactory
            imagesContainer = factory.domainObjectContainer(ProjectImageSpec) { String name ->
                def spec = factory.newInstance(ProjectImageSpec)
                spec.setName(name)  // Required for Named interface - used by container.getByName()
                spec.blockName.set(name)  // Also store in property for DSL access
                return spec
            }
        }
        if (testSpec == null) {
            testSpec = objectFactory.newInstance(ProjectTestSpec)
            test.set(testSpec)
        }
        if (successSpec == null) {
            successSpec = objectFactory.newInstance(ProjectSuccessSpec)
            onSuccess.set(successSpec)
        }
        if (failureSpec == null) {
            failureSpec = objectFactory.newInstance(ProjectFailureSpec)
            onFailure.set(failureSpec)
        }
    }

    /**
     * Get the images container for multi-image configuration.
     * Each image is defined with a named block inside images { }.
     *
     * @return The named domain object container of image specs
     */
    NamedDomainObjectContainer<ProjectImageSpec> getImages() {
        initializeNestedSpecs()
        return imagesContainer
    }

    abstract Property<ProjectTestSpec> getTest()
    abstract Property<ProjectSuccessSpec> getOnSuccess()
    abstract Property<ProjectFailureSpec> getOnFailure()

    // === DSL METHODS ===

    /**
     * Configure images using a closure.
     * Each named block inside the closure creates a new image configuration.
     *
     * @param closure Configuration closure for the images container
     */
    void images(@DelegatesTo(NamedDomainObjectContainer) Closure closure) {
        initializeNestedSpecs()
        imagesContainer.configure(closure)
    }

    /**
     * Configure images using an Action.
     *
     * @param action Configuration action for the images container
     */
    void images(Action<NamedDomainObjectContainer<ProjectImageSpec>> action) {
        initializeNestedSpecs()
        action.execute(imagesContainer)
    }

    void test(@DelegatesTo(ProjectTestSpec) Closure closure) {
        initializeNestedSpecs()
        closure.delegate = test.get()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void test(Action<ProjectTestSpec> action) {
        initializeNestedSpecs()
        action.execute(test.get())
    }

    void onSuccess(@DelegatesTo(ProjectSuccessSpec) Closure closure) {
        initializeNestedSpecs()
        closure.delegate = onSuccess.get()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void onSuccess(Action<ProjectSuccessSpec> action) {
        initializeNestedSpecs()
        action.execute(onSuccess.get())
    }

    void onFailure(@DelegatesTo(ProjectFailureSpec) Closure closure) {
        initializeNestedSpecs()
        closure.delegate = onFailure.get()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void onFailure(Action<ProjectFailureSpec> action) {
        initializeNestedSpecs()
        action.execute(onFailure.get())
    }

    /**
     * Check if this spec has been configured (at minimum, one image is present with meaningful values)
     */
    boolean isConfigured() {
        if (imagesContainer == null || imagesContainer.isEmpty()) {
            return false
        }
        // Check if any image has meaningful configuration
        return imagesContainer.any { img ->
            (img.imageName.isPresent() && !img.imageName.get().isEmpty()) ||
            (img.legacyName.isPresent() && !img.legacyName.get().isEmpty()) ||
            (img.sourceRef.isPresent() && !img.sourceRef.get().isEmpty()) ||
            (img.sourceRefImageName.isPresent() && !img.sourceRefImageName.get().isEmpty()) ||
            (img.sourceRefRepository.isPresent() && !img.sourceRefRepository.get().isEmpty()) ||
            (img.jarFrom.isPresent() && !img.jarFrom.get().isEmpty()) ||
            (img.contextDir.isPresent() && !img.contextDir.get().isEmpty()) ||
            (img.repository.isPresent() && !img.repository.get().isEmpty())
        }
    }

    /**
     * Get the primary image from the images container.
     * If only one image is defined, it's automatically considered primary.
     * If multiple images are defined, exactly one must have primary.set(true).
     *
     * @return The primary image spec, or null if no images or no primary designated
     */
    ProjectImageSpec getPrimaryImage() {
        if (imagesContainer == null || imagesContainer.isEmpty()) {
            return null
        }
        // If only one image, it's automatically primary
        if (imagesContainer.size() == 1) {
            return imagesContainer.first()
        }
        // Otherwise, find the one marked primary
        return imagesContainer.find { it.primary.getOrElse(false) }
    }
}
