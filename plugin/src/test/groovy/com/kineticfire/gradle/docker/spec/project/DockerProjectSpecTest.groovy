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
 * Unit tests for DockerProjectSpec
 */
class DockerProjectSpecTest extends Specification {

    def project
    def dockerProjectSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        dockerProjectSpec = project.objects.newInstance(DockerProjectSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor creates spec instance"() {
        expect:
        dockerProjectSpec != null
    }

    def "initializeNestedSpecs creates all nested specs"() {
        when:
        dockerProjectSpec.initializeNestedSpecs()

        then:
        dockerProjectSpec.image.present
        dockerProjectSpec.image.get() instanceof ProjectImageSpec
        dockerProjectSpec.test.present
        dockerProjectSpec.test.get() instanceof ProjectTestSpec
        dockerProjectSpec.onSuccess.present
        dockerProjectSpec.onSuccess.get() instanceof ProjectSuccessSpec
        dockerProjectSpec.onFailure.present
        dockerProjectSpec.onFailure.get() instanceof ProjectFailureSpec
    }

    def "initializeNestedSpecs is idempotent"() {
        when:
        dockerProjectSpec.initializeNestedSpecs()
        def firstImage = dockerProjectSpec.image.get()
        dockerProjectSpec.initializeNestedSpecs()
        def secondImage = dockerProjectSpec.image.get()

        then:
        firstImage.is(secondImage)
    }

    // ===== DSL CLOSURE TESTS =====

    def "image closure configures ProjectImageSpec"() {
        when:
        dockerProjectSpec.image {
            name.set('my-app')
            tags.set(['1.0.0', 'latest'])
        }

        then:
        dockerProjectSpec.image.present
        dockerProjectSpec.image.get().name.get() == 'my-app'
        dockerProjectSpec.image.get().tags.get() == ['1.0.0', 'latest']
    }

    def "test closure configures ProjectTestSpec"() {
        when:
        dockerProjectSpec.test {
            compose.set('docker-compose.yml')
            waitForHealthy.set(['app', 'db'])
        }

        then:
        dockerProjectSpec.test.present
        dockerProjectSpec.test.get().compose.get() == 'docker-compose.yml'
        dockerProjectSpec.test.get().waitForHealthy.get() == ['app', 'db']
    }

    def "onSuccess closure configures ProjectSuccessSpec"() {
        when:
        dockerProjectSpec.onSuccess {
            additionalTags.set(['tested', 'stable'])
            saveFile.set('build/image.tar.gz')
        }

        then:
        dockerProjectSpec.onSuccess.present
        dockerProjectSpec.onSuccess.get().additionalTags.get() == ['tested', 'stable']
        dockerProjectSpec.onSuccess.get().saveFile.get() == 'build/image.tar.gz'
    }

    def "onFailure closure configures ProjectFailureSpec"() {
        when:
        dockerProjectSpec.onFailure {
            additionalTags.set(['failed', 'needs-review'])
        }

        then:
        dockerProjectSpec.onFailure.present
        dockerProjectSpec.onFailure.get().additionalTags.get() == ['failed', 'needs-review']
    }

    // ===== DSL ACTION TESTS =====

    def "image action configures ProjectImageSpec"() {
        when:
        dockerProjectSpec.image({ ProjectImageSpec imageSpec ->
            imageSpec.name.set('action-app')
            imageSpec.jarFrom.set(':app:jar')
        } as org.gradle.api.Action<ProjectImageSpec>)

        then:
        dockerProjectSpec.image.get().name.get() == 'action-app'
        dockerProjectSpec.image.get().jarFrom.get() == ':app:jar'
    }

    def "test action configures ProjectTestSpec"() {
        when:
        dockerProjectSpec.test({ ProjectTestSpec testSpec ->
            testSpec.compose.set('test-compose.yml')
            testSpec.lifecycle.set('method')
        } as org.gradle.api.Action<ProjectTestSpec>)

        then:
        dockerProjectSpec.test.get().compose.get() == 'test-compose.yml'
        dockerProjectSpec.test.get().lifecycle.get() == 'method'
    }

    def "onSuccess action configures ProjectSuccessSpec"() {
        when:
        dockerProjectSpec.onSuccess({ ProjectSuccessSpec successSpec ->
            successSpec.additionalTags.set(['passed'])
            successSpec.publishRegistry.set('ghcr.io')
        } as org.gradle.api.Action<ProjectSuccessSpec>)

        then:
        dockerProjectSpec.onSuccess.get().additionalTags.get() == ['passed']
        dockerProjectSpec.onSuccess.get().publishRegistry.get() == 'ghcr.io'
    }

