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

    // ===== PULLAUTH BRANCH COVERAGE TESTS =====

    def "pullAuth closure can be called multiple times without recreating"() {
        when:
        imageSpec.pullAuth {
            username.set('user1')
        }
        imageSpec.pullAuth {
            password.set('pass1')
        }

        then:
        imageSpec.pullAuth != null
        imageSpec.pullAuth.username.get() == 'user1'
        imageSpec.pullAuth.password.get() == 'pass1'
    }

    def "pullAuth action can be called multiple times without recreating"() {
        when:
        imageSpec.pullAuth({ auth ->
            auth.username.set('actionuser1')
        } as org.gradle.api.Action)
        imageSpec.pullAuth({ auth ->
            auth.password.set('actionpass1')
        } as org.gradle.api.Action)

        then:
        imageSpec.pullAuth != null
        imageSpec.pullAuth.username.get() == 'actionuser1'
        imageSpec.pullAuth.password.get() == 'actionpass1'
    }

    // ===== MODE DETECTION BRANCH COVERAGE TESTS =====

    def "isSourceRefMode returns false when sourceRef is present but empty"() {
        when:
        imageSpec.sourceRef.set('')

        then:
        imageSpec.isSourceRefMode() == false
    }

    def "isSourceRefMode returns false when sourceRefRepository is present but empty"() {
        when:
        imageSpec.sourceRefRepository.set('')

        then:
        imageSpec.isSourceRefMode() == false
    }

    def "isSourceRefMode returns false when sourceRefImageName is present but empty"() {
        when:
        imageSpec.sourceRefImageName.set('')

        then:
        imageSpec.isSourceRefMode() == false
    }

    def "isBuildMode returns false when jarFrom is present but empty"() {
        when:
        imageSpec.jarFrom.set('')

        then:
        imageSpec.isBuildMode() == false
    }

    def "isBuildMode returns false when contextDir is present but empty"() {
        when:
        imageSpec.contextDir.set('')

        then:
        imageSpec.isBuildMode() == false
    }

    def "isBuildMode returns false when contextTaskName is present but empty"() {
        when:
        imageSpec.contextTaskName.set('')

        then:
        imageSpec.isBuildMode() == false
    }

    // ===== VERSION DERIVATION BRANCH COVERAGE TESTS =====

    def "deriveVersion returns empty string when version is present but empty"() {
        when:
        imageSpec.version.set('')
        imageSpec.tags.set(['latest'])

        then:
        imageSpec.deriveVersion() == ''
    }

    // ===== SOURCEREF DSL BRANCH COVERAGE TESTS =====

    def "sourceRef closure without registry"() {
        when:
        imageSpec.sourceRef {
            namespace = 'library'
            name = 'nginx'
            tag = 'latest'
        }

        then:
        imageSpec.sourceRef.get() == 'library/nginx:latest'
        imageSpec.sourceRefRegistry.get() == ''  // Not set because registry was empty
        imageSpec.sourceRefNamespace.get() == 'library'
        imageSpec.sourceRefImageName.get() == 'nginx'
        imageSpec.sourceRefTag.get() == 'latest'
    }

    def "sourceRef closure with only name no namespace"() {
        when:
        imageSpec.sourceRef {
            name = 'nginx'
        }

        then:
        imageSpec.sourceRef.get() == 'nginx'
        imageSpec.sourceRefImageName.get() == 'nginx'
    }

    def "sourceRef closure with no tag"() {
        when:
        imageSpec.sourceRef {
            registry = 'docker.io'
            name = 'nginx'
        }

        then:
        imageSpec.sourceRef.get() == 'docker.io/nginx'
    }

    // ===== GETEFFECTIVESOURCEREF BRANCH COVERAGE TESTS =====

    def "getEffectiveSourceRef returns empty string when sourceRef is present but empty"() {
        when:
        imageSpec.sourceRef.set('')

        then:
        imageSpec.getEffectiveSourceRef() == ''
    }

    def "getEffectiveSourceRef without repository uses namespace and name"() {
        when:
        imageSpec.sourceRefRegistry.set('docker.io')
        imageSpec.sourceRefNamespace.set('library')
        imageSpec.sourceRefImageName.set('nginx')
        imageSpec.sourceRefTag.set('latest')
        // sourceRefRepository is not set

        then:
        imageSpec.getEffectiveSourceRef() == 'docker.io/library/nginx:latest'
    }

    def "getEffectiveSourceRef when sourceRefRepository is present but empty"() {
        when:
        imageSpec.sourceRefRegistry.set('docker.io')
        imageSpec.sourceRefNamespace.set('library')
        imageSpec.sourceRefImageName.set('nginx')
        imageSpec.sourceRefTag.set('latest')
        imageSpec.sourceRefRepository.set('')  // Explicitly set to empty

        then:
        // Should use namespace+name since repository is empty
        imageSpec.getEffectiveSourceRef() == 'docker.io/library/nginx:latest'
    }

    // ===== ISBUILDMODE ADDITIONAL BRANCH COVERAGE =====

    def "isBuildMode returns true when contextTaskName is set with value"() {
        when:
        imageSpec.contextTaskName.set('prepareContext')

        then:
        imageSpec.isBuildMode() == true
    }

    def "isBuildMode evaluates contextTaskName when others are empty"() {
        when:
        // Ensure jarFrom and contextDir are empty (convention)
        imageSpec.jarFrom.set('')
        imageSpec.contextDir.set('')
        imageSpec.contextTaskName.set('myTask')

        then:
        imageSpec.isBuildMode() == true
    }

    // ===== DERIVEVERSION ADDITIONAL BRANCH COVERAGE =====

    def "deriveVersion when version property is not present"() {
        // version has a convention of empty string
        expect:
        imageSpec.deriveVersion() == ''
    }

    def "deriveVersion returns explicitly set version"() {
        when:
        imageSpec.version.set('2.0.0')

        then:
        imageSpec.deriveVersion() == '2.0.0'
    }

    // ===== ISSOURCEREFMODE SHORT-CIRCUIT COVERAGE =====

    def "isSourceRefMode evaluates all conditions in order with empty first two"() {
        when:
        // First two conditions should fail, third should succeed
        imageSpec.sourceRef.set('')
        imageSpec.sourceRefRepository.set('')
        imageSpec.sourceRefImageName.set('nginx')

        then:
        imageSpec.isSourceRefMode() == true
    }

    def "isSourceRefMode returns true from first condition"() {
        when:
        imageSpec.sourceRef.set('docker.io/nginx:latest')

        then:
        imageSpec.isSourceRefMode() == true
    }

    def "isSourceRefMode returns true from second condition"() {
        when:
        imageSpec.sourceRef.set('')
        imageSpec.sourceRefRepository.set('library/nginx')

        then:
        imageSpec.isSourceRefMode() == true
    }

    // ===== GETEFFECTIVESOURCEREF SHORT-CIRCUIT COVERAGE =====

    def "getEffectiveSourceRef returns value from explicitly set sourceRef"() {
        when:
        imageSpec.sourceRef.set('explicit-ref/image:tag')

        then:
        imageSpec.getEffectiveSourceRef() == 'explicit-ref/image:tag'
    }

    // ===== VALIDATION TESTS =====

    // --- validate() method tests ---

    def "validate passes with valid build mode configuration using jarFrom"() {
        when:
        imageSpec.imageName.set('myapp')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes with valid build mode configuration using contextDir"() {
        when:
        imageSpec.imageName.set('myapp')
        imageSpec.contextDir.set('docker/context')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes with valid build mode configuration using contextTask"() {
        when:
        imageSpec.imageName.set('myapp')
        imageSpec.contextTaskName.set('prepareContext')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes with valid sourceRef mode configuration"() {
        when:
        imageSpec.sourceRef.set('docker.io/library/nginx:latest')
        imageSpec.pullPolicy.set(com.kineticfire.gradle.docker.PullPolicy.IF_MISSING)
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes with repository instead of imageName"() {
        when:
        imageSpec.repository.set('myorg/myapp')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when no context/source is configured (default build)"() {
        when:
        imageSpec.imageName.set('myapp')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    // --- imageName and repository mutual exclusivity tests ---

    def "validate fails when both imageName and repository are set"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.imageName.set('myapp')
        imageSpec.repository.set('myorg/myapp')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("'imageName' and 'repository' are mutually exclusive")
        e.message.contains("testImage")
    }

    def "validate passes when only imageName is set"() {
        when:
        imageSpec.imageName.set('myapp')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when only repository is set"() {
        when:
        imageSpec.repository.set('myorg/myapp')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when imageName is empty and repository is set"() {
        when:
        imageSpec.imageName.set('')
        imageSpec.repository.set('myorg/myapp')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when repository is empty and imageName is set"() {
        when:
        imageSpec.imageName.set('myapp')
        imageSpec.repository.set('')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    // --- context/source mutual exclusivity tests ---

    def "validate fails when jarFrom and contextDir are both set"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.contextDir.set('docker/context')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
        e.message.contains("jarFrom")
        e.message.contains("contextDir")
    }

    def "validate fails when jarFrom and contextTask are both set"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.contextTaskName.set('prepareContext')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
        e.message.contains("jarFrom")
        e.message.contains("contextTask")
    }

    def "validate fails when jarFrom and sourceRef are both set"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.sourceRef.set('docker.io/library/nginx:latest')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
        e.message.contains("jarFrom")
        e.message.contains("sourceRef")
    }

    def "validate fails when contextDir and contextTask are both set"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.contextDir.set('docker/context')
        imageSpec.contextTaskName.set('prepareContext')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
        e.message.contains("contextDir")
        e.message.contains("contextTask")
    }

    def "validate fails when contextDir and sourceRef are both set"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.contextDir.set('docker/context')
        imageSpec.sourceRef.set('docker.io/library/nginx:latest')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
        e.message.contains("contextDir")
        e.message.contains("sourceRef")
    }

    def "validate fails when contextTask and sourceRef are both set"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.contextTaskName.set('prepareContext')
        imageSpec.sourceRef.set('docker.io/library/nginx:latest')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
        e.message.contains("contextTask")
        e.message.contains("sourceRef")
    }

    def "validate fails when three source methods are configured"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.contextDir.set('docker/context')
        imageSpec.sourceRef.set('docker.io/library/nginx:latest')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
    }

    def "validate fails when all four source methods are configured"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.contextDir.set('docker/context')
        imageSpec.contextTaskName.set('prepareContext')
        imageSpec.sourceRef.set('docker.io/library/nginx:latest')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
    }

    def "validate passes when empty jarFrom with other source"() {
        when:
        imageSpec.jarFrom.set('')
        imageSpec.contextDir.set('docker/context')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when empty contextDir with other source"() {
        when:
        imageSpec.contextDir.set('')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when empty contextTaskName with other source"() {
        when:
        imageSpec.contextTaskName.set('')
        imageSpec.contextDir.set('docker/context')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    // --- sourceRef component triggers sourceRef mode ---

    def "validate fails when jarFrom and sourceRefImageName are both set"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.sourceRefImageName.set('nginx')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
        e.message.contains("jarFrom")
        e.message.contains("sourceRef")
    }

    def "validate fails when contextDir and sourceRefRepository are both set"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.contextDir.set('docker/context')
        imageSpec.sourceRefRepository.set('library/nginx')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Multiple source/context methods configured")
        e.message.contains("contextDir")
        e.message.contains("sourceRef")
    }

    // --- pullPolicy validation tests ---

    def "validate fails when pullPolicy is IF_MISSING without sourceRef"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.pullPolicy.set(com.kineticfire.gradle.docker.PullPolicy.IF_MISSING)
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("'pullPolicy' is only applicable in Source Reference Mode")
        e.message.contains("IF_MISSING")
    }

    def "validate fails when pullPolicy is ALWAYS without sourceRef"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.pullPolicy.set(com.kineticfire.gradle.docker.PullPolicy.ALWAYS)
        imageSpec.contextDir.set('docker/context')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("'pullPolicy' is only applicable in Source Reference Mode")
        e.message.contains("ALWAYS")
    }

    def "validate passes when pullPolicy is NEVER without sourceRef"() {
        when:
        imageSpec.pullPolicy.set(com.kineticfire.gradle.docker.PullPolicy.NEVER)
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when pullPolicy is IF_MISSING with sourceRef"() {
        when:
        imageSpec.pullPolicy.set(com.kineticfire.gradle.docker.PullPolicy.IF_MISSING)
        imageSpec.sourceRef.set('docker.io/library/nginx:latest')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when pullPolicy is ALWAYS with sourceRef"() {
        when:
        imageSpec.pullPolicy.set(com.kineticfire.gradle.docker.PullPolicy.ALWAYS)
        imageSpec.sourceRef.set('docker.io/library/nginx:latest')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when pullPolicy is IF_MISSING with sourceRefImageName"() {
        when:
        imageSpec.pullPolicy.set(com.kineticfire.gradle.docker.PullPolicy.IF_MISSING)
        imageSpec.sourceRefImageName.set('nginx')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes with default pullPolicy NEVER and no sourceRef"() {
        when:
        // Default pullPolicy is NEVER, which should be valid in build mode
        imageSpec.imageName.set('myapp')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.validate()

        then:
        noExceptionThrown()
    }

    // --- Error message clarity tests ---

    def "validation error messages include image name"() {
        when:
        imageSpec.setName('myTestImage')
        imageSpec.imageName.set('myapp')
        imageSpec.repository.set('myorg/myapp')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("myTestImage")
    }

    def "validation error messages are actionable with suggestions"() {
        when:
        imageSpec.setName('testImage')
        imageSpec.jarFrom.set(':app:jar')
        imageSpec.contextDir.set('docker/context')
        imageSpec.validate()

        then:
        def e = thrown(org.gradle.api.GradleException)
        e.message.contains("Choose exactly one")
        e.message.contains("Remove all but one")
    }
}
