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

package com.kineticfire.gradle.docker.model

import java.util.Objects

/**
 * Authentication configuration for Docker registries
 */
class AuthConfig {
    final String username
    final String password
    final String registryToken
    
    AuthConfig(String username = null, String password = null, String registryToken = null) {
        this.username = username
        this.password = password
        this.registryToken = registryToken
    }
    
    /**
     * Check if this config has valid credentials
     */
    boolean hasCredentials() {
        // Valid username/password: both non-null and both non-empty, OR both empty strings (anonymous)
        def validUsernamePassword = (username != null && password != null) &&
                                   ((username.isEmpty() && password.isEmpty()) ||
                                    (!username.isEmpty() && !password.isEmpty()))
        // Valid token: non-null (can be empty string)
        def validToken = (registryToken != null)

        return validUsernamePassword || validToken
    }

    /**
     * Check if this is username/password authentication
     */
    boolean isUsernamePassword() {
        return (username != null && password != null) &&
               ((username.isEmpty() && password.isEmpty()) ||
                (!username.isEmpty() && !password.isEmpty()))
    }
    
    /**
     * Check if this is token-based authentication
     */
    boolean isTokenBased() {
        return registryToken != null
    }
    
    /**
     * Convert to Docker Java Client AuthConfig
     */
    com.github.dockerjava.api.model.AuthConfig toDockerJavaAuthConfig() {
        def authConfig = new com.github.dockerjava.api.model.AuthConfig()

        if (username != null) authConfig.withUsername(username)
        if (password != null) authConfig.withPassword(password)
        if (registryToken != null) authConfig.withRegistrytoken(registryToken)
        // serverAddress removed - extracted automatically from image reference

        return authConfig
    }
    
    @Override
    String toString() {
        if (isTokenBased()) {
            // Token-based auth takes priority
            return "AuthConfig{token=*****}"
        } else if (isUsernamePassword()) {
            return "AuthConfig{username='${username}', password=*****}"
        } else {
            return "AuthConfig{empty}"
        }
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) return true
        if (!(obj instanceof AuthConfig)) return false

        AuthConfig other = (AuthConfig) obj
        return Objects.equals(username, other.username) &&
               Objects.equals(password, other.password) &&
               Objects.equals(registryToken, other.registryToken)
        // serverAddress removed from comparison - extracted automatically from image reference
    }

    @Override
    int hashCode() {
        return Objects.hash(username, password, registryToken)
        // serverAddress removed from hash - extracted automatically from image reference
    }
}