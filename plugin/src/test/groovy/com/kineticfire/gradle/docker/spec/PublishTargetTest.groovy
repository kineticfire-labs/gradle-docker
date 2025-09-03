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

import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for PublishTarget
 */
class PublishTargetTest extends Specification {

    def project
    def publishTarget

    def setup() {
        project = ProjectBuilder.builder().build()
        publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with name and project"() {
        expect:
        publishTarget != null
        publishTarget.name == 'testTarget'
    }

    def "constructor with different names"() {
        given:
        def target1 = project.objects.newInstance(PublishTarget, 'production', project)
        def target2 = project.objects.newInstance(PublishTarget, 'staging', project)

        expect:
        target1.name == 'production'
        target2.name == 'staging'
    }

    // ===== PROPERTY TESTS =====

    def "repository property works correctly"() {
        when:
        publishTarget.repository.set('docker.io/myapp')

        then:
        publishTarget.repository.present
        publishTarget.repository.get() == 'docker.io/myapp'
    }

    def "tags property works correctly"() {
        when:
        publishTarget.tags.set(['v1.0', 'latest', 'stable'])

        then:
        publishTarget.tags.present
        publishTarget.tags.get() == ['v1.0', 'latest', 'stable']
    }

    def "tags property with empty list"() {
        when:
        publishTarget.tags.set([])

        then:
        publishTarget.tags.present
        publishTarget.tags.get().isEmpty()
    }

    def "auth property initially not present"() {
        expect:
        !publishTarget.auth.present
    }

    // ===== AUTH CLOSURE TESTS =====

    def "auth(Closure) configures authentication"() {
        when:
        publishTarget.auth {
            username = 'myuser'
            password = 'mypassword'
            serverAddress = 'docker.io'
        }

        then:
        publishTarget.auth.present
        publishTarget.auth.get().username.get() == 'myuser'
        publishTarget.auth.get().password.get() == 'mypassword'
        publishTarget.auth.get().serverAddress.get() == 'docker.io'
    }

    def "auth(Closure) with registry token"() {
        when:
        publishTarget.auth {
            registryToken = 'ghp_abc123def456'
            serverAddress = 'ghcr.io'
        }

        then:
        publishTarget.auth.present
        publishTarget.auth.get().registryToken.get() == 'ghp_abc123def456'
        publishTarget.auth.get().serverAddress.get() == 'ghcr.io'
        !publishTarget.auth.get().username.present
        !publishTarget.auth.get().password.present
    }

    def "auth(Closure) with helper"() {
        when:
        publishTarget.auth {
            helper = 'docker-credential-helper'
            serverAddress = 'private.registry.com'
        }

        then:
        publishTarget.auth.present
        publishTarget.auth.get().helper.get() == 'docker-credential-helper'
        publishTarget.auth.get().serverAddress.get() == 'private.registry.com'
    }

    // ===== AUTH ACTION TESTS =====

    def "auth(Action) configures authentication"() {
        when:
        publishTarget.auth(new Action<AuthSpec>() {
            @Override
            void execute(AuthSpec authSpec) {
                authSpec.username.set('actionuser')
                authSpec.password.set('actionpass')
                authSpec.serverAddress.set('registry.company.com')
            }
        })

        then:
        publishTarget.auth.present
        publishTarget.auth.get().username.get() == 'actionuser'
        publishTarget.auth.get().password.get() == 'actionpass'
        publishTarget.auth.get().serverAddress.get() == 'registry.company.com'
    }

    def "auth(Action) with minimal configuration"() {
        when:
        publishTarget.auth(new Action<AuthSpec>() {
            @Override
            void execute(AuthSpec authSpec) {
                authSpec.registryToken.set('minimal-token')
            }
        })

        then:
        publishTarget.auth.present
        publishTarget.auth.get().registryToken.get() == 'minimal-token'
        !publishTarget.auth.get().username.present
        !publishTarget.auth.get().password.present
        !publishTarget.auth.get().serverAddress.present
        !publishTarget.auth.get().helper.present
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        when:
        publishTarget.repository.set('private.registry.com/myproject/myapp')
        publishTarget.tags.set(['v1.0.0', '1.0', 'latest'])
        publishTarget.auth {
            username = 'deployuser'
            password = 'deploypass'
            serverAddress = 'private.registry.com'
        }

        then:
        publishTarget.name == 'testTarget'
        publishTarget.repository.get() == 'private.registry.com/myproject/myapp'
        publishTarget.tags.get() == ['v1.0.0', '1.0', 'latest']
        publishTarget.auth.present
        publishTarget.auth.get().username.get() == 'deployuser'
        publishTarget.auth.get().password.get() == 'deploypass'
        publishTarget.auth.get().serverAddress.get() == 'private.registry.com'
    }

    // ===== EDGE CASES =====

    def "auth can be reconfigured"() {
        when:
        publishTarget.auth {
            username = 'user1'
            password = 'pass1'
        }
        
        then:
        publishTarget.auth.get().username.get() == 'user1'
        publishTarget.auth.get().password.get() == 'pass1'
        
        when:
        publishTarget.auth {
            registryToken = 'token123'
            serverAddress = 'new.registry.com'
        }
        
        then:
        publishTarget.auth.get().registryToken.get() == 'token123'
        publishTarget.auth.get().serverAddress.get() == 'new.registry.com'
    }

    def "properties can be updated after initial configuration"() {
        given:
        publishTarget.repository.set('initial.repo.com/app')
        publishTarget.tags.set(['v1.0'])

        when:
        publishTarget.repository.set('updated.repo.com/app')
        publishTarget.tags.set(['v2.0', 'latest'])

        then:
        publishTarget.repository.get() == 'updated.repo.com/app'
        publishTarget.tags.get() == ['v2.0', 'latest']
    }
}