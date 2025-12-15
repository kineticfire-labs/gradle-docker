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

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for ProjectImageSpec
 */
class ProjectImageSpecTest extends Specification {

    def project
    def imageSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        imageSpec = project.objects.newInstance(ProjectImageSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        imageSpec != null
        imageSpec.dockerfile.get() == 'src/main/docker/Dockerfile'
        imageSpec.buildArgs.get() == [:]
        imageSpec.labels.get() == [:]
        imageSpec.sourceRef.get() == ''
        imageSpec.sourceRefRegistry.get() == ''
        imageSpec.sourceRefNamespace.get() == ''
        imageSpec.sourceRefImageName.get() == ''
        imageSpec.sourceRefTag.get() == ''
        imageSpec.sourceRefRepository.get() == ''
        imageSpec.pullIfMissing.get() == false
        imageSpec.registry.get() == ''
        imageSpec.namespace.get() == ''
        imageSpec.tags.get() == ['latest']
        imageSpec.version.get() == ''
    }

    // ===== BUILD MODE PROPERTY TESTS =====

    def "legacyName property works correctly"() {
        when:
        imageSpec.legacyName.set('my-app')

        then:
        imageSpec.legacyName.present
        imageSpec.legacyName.get() == 'my-app'
    }

    def "tags property works correctly"() {
        when:
        imageSpec.tags.set(['1.0.0', 'latest', 'stable'])

        then:
        imageSpec.tags.get() == ['1.0.0', 'latest', 'stable']
    }

    def "version property works correctly"() {
        when:
        imageSpec.version.set('2.0.0')

        then:
        imageSpec.version.get() == '2.0.0'
    }

    def "dockerfile property works correctly"() {
        when:
        imageSpec.dockerfile.set('docker/Dockerfile.prod')

        then:
        imageSpec.dockerfile.get() == 'docker/Dockerfile.prod'
    }

    def "jarFrom property works correctly"() {
        when:
        imageSpec.jarFrom.set(':app:jar')

        then:
        imageSpec.jarFrom.present
        imageSpec.jarFrom.get() == ':app:jar'
    }

    def "contextDir property works correctly"() {
        when:
        imageSpec.contextDir.set('docker/context')

        then:
        imageSpec.contextDir.present
        imageSpec.contextDir.get() == 'docker/context'
    }

    def "buildArgs property works correctly"() {
        when:
        imageSpec.buildArgs.put('VERSION', '1.0.0')
        imageSpec.buildArgs.put('ENV', 'production')

        then:
        imageSpec.buildArgs.get() == [VERSION: '1.0.0', ENV: 'production']
    }

    def "labels property works correctly"() {
        when:
        imageSpec.labels.put('maintainer', 'team@example.com')
        imageSpec.labels.put('version', '1.0')

        then:
        imageSpec.labels.get() == [maintainer: 'team@example.com', version: '1.0']
    }

    // ===== SOURCE REF MODE PROPERTY TESTS =====

    def "sourceRef property works correctly"() {
        when:
        imageSpec.sourceRef.set('docker.io/library/nginx:1.25')

        then:
        imageSpec.sourceRef.get() == 'docker.io/library/nginx:1.25'
    }

    def "sourceRef component properties work correctly"() {
        when:
        imageSpec.sourceRefRegistry.set('docker.io')
        imageSpec.sourceRefNamespace.set('library')
        imageSpec.sourceRefImageName.set('nginx')
        imageSpec.sourceRefTag.set('1.25')

        then:
        imageSpec.sourceRefRegistry.get() == 'docker.io'
        imageSpec.sourceRefNamespace.get() == 'library'
        imageSpec.sourceRefImageName.get() == 'nginx'
        imageSpec.sourceRefTag.get() == '1.25'
    }

    def "sourceRefRepository property works correctly"() {
        when:
        imageSpec.sourceRefRepository.set('library/nginx')

        then:
        imageSpec.sourceRefRepository.get() == 'library/nginx'
    }

    def "pullIfMissing property works correctly"() {
        when:
        imageSpec.pullIfMissing.set(true)

        then:
        imageSpec.pullIfMissing.get() == true
    }

    // ===== COMMON PROPERTY TESTS =====

    def "registry property works correctly"() {
        when:
        imageSpec.registry.set('ghcr.io')

        then:
        imageSpec.registry.get() == 'ghcr.io'
    }

    def "namespace property works correctly"() {
        when:
        imageSpec.namespace.set('myorg')

        then:
        imageSpec.namespace.get() == 'myorg'
    }

    // ===== PULL AUTH TESTS =====

    def "pullAuth closure creates and configures AuthSpec"() {
        when:
        imageSpec.pullAuth {
            username.set('testuser')
            password.set('testpass')
        }

        then:
        imageSpec.pullAuth != null
        imageSpec.pullAuth.username.get() == 'testuser'
        imageSpec.pullAuth.password.get() == 'testpass'
    }

    def "pullAuth action creates and configures AuthSpec"() {
        when:
        imageSpec.pullAuth({ auth ->
            auth.username.set('actionuser')
            auth.password.set('actionpass')
        } as org.gradle.api.Action)

        then:
        imageSpec.pullAuth != null
        imageSpec.pullAuth.username.get() == 'actionuser'
        imageSpec.pullAuth.password.get() == 'actionpass'
    }

