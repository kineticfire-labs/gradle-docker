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

/**
 * Authentication configuration for Docker registries
 */
class AuthConfig {
    final String username
    final String password
    final String registryToken
    final String serverAddress
    
    AuthConfig(String username = null, String password = null, String registryToken = null, String serverAddress = null) {
        this.username = username
        this.password = password
        this.registryToken = registryToken
        this.serverAddress = serverAddress
    }
    
    /**
     * Check if this config has valid credentials
     */
    boolean hasCredentials() {
        return (username && password) || registryToken
    }
    
    /**
     * Check if this is username/password authentication
     */
    boolean isUsernamePassword() {
        return username && password
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
        
        if (username) authConfig.withUsername(username)
        if (password) authConfig.withPassword(password)
        if (registryToken) authConfig.withRegistrytoken(registryToken)
        if (serverAddress) authConfig.withRegistryAddress(serverAddress)
        
        return authConfig
    }
    
    @Override
    String toString() {
        if (isTokenBased()) {
            return "AuthConfig{token=*****, serverAddress='${serverAddress}'}"
        } else if (isUsernamePassword()) {
            return "AuthConfig{username='${username}', password=*****, serverAddress='${serverAddress}'}"
        } else {
            return "AuthConfig{empty}"
        }
    }
}