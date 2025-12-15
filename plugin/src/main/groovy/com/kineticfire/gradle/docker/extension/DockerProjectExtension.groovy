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

package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.spec.project.DockerProjectSpec
import com.kineticfire.gradle.docker.spec.project.ProjectImageSpec
import com.kineticfire.gradle.docker.spec.project.ProjectTestSpec
import com.kineticfire.gradle.docker.spec.project.ProjectSuccessSpec
import com.kineticfire.gradle.docker.spec.project.ProjectFailureSpec
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

/**
 * Extension providing the dockerProject { } simplified DSL.
 *
 * This extension provides a high-level facade that internally translates
 * to docker, dockerTest, and dockerWorkflows configurations.
 *
 * Example usage:
 * <pre>
 * dockerProject {
 *     images {
 *         myApp {
 *             imageName.set('my-app')
 *             tags.set(['latest', '1.0.0'])
 *             jarFrom.set(':app:jar')
 *             primary.set(true)  // receives onSuccess.additionalTags
 *         }
 *         testDb {
 *             imageName.set('test-db')
 *             contextDir.set('src/test/docker/db')
 *         }
 *     }
 *     test {
 *         compose.set('src/integrationTest/resources/compose/app.yml')
 *         waitForHealthy.set(['app', 'db'])
 *     }
 *     onSuccess {
 *         additionalTags.set(['tested'])
 *     }
 * }
 * </pre>
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses ObjectFactory.newInstance() for spec creation,
 * allowing Gradle to inject services automatically. The nested specs are initialized
 * lazily via initializeNestedSpecs() to work with abstract Property fields.
 */
abstract class DockerProjectExtension {

    private final DockerProjectSpec spec

    @Inject
    DockerProjectExtension(ObjectFactory objectFactory) {
        // Let Gradle inject services into DockerProjectSpec via ObjectFactory
        this.spec = objectFactory.newInstance(DockerProjectSpec)
        // Initialize nested specs after the spec is created
        this.spec.initializeNestedSpecs()
    }

    DockerProjectSpec getSpec() {
        return spec
    }

    /**
     * Get the images container for multi-image configuration.
     *
     * @return The named domain object container of image specs
     */
    NamedDomainObjectContainer<ProjectImageSpec> getImages() {
        return spec.images
    }

    // DSL methods with Closure support (Groovy DSL compatibility)

    /**
     * Configure images using a closure.
     * Each named block inside the closure creates a new image configuration.
     *
     * @param closure Configuration closure for the images container
     */
    void images(@DelegatesTo(NamedDomainObjectContainer) Closure closure) {
        spec.images(closure)
    }

    /**
     * Configure images using an Action.
     *
     * @param action Configuration action for the images container
     */
    void images(Action<NamedDomainObjectContainer<ProjectImageSpec>> action) {
        spec.images(action)
    }

    void test(@DelegatesTo(ProjectTestSpec) Closure closure) {
        spec.test(closure)
    }

    void test(Action<ProjectTestSpec> action) {
        spec.test(action)
    }

    void onSuccess(@DelegatesTo(ProjectSuccessSpec) Closure closure) {
        spec.onSuccess(closure)
    }

    void onSuccess(Action<ProjectSuccessSpec> action) {
        spec.onSuccess(action)
    }

    void onFailure(@DelegatesTo(ProjectFailureSpec) Closure closure) {
        spec.onFailure(closure)
    }

    void onFailure(Action<ProjectFailureSpec> action) {
        spec.onFailure(action)
    }
}