    // ===== MODE DETECTION TESTS =====

    def "isSourceRefMode returns true when sourceRef is set"() {
        when:
        imageSpec.sourceRef.set('docker.io/library/nginx:1.25')

        then:
        imageSpec.isSourceRefMode() == true
        imageSpec.isBuildMode() == false
    }

    def "isSourceRefMode returns true when sourceRefImageName is set"() {
        when:
        imageSpec.sourceRefImageName.set('nginx')

        then:
        imageSpec.isSourceRefMode() == true
        imageSpec.isBuildMode() == false
    }

    def "isSourceRefMode returns true when sourceRefRepository is set"() {
        when:
        imageSpec.sourceRefRepository.set('library/nginx')

        then:
        imageSpec.isSourceRefMode() == true
        imageSpec.isBuildMode() == false
    }

    def "isSourceRefMode returns false when sourceRef is empty"() {
        expect:
        imageSpec.isSourceRefMode() == false
    }

    def "isBuildMode returns true when jarFrom is set"() {
        when:
        imageSpec.jarFrom.set(':app:jar')

        then:
        imageSpec.isBuildMode() == true
    }

    def "isBuildMode returns true when contextDir is set"() {
        when:
        imageSpec.contextDir.set('docker/context')

        then:
        imageSpec.isBuildMode() == true
    }

    def "isBuildMode and isSourceRefMode can both be true if both are configured"() {
        when:
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.sourceRefImageName.set('nginx')

        then:
        // Both modes can be detected independently
        // The translator is responsible for validating mutual exclusivity
        imageSpec.isBuildMode() == true
        imageSpec.isSourceRefMode() == true
    }

    def "isBuildMode returns false when neither jarFrom nor contextDir is set"() {
        expect:
        imageSpec.isBuildMode() == false
    }

    // ===== VERSION DERIVATION TESTS =====

    def "deriveVersion returns explicit version when set"() {
        when:
        imageSpec.version.set('3.0.0')
        imageSpec.tags.set(['1.0.0', 'latest'])

        then:
        imageSpec.deriveVersion() == '3.0.0'
    }

    def "deriveVersion returns first non-latest tag when version not set"() {
        when:
        imageSpec.tags.set(['latest', '1.0.0', 'stable'])

        then:
        imageSpec.deriveVersion() == '1.0.0'
    }

    def "deriveVersion returns empty string when only latest tag exists"() {
        when:
        imageSpec.tags.set(['latest'])

        then:
        imageSpec.deriveVersion() == ''
    }

    def "deriveVersion returns empty string when tags is empty"() {
        when:
        imageSpec.tags.set([])

        then:
        imageSpec.deriveVersion() == ''
    }

    def "deriveVersion returns first tag when no latest tag"() {
        when:
        imageSpec.tags.set(['1.0.0', '2.0.0', 'stable'])

        then:
        imageSpec.deriveVersion() == '1.0.0'
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete build mode configuration"() {
        when:
        imageSpec.imageName.set('my-service')
        imageSpec.tags.set(['1.0.0', 'latest'])
        imageSpec.dockerfile.set('docker/Dockerfile')
        imageSpec.jarFrom.set(':service:jar')
        imageSpec.buildArgs.put('BUILD_VERSION', '1.0.0')
        imageSpec.labels.put('app', 'my-service')
        imageSpec.registry.set('ghcr.io')
        imageSpec.namespace.set('myorg')

        then:
        imageSpec.imageName.get() == 'my-service'
        imageSpec.tags.get() == ['1.0.0', 'latest']
        imageSpec.dockerfile.get() == 'docker/Dockerfile'
        imageSpec.jarFrom.get() == ':service:jar'
        imageSpec.buildArgs.get() == [BUILD_VERSION: '1.0.0']
        imageSpec.labels.get() == [app: 'my-service']
        imageSpec.registry.get() == 'ghcr.io'
        imageSpec.namespace.get() == 'myorg'
        imageSpec.isBuildMode() == true
        imageSpec.isSourceRefMode() == false
    }

    def "complete sourceRef mode configuration"() {
        when:
        imageSpec.sourceRefRegistry.set('docker.io')
        imageSpec.sourceRefNamespace.set('library')
        imageSpec.sourceRefImageName.set('nginx')
        imageSpec.sourceRefTag.set('1.25-alpine')
        imageSpec.pullIfMissing.set(true)
        imageSpec.tags.set(['my-nginx', 'latest'])
        imageSpec.pullAuth {
            username.set('user')
            password.set('pass')
        }

        then:
        imageSpec.sourceRefRegistry.get() == 'docker.io'
        imageSpec.sourceRefNamespace.get() == 'library'
        imageSpec.sourceRefImageName.get() == 'nginx'
        imageSpec.sourceRefTag.get() == '1.25-alpine'
        imageSpec.pullIfMissing.get() == true
        imageSpec.tags.get() == ['my-nginx', 'latest']
        imageSpec.pullAuth.username.get() == 'user'
        imageSpec.isSourceRefMode() == true
        imageSpec.isBuildMode() == false
    }
}
