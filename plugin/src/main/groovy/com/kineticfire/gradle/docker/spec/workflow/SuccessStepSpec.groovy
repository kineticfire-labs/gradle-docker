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

package com.kineticfire.gradle.docker.spec.workflow

import com.kineticfire.gradle.docker.spec.PublishSpec
import com.kineticfire.gradle.docker.spec.SaveSpec
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * Specification for operations to perform when tests pass
 *
 * Defines additional tags, save, and publish operations for successful builds.
 */
abstract class SuccessStepSpec {

    private final ObjectFactory objectFactory
    private final ProjectLayout layout

    @Inject
    SuccessStepSpec(ObjectFactory objectFactory, ProjectLayout layout) {
        this.objectFactory = objectFactory
        this.layout = layout

        additionalTags.convention([])
    }

    /**
     * Additional tags to apply to the image after tests pass
     */
    abstract ListProperty<String> getAdditionalTags()

    /**
     * Save configuration for persisting the image to a tar file
     */
    abstract Property<SaveSpec> getSave()

    /**
     * Publish configuration for pushing the image to registries
     */
    abstract Property<PublishSpec> getPublish()

    /**
     * Hook executed after all success operations complete
     */
    abstract Property<Action<Void>> getAfterSuccess()

    /**
     * Configure save operation using a closure
     *
     * @param closure Configuration closure for SaveSpec
     */
    void save(@DelegatesTo(SaveSpec) Closure closure) {
        def saveSpec = objectFactory.newInstance(SaveSpec, objectFactory, layout)
        closure.delegate = saveSpec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        save.set(saveSpec)
    }

    /**
     * Configure save operation using an action
     *
     * @param action Configuration action for SaveSpec
     */
    void save(Action<SaveSpec> action) {
        def saveSpec = objectFactory.newInstance(SaveSpec, objectFactory, layout)
        action.execute(saveSpec)
        save.set(saveSpec)
    }

    /**
     * Configure publish operation using a closure
     *
     * @param closure Configuration closure for PublishSpec
     */
    void publish(@DelegatesTo(PublishSpec) Closure closure) {
        def publishSpec = objectFactory.newInstance(PublishSpec, objectFactory)
        closure.delegate = publishSpec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        publish.set(publishSpec)
    }

    /**
     * Configure publish operation using an action
     *
     * @param action Configuration action for PublishSpec
     */
    void publish(Action<PublishSpec> action) {
        def publishSpec = objectFactory.newInstance(PublishSpec, objectFactory)
        action.execute(publishSpec)
        publish.set(publishSpec)
    }
}
