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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject

/**
 * Authentication configuration for publish targets.
 *
 * Provides username and password credentials for Docker registry authentication
 * when publishing images.
 *
 * Example:
 * <pre>
 * publish {
 *     dockerHub {
 *         registry = 'docker.io'
 *         auth {
 *             username = project.findProperty('dockerUsername') ?: ''
 *             password = project.findProperty('dockerPassword') ?: ''
 *         }
 *     }
 * }
 * </pre>
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses @Inject for ObjectFactory injection.
 * All properties use Provider API with proper @Input/@Optional annotations.
 */
abstract class PublishTargetAuthSpec {

    @Inject
    PublishTargetAuthSpec(ObjectFactory objectFactory) {
        username.convention('')
        password.convention('')
    }

    /**
     * Username for registry authentication.
     * Can be set from environment variables or project properties for security.
     */
    @Input
    @Optional
    abstract Property<String> getUsername()

    /**
     * Password or token for registry authentication.
     * Can be set from environment variables or project properties for security.
     *
     * Note: For security, prefer using tokens over passwords where supported.
     */
    @Input
    @Optional
    abstract Property<String> getPassword()

    /**
     * Check if authentication is configured.
     *
     * @return true if both username and password are set and non-empty
     */
    boolean isConfigured() {
        return username.isPresent() && !username.get().isEmpty() &&
               password.isPresent() && !password.get().isEmpty()
    }
}
