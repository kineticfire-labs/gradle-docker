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

    def "serverAddress property works"() {
        when:
        authSpec.serverAddress.set("registry.example.com")

        then:
        authSpec.serverAddress.present
        authSpec.serverAddress.get() == "registry.example.com"
    }

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

    def "serverAddress property works with Provider API"() {
        given:
        def serverProvider = project.provider { "dynamic.registry.io" }

        when:
        authSpec.serverAddress.set(serverProvider)

        then:
        authSpec.serverAddress.get() == "dynamic.registry.io"
    }

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
        authSpec.serverAddress.set("docker.io")

        then:
        authSpec.username.get() == "dockeruser"
        authSpec.password.get() == "dockerpass"
        authSpec.serverAddress.get() == "docker.io"
        !authSpec.registryToken.present
        !authSpec.helper.present
    }

    def "token authentication"() {
        when:
        authSpec.registryToken.set("ghp_abcdef123456")
        authSpec.serverAddress.set("ghcr.io")

        then:
        authSpec.registryToken.get() == "ghp_abcdef123456"
        authSpec.serverAddress.get() == "ghcr.io"
        !authSpec.username.present
        !authSpec.password.present
        !authSpec.helper.present
    }

    def "helper authentication"() {
        when:
        authSpec.helper.set("ecr-login")
        authSpec.serverAddress.set("123456789012.dkr.ecr.us-west-2.amazonaws.com")

        then:
        authSpec.helper.get() == "ecr-login"
        authSpec.serverAddress.get() == "123456789012.dkr.ecr.us-west-2.amazonaws.com"
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
        authSpec.serverAddress.set("mixed.registry.io")

        then:
        authSpec.username.get() == "user"
        authSpec.password.get() == "pass"
        authSpec.registryToken.get() == "token"
        authSpec.helper.get() == "helper"
        authSpec.serverAddress.get() == "mixed.registry.io"
    }

    // ===== TO AUTH CONFIG CONVERSION =====

    def "toAuthConfig with username and password"() {
        given:
        authSpec.username.set("configuser")
        authSpec.password.set("configpass")
        authSpec.serverAddress.set("config.registry.io")

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == "configuser"
        authConfig.password == "configpass"
        authConfig.serverAddress == "config.registry.io"
        authConfig.registryToken == null
    }

    def "toAuthConfig with registry token"() {
        given:
        authSpec.registryToken.set("config_token_abc")
        authSpec.serverAddress.set("token.config.registry")

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.registryToken == "config_token_abc"
        authConfig.serverAddress == "token.config.registry"
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
        authConfig.serverAddress == null
    }

    def "toAuthConfig with all properties set"() {
        given:
        authSpec.username.set("alluser")
        authSpec.password.set("allpass")
        authSpec.registryToken.set("alltoken")
        authSpec.serverAddress.set("all.registry.io")

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == "alluser"
        authConfig.password == "allpass"
        authConfig.registryToken == "alltoken"
        authConfig.serverAddress == "all.registry.io"
    }

    // ===== REAL-WORLD SCENARIOS =====

    def "Docker Hub authentication"() {
        when:
        authSpec.username.set("dockerhubuser")
        authSpec.password.set("dockerhubtoken")  // Docker Hub uses tokens as passwords
        authSpec.serverAddress.set("docker.io")

        then:
        def authConfig = authSpec.toAuthConfig()
        authConfig.username == "dockerhubuser"
        authConfig.password == "dockerhubtoken"
        authConfig.serverAddress == "docker.io"
    }

    def "GitHub Container Registry authentication"() {
        when:
        authSpec.registryToken.set("ghp_1234567890abcdef")
        authSpec.serverAddress.set("ghcr.io")

        then:
        def authConfig = authSpec.toAuthConfig()
        authConfig.registryToken == "ghp_1234567890abcdef"
        authConfig.serverAddress == "ghcr.io"
    }

    def "Amazon ECR authentication with helper"() {
        when:
        authSpec.helper.set("ecr-login")
        authSpec.serverAddress.set("123456789012.dkr.ecr.us-west-2.amazonaws.com")

        then:
        def authConfig = authSpec.toAuthConfig()
        authConfig.serverAddress == "123456789012.dkr.ecr.us-west-2.amazonaws.com"
        // Note: helper is not part of AuthConfig model
    }

    def "Google Container Registry authentication"() {
        when:
        authSpec.username.set("_json_key")
        authSpec.password.set('{"type": "service_account", "project_id": "my-project"}')
        authSpec.serverAddress.set("gcr.io")

        then:
        def authConfig = authSpec.toAuthConfig()
        authConfig.username == "_json_key"
        authConfig.password == '{"type": "service_account", "project_id": "my-project"}'
        authConfig.serverAddress == "gcr.io"
    }

    def "Private registry with basic auth"() {
        when:
        authSpec.username.set("privateuser")
        authSpec.password.set("privatepass")
        authSpec.serverAddress.set("private.company.registry:5000")

        then:
        def authConfig = authSpec.toAuthConfig()
        authConfig.username == "privateuser"
        authConfig.password == "privatepass"
        authConfig.serverAddress == "private.company.registry:5000"
    }

    // ===== EDGE CASES =====

    def "empty string values"() {
        when:
        authSpec.username.set("")
        authSpec.password.set("")
        authSpec.registryToken.set("")
        authSpec.serverAddress.set("")
        authSpec.helper.set("")

        then:
        authSpec.username.get() == ""
        authSpec.password.get() == ""
        authSpec.registryToken.get() == ""
        authSpec.serverAddress.get() == ""
        authSpec.helper.get() == ""
        
        def authConfig = authSpec.toAuthConfig()
        authConfig.username == ""
        authConfig.password == ""
        authConfig.registryToken == ""
        authConfig.serverAddress == ""
    }

    def "properties can be overridden"() {
        when:
        authSpec.username.set("firstuser")
        authSpec.username.set("seconduser")
        
        authSpec.serverAddress.set("first.registry.io")
        authSpec.serverAddress.set("second.registry.io")

        then:
        authSpec.username.get() == "seconduser"
        authSpec.serverAddress.get() == "second.registry.io"
    }

    def "complex server addresses"() {
        expect:
        authSpec.serverAddress.set(serverAddress)
        authSpec.serverAddress.get() == serverAddress

        where:
        serverAddress << [
            "localhost:5000",
            "192.168.1.100:8080",
            "registry.company.internal",
            "sub.domain.registry.com:443",
            "registry-with-dashes.io",
            "registry_with_underscores.com"
        ]
    }

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