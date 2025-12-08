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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input

import javax.inject.Inject

/**
 * Simplified failure operations configuration for dockerProject DSL.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses @Inject for ObjectFactory injection.
 * All properties use Provider API with proper @Input annotations.
 */
abstract class ProjectFailureSpec {

    @Inject
    ProjectFailureSpec(ObjectFactory objectFactory) {
        additionalTags.convention([])
    }

    /**
     * Additional tags to apply when tests fail (e.g., ['failed', 'needs-review'])
     */
    @Input
    abstract ListProperty<String> getAdditionalTags()
}
