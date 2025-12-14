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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for Phase 1 additions to ProjectSuccessSpec (multiple publish targets)
 */
class ProjectSuccessSpecPhase1Test extends Specification {

    Project project
    ProjectSuccessSpec spec

    def setup() {
        project = ProjectBuilder.builder().build()
        spec = project.objects.newInstance(ProjectSuccessSpec)
    }

    // ===== PUBLISH TARGETS CONTAINER TESTS =====

    def "publishTargets container is initialized"() {
        expect:
        spec.publishTargets != null
        spec.publishTargets.isEmpty()
    }

    def "publishTargets container is a NamedDomainObjectContainer"() {
        expect:
        spec.publishTargets instanceof org.gradle.api.NamedDomainObjectContainer
    }

    // ===== PUBLISH DSL TESTS =====

    def "publish closure creates named targets"() {
        when:
        spec.publishTargets.create('dockerHub') { target ->
            target.registry.set('docker.io')
        }
        spec.publishTargets.create('privateRegistry') { target ->
            target.registry.set('registry.example.com')
        }

        then:
        spec.publishTargets.size() == 2
        spec.publishTargets.findByName('dockerHub') != null
        spec.publishTargets.findByName('privateRegistry') != null
    }

    def "publish closure configures target properties"() {
        when:
        spec.publishTargets.create('dockerHub') { target ->
            target.registry.set('docker.io')
            target.namespace.set('myorg')
            target.tags.set(['latest', '1.0.0'])
        }

        then:
        def target = spec.publishTargets.findByName('dockerHub')
        target.registry.get() == 'docker.io'
        target.namespace.get() == 'myorg'
        target.tags.get() == ['latest', '1.0.0']
    }

    def "publish closure can configure auth"() {
        when:
        spec.publishTargets.create('dockerHub') { target ->
            target.registry.set('docker.io')
            target.auth {
                username = 'myuser'
                password = 'mysecret'
            }
        }

        then:
        def target = spec.publishTargets.findByName('dockerHub')
        target.auth != null
        target.auth.username.get() == 'myuser'
        target.auth.password.get() == 'mysecret'
    }

    def "publish action creates named targets"() {
        when:
        spec.publish { container ->
            container.create('ghcr') { target ->
                target.registry.set('ghcr.io')
            }
        }

        then:
        spec.publishTargets.size() == 1
        spec.publishTargets.findByName('ghcr') != null
        spec.publishTargets.findByName('ghcr').registry.get() == 'ghcr.io'
    }

    // ===== hasFlatPublishConfig TESTS =====

    def "hasFlatPublishConfig returns false with defaults"() {
        expect:
        !spec.hasFlatPublishConfig()
    }

    def "hasFlatPublishConfig returns true when publishRegistry is set"() {
        when:
        spec.publishRegistry.set('docker.io')

        then:
        spec.hasFlatPublishConfig()
    }

    def "hasFlatPublishConfig returns true when publishNamespace is set"() {
        when:
        spec.publishNamespace.set('myorg')

        then:
        spec.hasFlatPublishConfig()
    }

    def "hasFlatPublishConfig returns true when publishTags is not empty"() {
        when:
        spec.publishTags.set(['latest'])

        then:
        spec.hasFlatPublishConfig()
    }

    def "hasFlatPublishConfig returns false when publishRegistry is empty string"() {
        when:
        spec.publishRegistry.set('')

        then:
        !spec.hasFlatPublishConfig()
    }

    // ===== hasMultiplePublishTargets TESTS =====

    def "hasMultiplePublishTargets returns false when empty"() {
        expect:
        !spec.hasMultiplePublishTargets()
    }

    def "hasMultiplePublishTargets returns true when targets exist"() {
        when:
        spec.publishTargets.create('dockerHub') { target ->
            target.registry.set('docker.io')
        }

        then:
        spec.hasMultiplePublishTargets()
    }

    // ===== validatePublishConfig TESTS =====

    def "validatePublishConfig passes with no config"() {
        when:
        spec.validatePublishConfig()

        then:
        noExceptionThrown()
    }

    def "validatePublishConfig passes with only flat config"() {
        when:
        spec.publishRegistry.set('docker.io')
        spec.publishTags.set(['latest'])
        spec.validatePublishConfig()

        then:
        noExceptionThrown()
    }

    def "validatePublishConfig passes with only publish block"() {
        when:
        spec.publishTargets.create('dockerHub') { target ->
            target.registry.set('docker.io')
        }
        spec.validatePublishConfig()

        then:
        noExceptionThrown()
    }

    def "validatePublishConfig throws when both flat and publish block configured"() {
        when:
        spec.publishRegistry.set('docker.io')
        spec.publishTargets.create('dockerHub') { target ->
            target.registry.set('docker.io')
        }
        spec.validatePublishConfig()

        then:
        def e = thrown(GradleException)
        e.message.contains('Cannot use both flat publish properties')
        e.message.contains('publish { }')
    }

    def "validatePublishConfig throws with publishNamespace and publish block"() {
        when:
        spec.publishNamespace.set('myorg')
        spec.publishTargets.create('target1') { target ->
            target.registry.set('registry.example.com')
        }
        spec.validatePublishConfig()

        then:
        def e = thrown(GradleException)
        e.message.contains('Cannot use both')
    }

    def "validatePublishConfig throws with publishTags and publish block"() {
        when:
        spec.publishTags.set(['v1.0'])
        spec.publishTargets.create('target1') { target ->
            target.registry.set('registry.example.com')
        }
        spec.validatePublishConfig()

        then:
        def e = thrown(GradleException)
        e.message.contains('Cannot use both')
    }

    // ===== EXISTING FUNCTIONALITY TESTS (ENSURING NO REGRESSION) =====

    def "additionalTags has convention of empty list"() {
        expect:
        spec.additionalTags.get() == []
    }

    def "saveCompression has convention of empty string"() {
        expect:
        spec.saveCompression.get() == ''
    }

    def "publishTags has convention of empty list"() {
        expect:
        spec.publishTags.get() == []
    }

    def "save method sets saveFile"() {
        when:
        spec.save('build/images/app.tar.gz')

        then:
        spec.saveFile.get() == 'build/images/app.tar.gz'
    }

    def "save map method sets saveFile and compression"() {
        when:
        spec.save(file: 'build/images/app.tar', compression: 'gzip')

        then:
        spec.saveFile.get() == 'build/images/app.tar'
        spec.saveCompression.get() == 'gzip'
    }
}
