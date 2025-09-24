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
 * Unit tests for AuthSpec
 */
class AuthSpecTest extends Specification {

    def project
    def authSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        authSpec = project.objects.newInstance(AuthSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes correctly"() {
        expect:
        authSpec != null
    }

    // ===== PROPERTY TESTS =====

    def "username property works correctly"() {
        when:
        authSpec.username.set('myuser')

        then:
        authSpec.username.present
        authSpec.username.get() == 'myuser'
    }

    def "password property works correctly"() {
        when:
        authSpec.password.set('mypassword')

        then:
        authSpec.password.present
        authSpec.password.get() == 'mypassword'
    }

    def "registryToken property works correctly"() {
        when:
        authSpec.registryToken.set('ghp_abc123def456')

        then:
        authSpec.registryToken.present
        authSpec.registryToken.get() == 'ghp_abc123def456'
    }

    // serverAddress property removed - extracted automatically from image reference

    def "helper property works correctly"() {
        when:
        authSpec.helper.set('docker-credential-helper')

        then:
        authSpec.helper.present
        authSpec.helper.get() == 'docker-credential-helper'
    }

    // ===== TO AUTH CONFIG TESTS =====

    def "toAuthConfig with username and password"() {
        given:
        authSpec.username.set('testuser')
        authSpec.password.set('testpass')
        // serverAddress removed - extracted automatically from image reference

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == 'testuser'
        authConfig.password == 'testpass'
        authConfig.registryToken == null
        // serverAddress removed - extracted automatically from image reference
    }

    def "toAuthConfig with registry token"() {
        given:
        authSpec.registryToken.set('token_abc123')
        // serverAddress removed - extracted automatically from image reference

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == null
        authConfig.password == null
        authConfig.registryToken == 'token_abc123'
        // serverAddress removed - extracted automatically from image reference
    }

    def "toAuthConfig with all properties set"() {
        given:
        authSpec.username.set('fulluser')
        authSpec.password.set('fullpass')
        authSpec.registryToken.set('fulltoken')
        // serverAddress removed - extracted automatically from image reference
        authSpec.helper.set('credential-helper')

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == 'fulluser'
        authConfig.password == 'fullpass'
        authConfig.registryToken == 'fulltoken'
        // serverAddress removed - extracted automatically from image reference
        // Note: helper is not included in AuthConfig model
    }

    def "toAuthConfig with minimal configuration"() {
        given:
        authSpec.username.set('minuser')

        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == 'minuser'
        authConfig.password == null
        authConfig.registryToken == null
        // serverAddress removed - extracted automatically from image reference
    }

    def "toAuthConfig with empty configuration"() {
        when:
        def authConfig = authSpec.toAuthConfig()

        then:
        authConfig instanceof AuthConfig
        authConfig.username == null
        authConfig.password == null
        authConfig.registryToken == null
        // serverAddress removed - extracted automatically from image reference
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated"() {
        given:
        authSpec.username.set('olduser')
        authSpec.password.set('oldpass')

        when:
        authSpec.username.set('newuser')
        authSpec.password.set('newpass')

        then:
        authSpec.username.get() == 'newuser'
        authSpec.password.get() == 'newpass'
    }

    def "toAuthConfig reflects property updates"() {
        given:
        authSpec.username.set('initial')
        def initialConfig = authSpec.toAuthConfig()

        when:
        authSpec.username.set('updated')
        def updatedConfig = authSpec.toAuthConfig()

        then:
        initialConfig.username == 'initial'
        updatedConfig.username == 'updated'
    }

    // ===== EDGE CASES =====

    def "properties are initially not present"() {
        expect:
        !authSpec.username.present
        !authSpec.password.present
        !authSpec.registryToken.present
        // serverAddress property removed - extracted automatically from image reference
        !authSpec.helper.present
    }

    def "multiple toAuthConfig calls return consistent results"() {
        given:
        authSpec.username.set('consistent')
        authSpec.registryToken.set('token123')

        when:
        def config1 = authSpec.toAuthConfig()
        def config2 = authSpec.toAuthConfig()

        then:
        config1.username == config2.username
        config1.registryToken == config2.registryToken
        config1.password == config2.password
        // serverAddress removed - extracted automatically from image reference
    }
}