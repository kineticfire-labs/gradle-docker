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

package com.kineticfire.gradle.docker.spec.project

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for PublishTargetSpec
 */
class PublishTargetSpecTest extends Specification {

    Project project
    PublishTargetSpec spec

    def setup() {
        project = ProjectBuilder.builder().build()
        spec = project.objects.newInstance(PublishTargetSpec, 'dockerHub')
    }

    // ===== NAME TESTS =====

    def "getName returns configured name"() {
        expect:
        spec.getName() == 'dockerHub'
    }

    def "spec implements Named interface"() {
        expect:
        spec.name == 'dockerHub'
    }

    // ===== CONVENTION TESTS =====

    def "registry has convention of empty string"() {
        expect:
        spec.registry.get() == ''
    }

    def "namespace has convention of empty string"() {
        expect:
        spec.namespace.get() == ''
    }

    def "repository has convention of empty string"() {
        expect:
        spec.repository.get() == ''
    }

    def "tags has convention of empty list"() {
        expect:
        spec.tags.get() == []
    }

    def "auth is initially null"() {
        expect:
        spec.auth == null
    }

    // ===== PROPERTY TESTS =====

    def "registry property can be set"() {
        when:
        spec.registry.set('docker.io')

        then:
        spec.registry.get() == 'docker.io'
    }

    def "namespace property can be set"() {
        when:
        spec.namespace.set('myorg')

        then:
        spec.namespace.get() == 'myorg'
    }

    def "repository property can be set"() {
        when:
        spec.repository.set('myorg/myapp')

        then:
        spec.repository.get() == 'myorg/myapp'
    }

    def "tags property can be set"() {
        when:
        spec.tags.set(['latest', '1.0.0'])

        then:
        spec.tags.get() == ['latest', '1.0.0']
    }

    // ===== AUTH DSL TESTS =====

    def "auth closure creates and configures auth spec"() {
        when:
        spec.auth {
            username = 'myuser'
            password = 'mysecret'
        }

        then:
        spec.auth != null
        spec.auth.username.get() == 'myuser'
        spec.auth.password.get() == 'mysecret'
    }

    def "auth action creates and configures auth spec"() {
        when:
        spec.auth { authSpec ->
            authSpec.username.set('myuser')
            authSpec.password.set('mysecret')
        }

        then:
        spec.auth != null
        spec.auth.username.get() == 'myuser'
        spec.auth.password.get() == 'mysecret'
    }

    def "auth closure can be called multiple times"() {
        when:
        spec.auth {
            username = 'user1'
        }
        spec.auth {
            password = 'pass1'
        }

        then:
        spec.auth.username.get() == 'user1'
        spec.auth.password.get() == 'pass1'
    }

    // ===== getPublishTaskName TESTS =====

    def "getPublishTaskName generates correct name"() {
        expect:
        spec.getPublishTaskName() == 'dockerProjectPublishDockerHub'
    }

    def "getPublishTaskName handles different names"() {
        given:
        def privateSpec = project.objects.newInstance(PublishTargetSpec, 'privateRegistry')

        expect:
        privateSpec.getPublishTaskName() == 'dockerProjectPublishPrivateRegistry'
    }

    // ===== isConfigured TESTS =====

    def "isConfigured returns false with default conventions"() {
        expect:
        !spec.isConfigured()
    }

    def "isConfigured returns false when registry is empty"() {
        when:
        spec.namespace.set('myorg')
        spec.tags.set(['latest'])

        then:
        !spec.isConfigured()
    }

    def "isConfigured returns true when registry is set"() {
        when:
        spec.registry.set('docker.io')

        then:
        spec.isConfigured()
    }

    def "isConfigured returns true with full configuration"() {
        when:
        spec.registry.set('registry.example.com')
        spec.namespace.set('myorg')
        spec.tags.set(['latest', '1.0.0'])
        spec.auth {
            username = 'user'
            password = 'pass'
        }

        then:
        spec.isConfigured()
    }

    // ===== MULTIPLE SPECS IN CONTAINER TEST =====

    def "multiple specs can be created with different names"() {
        given:
        def spec1 = project.objects.newInstance(PublishTargetSpec, 'dockerHub')
        def spec2 = project.objects.newInstance(PublishTargetSpec, 'privateRegistry')

        when:
        spec1.registry.set('docker.io')
        spec2.registry.set('registry.example.com')

        then:
        spec1.name == 'dockerHub'
        spec2.name == 'privateRegistry'
        spec1.registry.get() == 'docker.io'
        spec2.registry.get() == 'registry.example.com'
    }
}
