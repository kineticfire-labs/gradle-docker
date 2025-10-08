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

    def "tags property works correctly with simple tags"() {
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

    def "tags property supports various tag formats"() {
        when:
        publishTarget.tags.set([
            'latest',                                          // Simple latest tag
            'v1.0',                                           // Version tag
            'stable',                                         // Environment tag
            'dev',                                            // Development tag
            'test'                                            // Test tag
        ])

        then:
        publishTarget.tags.present
        publishTarget.tags.get().size() == 5
        publishTarget.tags.get().contains('latest')
        publishTarget.tags.get().contains('v1.0')
        publishTarget.tags.get().contains('stable')
        publishTarget.tags.get().contains('dev')
        publishTarget.tags.get().contains('test')
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
        publishTarget.tags.set(['v1.0.0', 'latest'])
        publishTarget.auth {
            username.set('deployuser')
            password.set('deploypass')
            // serverAddress removed - extracted automatically from image reference
        }

        then:
        publishTarget.name == 'testTarget'
        publishTarget.tags.get() == ['v1.0.0', 'latest']
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

    def "validateRegistry succeeds when target is completely empty (inheritance mode)"() {
        when:
        publishTarget.validateRegistry()

        then:
        noExceptionThrown() // Empty targets should be allowed for inheritance
    }

    def "validateRegistry fails when target has properties but no registry"() {
        when:
        publishTarget.imageName.set('myapp')  // Set a property to make it non-empty
        publishTarget.validateRegistry()

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Registry must be explicitly specified for publish target 'testTarget'")
        exception.message.contains("registry.set('docker.io') for Docker Hub")
        exception.message.contains("registry.set('localhost:5000') for local registry")
        exception.message.contains("registry.set('<other-target-registry>') for other registries")
        exception.message.contains("leave the target completely empty to inherit")
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
        publishTarget.tags.set(['v1.0'])

        when:
        publishTarget.tags.set(['v2.0', 'latest'])

        then:
        publishTarget.tags.get() == ['v2.0', 'latest']
    }

    // ===== PUBLISHTAGS VALIDATION TESTS =====

    def "publishTags should accept simple tags only"() {
        when:
        publishTarget.publishTags.set(['latest', 'v1.0', 'stable', 'dev'])

        then:
        publishTarget.publishTags.get() == ['latest', 'v1.0', 'stable', 'dev']
        noExceptionThrown()
    }

    def "publishTags accepts semantic version tags"() {
        when:
        publishTarget.publishTags.set(['1.0.0', '2.1.3', '1.0.0-alpha', '2.0.0-beta.1'])

        then:
        publishTarget.publishTags.get() == ['1.0.0', '2.1.3', '1.0.0-alpha', '2.0.0-beta.1']
        noExceptionThrown()
    }

    def "publishTags accepts environment and build tags"() {
        when:
        publishTarget.publishTags.set(['production', 'staging', 'dev', 'test', 'build-123', 'pr-456'])

        then:
        publishTarget.publishTags.get() == ['production', 'staging', 'dev', 'test', 'build-123', 'pr-456']
        noExceptionThrown()
    }

    // ===== PROVIDER-BASED PUBLISHTAGS TESTS =====

    def "publishTags accepts provider"() {
        given:
        def tagProvider = project.provider { ['v1.0', 'latest'] }

        when:
        publishTarget.publishTags(tagProvider)

        then:
        publishTarget.publishTags.get() == ['v1.0', 'latest']
    }
}