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

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * Specification for operations to perform when tests fail
 *
 * Defines failure tags, log collection, and other failure-handling operations.
 */
abstract class FailureStepSpec {

    private final ObjectFactory objectFactory

    @Inject
    FailureStepSpec(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory

        additionalTags.convention([])
        includeServices.convention([])
    }

    /**
     * Additional tags to apply to the image when tests fail (e.g., 'failed', 'debug')
     */
    abstract ListProperty<String> getAdditionalTags()

    /**
     * Directory where failure logs should be saved
     */
    abstract DirectoryProperty getSaveFailureLogsDir()

    /**
     * List of service names from which to collect logs
     */
    abstract ListProperty<String> getIncludeServices()

    /**
     * Hook executed after all failure operations complete
     */
    abstract Property<Action<Void>> getAfterFailure()
}
