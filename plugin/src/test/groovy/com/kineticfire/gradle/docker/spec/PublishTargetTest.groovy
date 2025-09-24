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
        publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with name and project"() {
        expect:
        publishTarget != null
        publishTarget.name == 'testTarget'
    }

    def "constructor with different names"() {
        given:
        def target1 = project.objects.newInstance(PublishTarget, 'production', project.objects)
        def target2 = project.objects.newInstance(PublishTarget, 'staging', project.objects)

        expect:
        target1.name == 'production'
        target2.name == 'staging'
    }

    // ===== PROPERTY TESTS =====

    def "tags property works correctly with full image references"() {
        when:
        publishTarget.tags.set(['myapp:v1.0', 'registry.com/team/myapp:latest', 'localhost:5000/myapp:stable'])

        then:
        publishTarget.tags.present
        publishTarget.tags.get() == ['myapp:v1.0', 'registry.com/team/myapp:latest', 'localhost:5000/myapp:stable']
    }

    def "tags property with empty list"() {
        when:
        publishTarget.tags.set([])

        then:
        publishTarget.tags.present
        publishTarget.tags.get().isEmpty()
    }

    def "tags property supports various registry formats"() {
        when:
        publishTarget.tags.set([
            'myapp:latest',                                    // Simple format
            'docker.io/user/myapp:v1.0',                      // Docker Hub
            'gcr.io/project/myapp:stable',                     // Google Container Registry
            'registry.company.com:8080/team/myapp:dev',        // Private registry with port
            'localhost:5000/namespace/myapp:test'              // Local registry
        ])

        then:
        publishTarget.tags.present
        publishTarget.tags.get().size() == 5
        publishTarget.tags.get().contains('myapp:latest')
        publishTarget.tags.get().contains('docker.io/user/myapp:v1.0')
        publishTarget.tags.get().contains('gcr.io/project/myapp:stable')
        publishTarget.tags.get().contains('registry.company.com:8080/team/myapp:dev')
        publishTarget.tags.get().contains('localhost:5000/namespace/myapp:test')
    }

    def "tags property is initially empty"() {
        expect:
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
            username.set('myuser')
            password.set('mypassword')
            // serverAddress removed - extracted automatically from image reference
        }

        then:
        publishTarget.auth.present
        publishTarget.auth.get().username.get() == 'myuser'
        publishTarget.auth.get().password.get() == 'mypassword'
        // serverAddress removed - extracted automatically from image reference
    }

    def "auth(Closure) with registry token"() {
        when:
        publishTarget.auth {
            registryToken.set('ghp_abc123def456')
            // serverAddress removed - extracted automatically from image reference
        }

        then:
        publishTarget.auth.present
        publishTarget.auth.get().registryToken.get() == 'ghp_abc123def456'
        // serverAddress removed - extracted automatically from image reference
        !publishTarget.auth.get().username.present
        !publishTarget.auth.get().password.present
    }

    def "auth(Closure) with helper"() {
        when:
        publishTarget.auth {
            helper.set('docker-credential-helper')
            // serverAddress removed - extracted automatically from image reference
        }

        then:
        publishTarget.auth.present
        publishTarget.auth.get().helper.get() == 'docker-credential-helper'
        // serverAddress removed - extracted automatically from image reference
    }

    // ===== AUTH ACTION TESTS =====

    def "auth(Action) configures authentication"() {
        when:
        publishTarget.auth(new Action<AuthSpec>() {
            @Override
            void execute(AuthSpec authSpec) {
                authSpec.username.set('actionuser')
                authSpec.password.set('actionpass')
                // serverAddress removed - extracted automatically from image reference
            }
        })

        then:
        publishTarget.auth.present
        publishTarget.auth.get().username.get() == 'actionuser'
        publishTarget.auth.get().password.get() == 'actionpass'
        // serverAddress removed - extracted automatically from image reference
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
        // serverAddress property removed - extracted automatically from image reference
        !publishTarget.auth.get().helper.present
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        when:
        publishTarget.tags.set(['private.registry.com/myproject/myapp:v1.0.0', 'private.registry.com/myproject/myapp:latest'])
        publishTarget.auth {
            username.set('deployuser')
            password.set('deploypass')
            // serverAddress removed - extracted automatically from image reference
        }

        then:
        publishTarget.name == 'testTarget'
        publishTarget.tags.get() == ['private.registry.com/myproject/myapp:v1.0.0', 'private.registry.com/myproject/myapp:latest']
        publishTarget.auth.present
        publishTarget.auth.get().username.get() == 'deployuser'
        publishTarget.auth.get().password.get() == 'deploypass'
        // serverAddress removed - extracted automatically from image reference
    }

    // ===== EDGE CASES =====

    def "auth can be reconfigured"() {
        when:
        publishTarget.auth {
            username.set('user1')
            password.set('pass1')
        }
        
        then:
        publishTarget.auth.get().username.get() == 'user1'
        publishTarget.auth.get().password.get() == 'pass1'
        
        when:
        publishTarget.auth {
            registryToken.set('token123')
            // serverAddress removed - extracted automatically from image reference
        }
        
        then:
        publishTarget.auth.get().registryToken.get() == 'token123'
        // serverAddress removed - extracted automatically from image reference
    }

    // ===== REGISTRY VALIDATION TESTS =====

    def "validateRegistry succeeds when registry is explicitly set"() {
        when:
        publishTarget.registry.set('docker.io')

        then:
        publishTarget.validateRegistry() // Should not throw
    }

    def "validateRegistry succeeds when repository is fully qualified"() {
        when:
        publishTarget.repository.set('docker.io/myuser/myapp')

        then:
        publishTarget.validateRegistry() // Should not throw
    }

    def "validateRegistry fails when neither registry nor fully qualified repository is set"() {
        when:
        publishTarget.validateRegistry()

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Registry must be explicitly specified for publish target 'testTarget'")
        exception.message.contains("registry.set('docker.io') for Docker Hub")
        exception.message.contains("registry.set('localhost:5000') for local registry")
        exception.message.contains("registry.set('<other-target-registry>') for other registries")
    }

    def "validateRegistry fails when repository contains slash but is not fully qualified"() {
        when:
        publishTarget.repository.set('myuser/myapp') // Not fully qualified (no . or :)
        publishTarget.validateRegistry()

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Registry must be explicitly specified for publish target 'testTarget'")
    }

    // ===== REGISTRY CONFLICT DETECTION TESTS =====

    def "validateRegistryConsistency succeeds when no conflict exists"() {
        when:
        publishTarget.registry.set('docker.io')
        publishTarget.repository.set('myuser/myapp')

        then:
        publishTarget.validateRegistryConsistency() // Should not throw
    }

    def "validateRegistryConsistency succeeds when only registry is set"() {
        when:
        publishTarget.registry.set('docker.io')

        then:
        publishTarget.validateRegistryConsistency() // Should not throw
    }

    def "validateRegistryConsistency succeeds when only repository is set"() {
        when:
        publishTarget.repository.set('docker.io/myuser/myapp')

        then:
        publishTarget.validateRegistryConsistency() // Should not throw
    }

    def "validateRegistryConsistency succeeds when registry matches repository registry"() {
        when:
        publishTarget.registry.set('docker.io')
        publishTarget.repository.set('docker.io/myuser/myapp')

        then:
        publishTarget.validateRegistryConsistency() // Should not throw
    }

    def "validateRegistryConsistency fails when registry conflicts with repository registry"() {
        when:
        publishTarget.registry.set('docker.io')
        publishTarget.repository.set('ghcr.io/myuser/myapp')
        publishTarget.validateRegistryConsistency()

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Registry conflict in publish target 'testTarget'")
        exception.message.contains("registry.set('docker.io') conflicts with repository 'ghcr.io/myuser/myapp'")
        exception.message.contains("Use either registry property OR fully qualified repository, not both")
    }

    def "validateRegistryConsistency fails with localhost registry conflict"() {
        when:
        publishTarget.registry.set('localhost:5000')
        publishTarget.repository.set('myregistry.com:8080/myuser/myapp')
        publishTarget.validateRegistryConsistency()

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Registry conflict in publish target 'testTarget'")
        exception.message.contains("registry.set('localhost:5000') conflicts with repository 'myregistry.com:8080/myuser/myapp'")
    }

    def "properties can be updated after initial configuration"() {
        given:
        publishTarget.tags.set(['myapp:v1.0'])

        when:
        publishTarget.tags.set(['myapp:v2.0', 'registry.com/team/myapp:latest'])

        then:
        publishTarget.tags.get() == ['myapp:v2.0', 'registry.com/team/myapp:latest']
    }
}