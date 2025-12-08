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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.file.ProjectLayout

import javax.inject.Inject

/**
 * Top-level specification for the dockerProject { } simplified DSL.
 *
 * This spec collects simplified configuration and is later translated
 * into docker, dockerOrch, and dockerWorkflows configurations.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: This class uses @Inject for service injection.
 * Gradle's ObjectFactory will inject ObjectFactory, ProviderFactory, and ProjectLayout
 * automatically when using objectFactory.newInstance(DockerProjectSpec).
 */
abstract class DockerProjectSpec {

    private final ObjectFactory objectFactory
    private final ProviderFactory providers
    private final ProjectLayout layout

    // Lazy-initialized nested specs to avoid calling .convention() on uninitialized abstract properties
    private ProjectImageSpec imageSpec
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
        if (imageSpec == null) {
            imageSpec = objectFactory.newInstance(ProjectImageSpec)
            image.set(imageSpec)
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

    abstract Property<ProjectImageSpec> getImage()
    abstract Property<ProjectTestSpec> getTest()
    abstract Property<ProjectSuccessSpec> getOnSuccess()
    abstract Property<ProjectFailureSpec> getOnFailure()

    // DSL methods with Closure support (Groovy DSL compatibility)
    void image(@DelegatesTo(ProjectImageSpec) Closure closure) {
        initializeNestedSpecs()
        closure.delegate = image.get()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }

    void image(Action<ProjectImageSpec> action) {
        initializeNestedSpecs()
        action.execute(image.get())
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
     * Check if this spec has been configured (at minimum, image block is present with meaningful values)
     */
    boolean isConfigured() {
        if (!image.isPresent()) {
            return false
        }
        def img = image.get()
        // Check if any meaningful configuration is present (non-empty values)
        return (img.name.isPresent() && !img.name.get().isEmpty()) ||
               (img.sourceRef.isPresent() && !img.sourceRef.get().isEmpty()) ||
               (img.sourceRefImageName.isPresent() && !img.sourceRefImageName.get().isEmpty()) ||
               (img.sourceRefRepository.isPresent() && !img.sourceRefRepository.get().isEmpty()) ||
               (img.jarFrom.isPresent() && !img.jarFrom.get().isEmpty()) ||
               (img.contextDir.isPresent() && !img.contextDir.get().isEmpty())
    }
}