    def "onFailure action configures ProjectFailureSpec"() {
        when:
        dockerProjectSpec.onFailure({ ProjectFailureSpec failureSpec ->
            failureSpec.additionalTags.set(['build-failed'])
        } as org.gradle.api.Action<ProjectFailureSpec>)

        then:
        dockerProjectSpec.onFailure.get().additionalTags.get() == ['build-failed']
    }

    // ===== IS CONFIGURED TESTS =====

    def "isConfigured returns false before initialization"() {
        expect:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns false when image name not set"() {
        when:
        dockerProjectSpec.initializeNestedSpecs()

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns true when image name is set"() {
        when:
        dockerProjectSpec.image {
            name.set('my-app')
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when sourceRef is set"() {
        when:
        dockerProjectSpec.image {
            sourceRef.set('docker.io/library/nginx:1.25')
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when sourceRefImageName is set"() {
        when:
        dockerProjectSpec.image {
            sourceRefImageName.set('nginx')
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when sourceRefRepository is set"() {
        when:
        dockerProjectSpec.image {
            sourceRefRepository.set('library/nginx')
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete build mode configuration"() {
        when:
        dockerProjectSpec.image {
            name.set('my-service')
            tags.set(['1.0.0', 'latest'])
            jarFrom.set(':service:jar')
            dockerfile.set('docker/Dockerfile')
        }
        dockerProjectSpec.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app'])
            lifecycle.set('class')
        }
        dockerProjectSpec.onSuccess {
            additionalTags.set(['tested', 'stable'])
            saveFile.set('build/images/service.tar.gz')
            publishRegistry.set('ghcr.io')
            publishNamespace.set('myorg')
        }
        dockerProjectSpec.onFailure {
            additionalTags.set(['failed'])
        }

        then:
        dockerProjectSpec.isConfigured() == true
        dockerProjectSpec.image.get().name.get() == 'my-service'
        dockerProjectSpec.image.get().isBuildMode() == true
        dockerProjectSpec.test.get().compose.get() == 'src/integrationTest/resources/compose/app.yml'
        dockerProjectSpec.onSuccess.get().saveFile.get() == 'build/images/service.tar.gz'
        dockerProjectSpec.onFailure.get().additionalTags.get() == ['failed']
    }

    def "complete sourceRef mode configuration"() {
        when:
        dockerProjectSpec.image {
            sourceRefRegistry.set('docker.io')
            sourceRefNamespace.set('library')
            sourceRefImageName.set('nginx')
            sourceRefTag.set('1.25-alpine')
            pullIfMissing.set(true)
            tags.set(['my-nginx', 'latest'])
        }
        dockerProjectSpec.test {
            compose.set('src/integrationTest/resources/compose/nginx.yml')
            waitForHealthy.set(['nginx'])
        }
        dockerProjectSpec.onSuccess {
            additionalTags.set(['verified'])
        }

        then:
        dockerProjectSpec.isConfigured() == true
        dockerProjectSpec.image.get().isSourceRefMode() == true
        dockerProjectSpec.image.get().sourceRefImageName.get() == 'nginx'
        dockerProjectSpec.test.get().waitForHealthy.get() == ['nginx']
        dockerProjectSpec.onSuccess.get().additionalTags.get() == ['verified']
    }

    // ===== MULTIPLE CONFIGURATION CALLS =====

    def "multiple image closure calls accumulate configuration"() {
        when:
        dockerProjectSpec.image {
            name.set('my-app')
        }
        dockerProjectSpec.image {
            tags.set(['1.0.0'])
        }
        dockerProjectSpec.image {
            jarFrom.set(':app:jar')
        }

        then:
        dockerProjectSpec.image.get().name.get() == 'my-app'
        dockerProjectSpec.image.get().tags.get() == ['1.0.0']
        dockerProjectSpec.image.get().jarFrom.get() == ':app:jar'
    }

    def "last value wins when same property set multiple times"() {
        when:
        dockerProjectSpec.image {
            name.set('first-name')
        }
        dockerProjectSpec.image {
            name.set('second-name')
        }

        then:
        dockerProjectSpec.image.get().name.get() == 'second-name'
    }
}
