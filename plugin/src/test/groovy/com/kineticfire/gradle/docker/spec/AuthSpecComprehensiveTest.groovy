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
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Comprehensive unit tests for AuthSpec
 */
class AuthSpecComprehensiveTest extends Specification {

    def project
    def authSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        authSpec = project.objects.newInstance(AuthSpec)
    }

    // ===== BASIC PROPERTY TESTS =====

    def "username property works"() {
        when:
        authSpec.username.set("testuser")

        then:
        authSpec.username.present
        authSpec.username.get() == "testuser"
    }

    def "password property works"() {
        when:
        authSpec.password.set("testpass")

        then:
        authSpec.password.present
        authSpec.password.get() == "testpass"
    }

    def "registryToken property works"() {
        when:
        authSpec.registryToken.set("token123abc")

        then:
        authSpec.registryToken.present
        authSpec.registryToken.get() == "token123abc"
    }

    // serverAddress property removed - extracted automatically from image reference

    def "helper property works"() {
        when:
        authSpec.helper.set("credential-helper")

        then:
        authSpec.helper.present
        authSpec.helper.get() == "credential-helper"
    }

    // ===== PROVIDER API TESTS =====

    def "username property works with Provider API"() {
        given:
        def usernameProvider = project.provider { "dynamic-user" }

        when:
        authSpec.username.set(usernameProvider)

        then:
        authSpec.username.get() == "dynamic-user"
    }

    def "password property works with Provider API"() {
        given:
        def passwordProvider = project.provider { "dynamic-password" }

        when:
        authSpec.password.set(passwordProvider)

        then:
        authSpec.password.get() == "dynamic-password"
    }

    def "registryToken property works with Provider API"() {
        given:
        def tokenProvider = project.provider { "dynamic-token-xyz" }

        when:
        authSpec.registryToken.set(tokenProvider)

        then:
        authSpec.registryToken.get() == "dynamic-token-xyz"
    }

    // serverAddress property removed - extracted automatically from image reference

    def "helper property works with Provider API"() {
        given:
        def helperProvider = project.provider { "dynamic-helper" }

        when:
        authSpec.helper.set(helperProvider)

        then:
        authSpec.helper.get() == "dynamic-helper"
    }

    // ===== AUTHENTICATION SCENARIOS =====

    def "username and password authentication"() {
        when:
        authSpec.username.set("dockeruser")
        authSpec.password.set("dockerpass")
        // serverAddress removed - extracted automatically from image reference

        then:
        authSpec.username.get() == "dockeruser"
        authSpec.password.get() == "dockerpass"
        // serverAddress removed - extracted automatically from image reference
        !authSpec.registryToken.present
        !authSpec.helper.present
    }

    def "token authentication"() {
        when:
        authSpec.registryToken.set("ghp_abcdef123456")
        // serverAddress removed - extracted automatically from image reference

        then:
        authSpec.registryToken.get() == "ghp_abcdef123456"
        // serverAddress removed - extracted automatically from image reference
        !authSpec.username.present
        !authSpec.password.present
        !authSpec.helper.present
    }

    def "helper authentication"() {
        when:
        authSpec.helper.set("ecr-login")
        // serverAddress removed - extracted automatically from image reference

        then:
        authSpec.helper.get() == "ecr-login"
        // serverAddress removed - extracted automatically from image reference
        !authSpec.username.present
        !authSpec.password.present
        !authSpec.registryToken.present
    }

    def "mixed authentication properties"() {
        when:
        authSpec.username.set("user")
        authSpec.password.set("pass")
        authSpec.registryToken.set("token")
        authSpec.helper.set("helper")
        // serverAddress removed - extracted automatically from image reference

        then:
        authSpec.username.get() == "user"
        authSpec.password.get() == "pass"
        authSpec.registryToken.get() == "token"
        authSpec.helper.get() == "helper"
        // serverAddress removed - extracted automatically from image reference
    }

    // ===== TO AUTH CONFIG CONVERSION =====

    def "toAuthConfig with username and password"() {
        given:
        authSpec.username.set("configuser")
        authSpec.password.set("configpass")
        // serverAddress removed - extracted automatically from image reference

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == "configuser"
        authConfig.password == "configpass"
        // serverAddress removed - extracted automatically from image reference
        authConfig.registryToken == null
    }

    def "toAuthConfig with registry token"() {
        given:
        authSpec.registryToken.set("config_token_abc")
        // serverAddress removed - extracted automatically from image reference

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.registryToken == "config_token_abc"
        // serverAddress removed - extracted automatically from image reference
        authConfig.username == null
        authConfig.password == null
    }

    def "toAuthConfig with no properties set"() {
        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == null
        authConfig.password == null
        authConfig.registryToken == null
        // serverAddress removed - extracted automatically from image reference
    }

    def "toAuthConfig with all properties set"() {
        given:
        authSpec.username.set("alluser")
        authSpec.password.set("allpass")
        authSpec.registryToken.set("alltoken")
        // serverAddress removed - extracted automatically from image reference

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == "alluser"
        authConfig.password == "allpass"
        authConfig.registryToken == "alltoken"
        // serverAddress removed - extracted automatically from image reference
    }

    // ===== REAL-WORLD SCENARIOS =====

    def "Docker Hub authentication"() {
        when:
        authSpec.username.set("dockerhubuser")
        authSpec.password.set("dockerhubtoken")  // Docker Hub uses tokens as passwords
        // serverAddress removed - extracted automatically from image reference

        then:
        def authConfig = authSpec.toAuthConfig()
        authConfig.username == "dockerhubuser"
        authConfig.password == "dockerhubtoken"
        // serverAddress removed - extracted automatically from image reference
    }

    def "GitHub Container Registry authentication"() {
        when:
        authSpec.registryToken.set("ghp_1234567890abcdef")
        // serverAddress removed - extracted automatically from image reference

        then:
        def authConfig = authSpec.toAuthConfig()
        authConfig.registryToken == "ghp_1234567890abcdef"
        // serverAddress removed - extracted automatically from image reference
    }

    def "Amazon ECR authentication with helper"() {
        when:
        authSpec.helper.set("ecr-login")
        // serverAddress removed - extracted automatically from image reference

        then:
        def authConfig = authSpec.toAuthConfig()
        // serverAddress removed - extracted automatically from image reference
        // Note: helper is not part of AuthConfig model
    }

    def "Google Container Registry authentication"() {
        when:
        authSpec.username.set("_json_key")
        authSpec.password.set('{"type": "service_account", "project_id": "my-project"}')
        // serverAddress removed - extracted automatically from image reference

        then:
        def authConfig = authSpec.toAuthConfig()
        authConfig.username == "_json_key"
        authConfig.password == '{"type": "service_account", "project_id": "my-project"}'
        // serverAddress removed - extracted automatically from image reference
    }

    def "Private registry with basic auth"() {
        when:
        authSpec.username.set("privateuser")
        authSpec.password.set("privatepass")
        // serverAddress removed - extracted automatically from image reference

        then:
        def authConfig = authSpec.toAuthConfig()
        authConfig.username == "privateuser"
        authConfig.password == "privatepass"
        // serverAddress removed - extracted automatically from image reference
    }

    // ===== EDGE CASES =====

    def "empty string values"() {
        when:
        authSpec.username.set("")
        authSpec.password.set("")
        authSpec.registryToken.set("")
        // serverAddress removed - extracted automatically from image reference
        authSpec.helper.set("")

        then:
        authSpec.username.get() == ""
        authSpec.password.get() == ""
        authSpec.registryToken.get() == ""
        // serverAddress removed - extracted automatically from image reference
        authSpec.helper.get() == ""
        
        def authConfig = authSpec.toAuthConfig()
        authConfig.username == ""
        authConfig.password == ""
        authConfig.registryToken == ""
        // serverAddress removed - extracted automatically from image reference
    }

    def "properties can be overridden"() {
        when:
        authSpec.username.set("firstuser")
        authSpec.username.set("seconduser")
        
        // serverAddress removed - extracted automatically from image reference

        then:
        authSpec.username.get() == "seconduser"
        // serverAddress removed - extracted automatically from image reference
    }

    // serverAddress property removed - extracted automatically from image reference

    def "long credentials"() {
        given:
        def longUsername = "a" * 255
        def longPassword = "b" * 1000
        def longToken = "c" * 2048

        when:
        authSpec.username.set(longUsername)
        authSpec.password.set(longPassword)
        authSpec.registryToken.set(longToken)

        then:
        authSpec.username.get() == longUsername
        authSpec.password.get() == longPassword
        authSpec.registryToken.get() == longToken
    }
}