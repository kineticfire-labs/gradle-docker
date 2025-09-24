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

package com.kineticfire.gradle.docker.spec

import com.kineticfire.gradle.docker.model.AuthConfig
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject

/**
 * Specification for Docker registry authentication
 */
abstract class AuthSpec {
    
    @Inject
    AuthSpec() {
    }
    
    @Input
    @Optional
    abstract Property<String> getUsername()
    
    @Input
    @Optional
    abstract Property<String> getPassword()
    
    @Input
    @Optional
    abstract Property<String> getRegistryToken()
    
    @Input
    @Optional
    abstract Property<String> getHelper()

    /**
     * Convert to model AuthConfig
     */
    AuthConfig toAuthConfig() {
        return new AuthConfig(
            username.orNull,
            password.orNull,
            registryToken.orNull,
            null  // serverAddress removed - extracted automatically from image reference
        )
    }
}