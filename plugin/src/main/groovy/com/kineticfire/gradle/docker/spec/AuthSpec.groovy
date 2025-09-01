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

import javax.inject.Inject

/**
 * Specification for Docker registry authentication
 */
abstract class AuthSpec {
    
    private final Project project
    
    @Inject
    AuthSpec(Project project) {
        this.project = project
    }
    
    abstract Property<String> getUsername()
    abstract Property<String> getPassword()
    abstract Property<String> getRegistryToken()
    abstract Property<String> getServerAddress()
    abstract Property<String> getHelper()
    
    /**
     * Convert to model AuthConfig
     */
    AuthConfig toAuthConfig() {
        return new AuthConfig(
            username.orNull,
            password.orNull,
            registryToken.orNull,
            serverAddress.orNull
        )
    }
}