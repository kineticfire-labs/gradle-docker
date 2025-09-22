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

import spock.lang.Specification

/**
 * Unit tests for AuthConfig
 */
class AuthConfigTest extends Specification {

    def "can create empty AuthConfig"() {
        when:
        def auth = new AuthConfig()

        then:
        auth.username == null
        auth.password == null
        auth.registryToken == null
        auth.serverAddress == null
        !auth.hasCredentials()
        !auth.isUsernamePassword()
        !auth.isTokenBased()
    }

    def "can create username/password AuthConfig"() {
        when:
        def auth = new AuthConfig("testuser", "testpass", null, "registry.example.com")

        then:
        auth.username == "testuser"
        auth.password == "testpass"
        auth.registryToken == null
        auth.serverAddress == "registry.example.com"
        auth.hasCredentials()
        auth.isUsernamePassword()
        !auth.isTokenBased()
    }

    def "can create token-based AuthConfig"() {
        when:
        def auth = new AuthConfig(null, null, "ghp_abcd1234", "ghcr.io")

        then:
        auth.username == null
        auth.password == null
        auth.registryToken == "ghp_abcd1234"
        auth.serverAddress == "ghcr.io"
        auth.hasCredentials()
        !auth.isUsernamePassword()
        auth.isTokenBased()
    }

    def "can create minimal username/password config"() {
        when:
        def auth = new AuthConfig("user", "pass")

        then:
        auth.username == "user"
        auth.password == "pass"
        auth.serverAddress == null
        auth.hasCredentials()
        auth.isUsernamePassword()
    }

    def "can create minimal token config"() {
        when:
        def auth = new AuthConfig(null, null, "token123")

        then:
        auth.registryToken == "token123"
        auth.serverAddress == null
        auth.hasCredentials()
        auth.isTokenBased()
    }

    def "hasCredentials returns false for incomplete username/password"() {
        expect:
        !new AuthConfig("user", null).hasCredentials()
        !new AuthConfig(null, "pass").hasCredentials()
        !new AuthConfig("", "pass").hasCredentials()
        !new AuthConfig("user", "").hasCredentials()
    }

    def "hasCredentials returns true for valid username/password"() {
        expect:
        new AuthConfig("user", "pass").hasCredentials()
        new AuthConfig("user", "pass", null, "server").hasCredentials()
    }

    def "hasCredentials returns true for token-based auth"() {
        expect:
        new AuthConfig(null, null, "token").hasCredentials()
        new AuthConfig("", "", "token").hasCredentials()
    }

    def "isUsernamePassword validates both fields are present"() {
        expect:
        new AuthConfig("user", "pass").isUsernamePassword()
        new AuthConfig("user", "pass", "token").isUsernamePassword() // Both types present
        
        !new AuthConfig("user", null).isUsernamePassword()
        !new AuthConfig(null, "pass").isUsernamePassword()
        !new AuthConfig("", "pass").isUsernamePassword()
        !new AuthConfig("user", "").isUsernamePassword()
        !new AuthConfig(null, null, "token").isUsernamePassword()
    }

    def "isTokenBased validates token is present"() {
        expect:
        new AuthConfig(null, null, "token").isTokenBased()
        new AuthConfig("user", "pass", "token").isTokenBased() // Both types present
        new AuthConfig("", "", "token").isTokenBased()
        
        !new AuthConfig("user", "pass").isTokenBased()
        !new AuthConfig().isTokenBased()
        !new AuthConfig(null, null, null).isTokenBased()
    }

    def "toDockerJavaAuthConfig creates proper Docker Java AuthConfig"() {
        given:
        def auth = new AuthConfig("testuser", "testpass", "testtoken", "registry.example.com")

        when:
        def dockerAuth = auth.toDockerJavaAuthConfig()

        then:
        dockerAuth != null
        dockerAuth.username == "testuser"
        dockerAuth.password == "testpass"
        dockerAuth.registrytoken == "testtoken"
        dockerAuth.registryAddress == "registry.example.com"
    }

    def "toDockerJavaAuthConfig handles null values properly"() {
        given:
        def auth = new AuthConfig("user", null, null, null)

        when:
        def dockerAuth = auth.toDockerJavaAuthConfig()

        then:
        dockerAuth != null
        dockerAuth.username == "user"
        dockerAuth.password == null
        dockerAuth.registrytoken == null
        // Docker Java sets a default registry address
        dockerAuth.registryAddress != null
    }

    def "toDockerJavaAuthConfig handles empty AuthConfig"() {
        given:
        def auth = new AuthConfig()

        when:
        def dockerAuth = auth.toDockerJavaAuthConfig()

        then:
        dockerAuth != null
        dockerAuth.username == null
        dockerAuth.password == null
        dockerAuth.registrytoken == null
        // Docker Java sets a default registry address
        dockerAuth.registryAddress != null
    }

    def "toString masks sensitive information"() {
        expect:
        new AuthConfig().toString() == "AuthConfig{empty}"
        
        new AuthConfig("user", "secret", null, "server").toString() == 
            "AuthConfig{username='user', password=*****, serverAddress='server'}"
            
        new AuthConfig(null, null, "secrettoken", "server").toString() == 
            "AuthConfig{token=*****, serverAddress='server'}"
    }

    def "toString handles null serverAddress"() {
        expect:
        new AuthConfig("user", "pass").toString() == 
            "AuthConfig{username='user', password=*****, serverAddress='null'}"
            
        new AuthConfig(null, null, "token").toString() == 
            "AuthConfig{token=*****, serverAddress='null'}"
    }

    def "toString prioritizes token over username/password when both present"() {
        when:
        def auth = new AuthConfig("user", "pass", "token", "server")

        then:
        auth.toString() == "AuthConfig{token=*****, serverAddress='server'}"
    }

    def "edge case: empty strings vs null values"() {
        given:
        def auth = new AuthConfig("", "", "", "")

        expect:
        // Empty strings are falsy in Groovy boolean context, but still valid for auth purposes
        auth.hasCredentials()  // True because both username/password and token methods are valid
        auth.isUsernamePassword()  // True because both username and password are empty (anonymous auth)
        // Empty string registryToken is not null, so isTokenBased returns true
        auth.isTokenBased()
        // Since isTokenBased is true and takes priority, toString shows token format
        auth.toString() == "AuthConfig{token=*****, serverAddress=''}"
    }

    def "edge case: whitespace-only strings"() {
        given:
        def auth = new AuthConfig("  ", "  ", "  ", "  ")

        expect:
        auth.hasCredentials() // Whitespace strings are truthy in Groovy
        auth.isUsernamePassword()
        auth.isTokenBased()
    }
}