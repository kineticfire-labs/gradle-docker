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

import com.kineticfire.gradle.docker.Lifecycle
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
        dockerProjectSpec.images != null
        dockerProjectSpec.images.size() == 0
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
        def firstImages = dockerProjectSpec.images
        dockerProjectSpec.initializeNestedSpecs()
        def secondImages = dockerProjectSpec.images

        then:
        firstImages.is(secondImages)
    }

    // ===== DSL CLOSURE TESTS =====

    def "images closure creates named image specs"() {
        when:
        dockerProjectSpec.images {
            myApp {
                imageName.set('my-app')
                tags.set(['1.0.0', 'latest'])
            }
        }

        then:
        dockerProjectSpec.images.size() == 1
        dockerProjectSpec.images.getByName('myApp').imageName.get() == 'my-app'
        dockerProjectSpec.images.getByName('myApp').tags.get() == ['1.0.0', 'latest']
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

    def "images action creates named image specs"() {
        when:
        dockerProjectSpec.images({ images ->
            images.create('actionApp') { imageSpec ->
                imageSpec.imageName.set('action-app')
                imageSpec.jarFrom.set(':app:jar')
            }
        } as org.gradle.api.Action)

        then:
        dockerProjectSpec.images.getByName('actionApp').imageName.get() == 'action-app'
        dockerProjectSpec.images.getByName('actionApp').jarFrom.get() == ':app:jar'
    }

    def "test action configures ProjectTestSpec"() {
        when:
        dockerProjectSpec.test({ ProjectTestSpec testSpec ->
            testSpec.compose.set('test-compose.yml')
            testSpec.lifecycle.set(Lifecycle.METHOD)
        } as org.gradle.api.Action<ProjectTestSpec>)

        then:
        dockerProjectSpec.test.get().compose.get() == 'test-compose.yml'
        dockerProjectSpec.test.get().lifecycle.get() == Lifecycle.METHOD
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

    def "isConfigured returns false when no images added"() {
        when:
        dockerProjectSpec.initializeNestedSpecs()

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns true when image with imageName is added"() {
        when:
        dockerProjectSpec.images {
            myApp {
                imageName.set('my-app')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when image with sourceRef is added"() {
        when:
        dockerProjectSpec.images {
            nginx {
                sourceRef.set('docker.io/library/nginx:1.25')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when image with sourceRefImageName is added"() {
        when:
        dockerProjectSpec.images {
            nginx {
                sourceRefImageName.set('nginx')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when image with sourceRefRepository is added"() {
        when:
        dockerProjectSpec.images {
            nginx {
                sourceRefRepository.set('library/nginx')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete build mode configuration"() {
        when:
        dockerProjectSpec.images {
            myService {
                imageName.set('my-service')
                tags.set(['1.0.0', 'latest'])
                jarFrom.set(':service:jar')
                dockerfile.set('docker/Dockerfile')
            }
        }
        dockerProjectSpec.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app'])
            lifecycle.set(Lifecycle.CLASS)
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
        def imageSpec = dockerProjectSpec.images.getByName('myService')
        imageSpec.imageName.get() == 'my-service'
        imageSpec.isBuildMode() == true
        dockerProjectSpec.test.get().compose.get() == 'src/integrationTest/resources/compose/app.yml'
        dockerProjectSpec.onSuccess.get().saveFile.get() == 'build/images/service.tar.gz'
        dockerProjectSpec.onFailure.get().additionalTags.get() == ['failed']
    }

    def "complete sourceRef mode configuration"() {
        when:
        dockerProjectSpec.images {
            nginx {
                sourceRefRegistry.set('docker.io')
                sourceRefNamespace.set('library')
                sourceRefImageName.set('nginx')
                sourceRefTag.set('1.25-alpine')
                pullIfMissing.set(true)
                tags.set(['my-nginx', 'latest'])
            }
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
        def imageSpec = dockerProjectSpec.images.getByName('nginx')
        imageSpec.isSourceRefMode() == true
        imageSpec.sourceRefImageName.get() == 'nginx'
        dockerProjectSpec.test.get().waitForHealthy.get() == ['nginx']
        dockerProjectSpec.onSuccess.get().additionalTags.get() == ['verified']
    }

    // ===== MULTIPLE IMAGES TESTS =====

    def "multiple images can be configured"() {
        when:
        dockerProjectSpec.images {
            app {
                imageName.set('my-app')
                jarFrom.set(':app:jar')
                primary.set(true)
            }
            db {
                imageName.set('my-db')
                contextDir.set('src/main/docker/db')
            }
        }

        then:
        dockerProjectSpec.images.size() == 2
        dockerProjectSpec.images.getByName('app').imageName.get() == 'my-app'
        dockerProjectSpec.images.getByName('db').imageName.get() == 'my-db'
    }

    def "blockName is automatically set from DSL block name"() {
        when:
        dockerProjectSpec.images {
            myAppImage {
                imageName.set('my-app')
            }
        }

        then:
        dockerProjectSpec.images.getByName('myAppImage').blockName.get() == 'myAppImage'
    }

    // ===== PRIMARY IMAGE TESTS =====

    def "getPrimaryImage returns single image when only one defined"() {
        when:
        dockerProjectSpec.images {
            myApp {
                imageName.set('my-app')
            }
        }

        then:
        def primary = dockerProjectSpec.primaryImage
        primary != null
        primary.imageName.get() == 'my-app'
    }

    def "getPrimaryImage returns image marked as primary"() {
        when:
        dockerProjectSpec.images {
            app {
                imageName.set('my-app')
                primary.set(true)
            }
            db {
                imageName.set('my-db')
            }
        }

        then:
        def primary = dockerProjectSpec.primaryImage
        primary != null
        primary.imageName.get() == 'my-app'
    }

    def "getPrimaryImage returns null when multiple images without primary"() {
        when:
        dockerProjectSpec.images {
            app {
                imageName.set('my-app')
            }
            db {
                imageName.set('my-db')
            }
        }

        then:
        dockerProjectSpec.primaryImage == null
    }

    def "getPrimaryImage returns null when no images"() {
        when:
        dockerProjectSpec.initializeNestedSpecs()

        then:
        dockerProjectSpec.primaryImage == null
    }

    def "getPrimaryImage returns null when imagesContainer is null"() {
        expect:
        // Before calling initializeNestedSpecs, imagesContainer is null
        dockerProjectSpec.primaryImage == null
    }

    // ===== ISCONFIGURED BRANCH COVERAGE TESTS =====

    def "isConfigured returns true when image with legacyName is added"() {
        when:
        dockerProjectSpec.images {
            myImage {
                legacyName.set('my-legacy-app')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when image with jarFrom is added"() {
        when:
        dockerProjectSpec.images {
            myImage {
                jarFrom.set(':app:jar')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when image with contextDir is added"() {
        when:
        dockerProjectSpec.images {
            myImage {
                contextDir.set('docker/context')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when image with repository is added"() {
        when:
        dockerProjectSpec.images {
            myImage {
                repository.set('myorg/myapp')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns false when image has empty imageName"() {
        when:
        dockerProjectSpec.images {
            myImage {
                imageName.set('')
            }
        }

        then:
        // imageName is set but empty, so we need other properties
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns false when image has empty legacyName"() {
        when:
        dockerProjectSpec.images {
            myImage {
                legacyName.set('')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns false when image has empty sourceRef"() {
        when:
        dockerProjectSpec.images {
            myImage {
                sourceRef.set('')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns false when image has empty sourceRefImageName"() {
        when:
        dockerProjectSpec.images {
            myImage {
                sourceRefImageName.set('')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns false when image has empty sourceRefRepository"() {
        when:
        dockerProjectSpec.images {
            myImage {
                sourceRefRepository.set('')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns false when image has empty jarFrom"() {
        when:
        dockerProjectSpec.images {
            myImage {
                jarFrom.set('')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns false when image has empty contextDir"() {
        when:
        dockerProjectSpec.images {
            myImage {
                contextDir.set('')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns false when image has empty repository"() {
        when:
        dockerProjectSpec.images {
            myImage {
                repository.set('')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured evaluates all properties in order when none match"() {
        when:
        // Create image with no meaningful configuration at all
        // This tests all the negative branches in isConfigured
        dockerProjectSpec.images {
            emptyImage {
                // All properties remain at their defaults or empty
                imageName.set('')
                legacyName.set('')
                sourceRef.set('')
                sourceRefImageName.set('')
                sourceRefRepository.set('')
                jarFrom.set('')
                contextDir.set('')
                repository.set('')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == false
    }

    def "isConfigured returns true when image with sourceRef is added"() {
        when:
        dockerProjectSpec.images {
            myImage {
                sourceRef.set('docker.io/library/nginx:latest')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when image with sourceRefImageName is added"() {
        when:
        dockerProjectSpec.images {
            myImage {
                sourceRefImageName.set('nginx')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }

    def "isConfigured returns true when image with sourceRefRepository is added"() {
        when:
        dockerProjectSpec.images {
            myImage {
                sourceRefRepository.set('library/nginx')
            }
        }

        then:
        dockerProjectSpec.isConfigured() == true
    }
}
