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

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Additional unit tests for PublishTarget to achieve 100% coverage
 * Focuses on edge cases and uncovered branches
 */
class PublishTargetAdditionalTest extends Specification {

    def project
    def publishTarget

    def setup() {
        project = ProjectBuilder.builder().build()
        publishTarget = project.objects.newInstance(PublishTarget, 'testTarget', project.objects)
    }

    // ===== ADDITIONAL REGISTRY CONSISTENCY VALIDATION TESTS =====
    // These tests cover the missed branch in line 159 of PublishTarget.groovy

    def "validateRegistryConsistency succeeds when registry with port matches repository registry with port"() {
        when:
        publishTarget.registry.set('localhost:5000')
        publishTarget.repository.set('localhost:5000/myuser/myapp')

        then:
        publishTarget.validateRegistryConsistency() // Should not throw
        noExceptionThrown()
    }

    def "validateRegistryConsistency succeeds when custom registry with port matches repository"() {
        when:
        publishTarget.registry.set('myregistry.com:8080')
        publishTarget.repository.set('myregistry.com:8080/team/project')

        then:
        publishTarget.validateRegistryConsistency()
        noExceptionThrown()
    }

    def "validateRegistryConsistency succeeds when repository has no slash"() {
        when:
        publishTarget.registry.set('docker.io')
        publishTarget.repository.set('myapp')

        then:
        publishTarget.validateRegistryConsistency()
        noExceptionThrown()
    }

    def "validateRegistryConsistency succeeds when repository part without dot or colon"() {
        when:
        publishTarget.registry.set('docker.io')
        publishTarget.repository.set('myuser/myapp')

        then:
        publishTarget.validateRegistryConsistency()
        noExceptionThrown()
    }

    // ===== ADDITIONAL VALIDATION EDGE CASES =====

    def "validateRegistry succeeds with repository containing port"() {
        when:
        publishTarget.repository.set('localhost:5000/myuser/myapp')

        then:
        publishTarget.validateRegistry()
        noExceptionThrown()
    }

    def "validateRegistry fails when only tags set without registry"() {
        when:
        publishTarget.publishTags.set(['v1.0', 'latest'])
        publishTarget.validateRegistry()

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Registry must be explicitly specified")
    }

    def "validateRegistry fails when only namespace set without registry"() {
        when:
        publishTarget.namespace.set('myorg')
        publishTarget.validateRegistry()

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Registry must be explicitly specified")
    }

    def "validateRegistry fails when only imageName set without registry"() {
        when:
        publishTarget.imageName.set('myapp')
        publishTarget.validateRegistry()

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Registry must be explicitly specified")
    }

    def "validateRegistry fails when only repository with path but not fully qualified"() {
        when:
        publishTarget.repository.set('myorg/myapp')
        publishTarget.validateRegistry()

        then:
        def exception = thrown(org.gradle.api.GradleException)
        exception.message.contains("Registry must be explicitly specified")
    }

    def "validateRegistry succeeds with fully qualified repository containing dot"() {
        when:
        publishTarget.repository.set('registry.example.com/myuser/myapp')

        then:
        publishTarget.validateRegistry()
        noExceptionThrown()
    }

    // ===== TAGS DSL METHOD TESTS =====

    def "tags() DSL method sets publishTags"() {
        when:
        publishTarget.tags(['v1.0', 'v2.0', 'latest'])

        then:
        publishTarget.tags.get() == ['v1.0', 'v2.0', 'latest']
        publishTarget.publishTags.get() == ['v1.0', 'v2.0', 'latest']
    }

    def "publishTags() method with list sets tags"() {
        when:
        publishTarget.publishTags(['alpha', 'beta', 'stable'])

        then:
        publishTarget.publishTags.get() == ['alpha', 'beta', 'stable']
    }

    def "publishTags() method with provider sets tags"() {
        given:
        def tagsProvider = project.provider { ['dev', 'test', 'prod'] }

        when:
        publishTarget.publishTags(tagsProvider)

        then:
        publishTarget.publishTags.get() == ['dev', 'test', 'prod']
    }

    // ===== PROPERTY CONVENTIONS TESTS =====

    def "registry property has empty string convention"() {
        expect:
        publishTarget.registry.present
        publishTarget.registry.get() == ""
    }

    def "namespace property has empty string convention"() {
        expect:
        publishTarget.namespace.present
        publishTarget.namespace.get() == ""
    }

    def "imageName property has empty string convention"() {
        expect:
        publishTarget.imageName.present
        publishTarget.imageName.get() == ""
    }

    def "repository property has empty string convention"() {
        expect:
        publishTarget.repository.present
        publishTarget.repository.get() == ""
    }

    def "publishTags property has empty list convention"() {
        expect:
        publishTarget.publishTags.present
        publishTarget.publishTags.get() == []
    }
}
