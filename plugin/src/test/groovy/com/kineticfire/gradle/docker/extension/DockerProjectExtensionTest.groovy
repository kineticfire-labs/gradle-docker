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

package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.Lifecycle
import com.kineticfire.gradle.docker.spec.project.ProjectImageSpec
import com.kineticfire.gradle.docker.spec.project.ProjectTestSpec
import com.kineticfire.gradle.docker.spec.project.ProjectSuccessSpec
import com.kineticfire.gradle.docker.spec.project.ProjectFailureSpec
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for DockerProjectExtension
 */
class DockerProjectExtensionTest extends Specification {

    def project
    def extension

    def setup() {
        project = ProjectBuilder.builder().build()
        extension = project.objects.newInstance(DockerProjectExtension)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "extension can be created"() {
        expect:
        extension != null
        extension.spec != null
    }

    def "spec is initialized with nested specs"() {
        expect:
        extension.spec.images != null
        extension.spec.test.present
        extension.spec.onSuccess.present
        extension.spec.onFailure.present
    }

    // ===== IMAGES DSL TESTS =====

    def "images closure creates named image specs"() {
        when:
        extension.images {
            myApp {
                imageName.set('my-app')
                tags.set(['1.0.0', 'latest'])
                jarFrom.set(':app:jar')
            }
        }

        then:
        println "DEBUG: Container size = ${extension.spec.images.size()}"
        println "DEBUG: Container names = ${extension.spec.images.names}"
        extension.spec.images.each { item ->
            println "DEBUG: Item - name property would be: ${item.blockName.getOrNull()}"
        }
        extension.spec.images.size() == 1
        extension.spec.images.getByName('myApp').imageName.get() == 'my-app'
        extension.spec.images.getByName('myApp').tags.get() == ['1.0.0', 'latest']
        extension.spec.images.getByName('myApp').jarFrom.get() == ':app:jar'
    }

    def "images action creates named image specs"() {
        when:
        extension.images({ NamedDomainObjectContainer<ProjectImageSpec> images ->
            images.create('actionApp') { imageSpec ->
                imageSpec.imageName.set('action-app')
                imageSpec.dockerfile.set('docker/Dockerfile')
            }
        } as org.gradle.api.Action<NamedDomainObjectContainer<ProjectImageSpec>>)

        then:
        extension.spec.images.size() == 1
        extension.spec.images.getByName('actionApp').imageName.get() == 'action-app'
        extension.spec.images.getByName('actionApp').dockerfile.get() == 'docker/Dockerfile'
    }

    def "images closure configures sourceRef mode"() {
        when:
        extension.images {
            nginx {
                sourceRefRegistry.set('docker.io')
                sourceRefNamespace.set('library')
                sourceRefImageName.set('nginx')
                sourceRefTag.set('1.25')
                pullIfMissing.set(true)
            }
        }

        then:
        def imageSpec = extension.spec.images.getByName('nginx')
        imageSpec.sourceRefRegistry.get() == 'docker.io'
        imageSpec.sourceRefNamespace.get() == 'library'
        imageSpec.sourceRefImageName.get() == 'nginx'
        imageSpec.sourceRefTag.get() == '1.25'
        imageSpec.pullIfMissing.get() == true
        imageSpec.isSourceRefMode() == true
    }

    def "blockName is automatically set from DSL block name"() {
        when:
        extension.images {
            myAppImage {
                imageName.set('my-app')
            }
        }

        then:
        extension.spec.images.getByName('myAppImage').blockName.get() == 'myAppImage'
    }

    // ===== TEST DSL TESTS =====

    def "test closure configures ProjectTestSpec"() {
        when:
        extension.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app', 'db'])
            lifecycle.set(Lifecycle.CLASS)
        }

        then:
        extension.spec.test.get().compose.get() == 'src/integrationTest/resources/compose/app.yml'
        extension.spec.test.get().waitForHealthy.get() == ['app', 'db']
        extension.spec.test.get().lifecycle.get() == Lifecycle.CLASS
    }

    def "test action configures ProjectTestSpec"() {
        when:
        extension.test({ ProjectTestSpec testSpec ->
            testSpec.compose.set('docker-compose.yml')
            testSpec.lifecycle.set(Lifecycle.METHOD)
            testSpec.timeoutSeconds.set(120)
        } as org.gradle.api.Action<ProjectTestSpec>)

        then:
        extension.spec.test.get().compose.get() == 'docker-compose.yml'
        extension.spec.test.get().lifecycle.get() == Lifecycle.METHOD
        extension.spec.test.get().timeoutSeconds.get() == 120
    }

    // ===== ON SUCCESS DSL TESTS =====

    def "onSuccess closure configures ProjectSuccessSpec"() {
        when:
        extension.onSuccess {
            additionalTags.set(['tested', 'stable'])
            saveFile.set('build/images/app.tar.gz')
            publishRegistry.set('ghcr.io')
        }

        then:
        extension.spec.onSuccess.get().additionalTags.get() == ['tested', 'stable']
        extension.spec.onSuccess.get().saveFile.get() == 'build/images/app.tar.gz'
        extension.spec.onSuccess.get().publishRegistry.get() == 'ghcr.io'
    }

    def "onSuccess action configures ProjectSuccessSpec"() {
        when:
        extension.onSuccess({ ProjectSuccessSpec successSpec ->
            successSpec.additionalTags.set(['passed'])
            successSpec.publishNamespace.set('myorg')
        } as org.gradle.api.Action<ProjectSuccessSpec>)

        then:
        extension.spec.onSuccess.get().additionalTags.get() == ['passed']
        extension.spec.onSuccess.get().publishNamespace.get() == 'myorg'
    }

    // ===== ON FAILURE DSL TESTS =====

    def "onFailure closure configures ProjectFailureSpec"() {
        when:
        extension.onFailure {
            additionalTags.set(['failed', 'needs-review'])
        }

        then:
        extension.spec.onFailure.get().additionalTags.get() == ['failed', 'needs-review']
    }

    def "onFailure action configures ProjectFailureSpec"() {
        when:
        extension.onFailure({ ProjectFailureSpec failureSpec ->
            failureSpec.additionalTags.set(['build-failed'])
        } as org.gradle.api.Action<ProjectFailureSpec>)

        then:
        extension.spec.onFailure.get().additionalTags.get() == ['build-failed']
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete build mode configuration via extension"() {
        when:
        extension.images {
            myService {
                imageName.set('my-service')
                tags.set(['1.0.0', 'latest'])
                jarFrom.set(':service:jar')
                dockerfile.set('docker/Dockerfile')
            }
        }
        extension.test {
            compose.set('src/integrationTest/resources/compose/app.yml')
            waitForHealthy.set(['app'])
        }
        extension.onSuccess {
            additionalTags.set(['tested'])
            saveFile.set('build/images/service.tar.gz')
        }
        extension.onFailure {
            additionalTags.set(['failed'])
        }

        then:
        extension.spec.isConfigured() == true
        def imageSpec = extension.spec.images.getByName('myService')
        imageSpec.isBuildMode() == true
        imageSpec.imageName.get() == 'my-service'
        extension.spec.test.get().compose.get() == 'src/integrationTest/resources/compose/app.yml'
        extension.spec.onSuccess.get().saveFile.get() == 'build/images/service.tar.gz'
        extension.spec.onFailure.get().additionalTags.get() == ['failed']
    }

    def "complete sourceRef mode configuration via extension"() {
        when:
        extension.images {
            nginx {
                sourceRefRegistry.set('docker.io')
                sourceRefNamespace.set('library')
                sourceRefImageName.set('nginx')
                sourceRefTag.set('1.25-alpine')
                pullIfMissing.set(true)
                tags.set(['my-nginx', 'latest'])
            }
        }
        extension.test {
            compose.set('src/integrationTest/resources/compose/nginx.yml')
            waitForHealthy.set(['nginx'])
        }
        extension.onSuccess {
            additionalTags.set(['verified'])
        }

        then:
        extension.spec.isConfigured() == true
        def imageSpec = extension.spec.images.getByName('nginx')
        imageSpec.isSourceRefMode() == true
        imageSpec.sourceRefImageName.get() == 'nginx'
    }

    // ===== SPEC ACCESS TESTS =====

    def "getSpec returns the underlying DockerProjectSpec"() {
        when:
        extension.images {
            testApp {
                imageName.set('test-app')
            }
        }
        def spec = extension.spec

        then:
        spec != null
        spec.images.getByName('testApp').imageName.get() == 'test-app'
    }

    def "spec is same instance on multiple access"() {
        when:
        def spec1 = extension.spec
        def spec2 = extension.spec

        then:
        spec1.is(spec2)
    }

    // ===== MULTIPLE IMAGE CONFIGURATION TESTS =====

    def "multiple images can be configured"() {
        when:
        extension.images {
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
        extension.spec.images.size() == 2
        extension.spec.images.getByName('app').imageName.get() == 'my-app'
        extension.spec.images.getByName('db').imageName.get() == 'my-db'
    }

    def "getPrimaryImage returns single image when only one defined"() {
        when:
        extension.images {
            myApp {
                imageName.set('my-app')
            }
        }

        then:
        def primary = extension.spec.primaryImage
        primary != null
        primary.imageName.get() == 'my-app'
    }

    def "getPrimaryImage returns image marked as primary"() {
        when:
        extension.images {
            app {
                imageName.set('my-app')
                primary.set(true)
            }
            db {
                imageName.set('my-db')
            }
        }

        then:
        def primary = extension.spec.primaryImage
        primary != null
        primary.imageName.get() == 'my-app'
    }

    // ===== EXTENSION REGISTRATION TEST =====

    def "extension can be registered on project"() {
        given:
        def testProject = ProjectBuilder.builder().build()

        when:
        def ext = testProject.extensions.create('dockerProject', DockerProjectExtension)

        then:
        ext != null
        testProject.extensions.findByName('dockerProject') != null
    }

    def "extension can be configured via project extensions"() {
        given:
        def testProject = ProjectBuilder.builder().build()
        testProject.extensions.create('dockerProject', DockerProjectExtension)

        when:
        testProject.dockerProject {
            images {
                projectApp {
                    imageName.set('project-app')
                }
            }
        }

        then:
        def ext = testProject.extensions.getByType(DockerProjectExtension)
        ext.spec.images.getByName('projectApp').imageName.get() == 'project-app'
    }

    // ===== COVERAGE ENHANCEMENT TESTS =====

    def "getImages returns the images container directly"() {
        when:
        extension.images {
            myApp {
                imageName.set('my-app')
            }
            myDb {
                imageName.set('my-db')
            }
        }
        def imagesContainer = extension.images

        then:
        imagesContainer != null
        imagesContainer.size() == 2
        imagesContainer.getByName('myApp').imageName.get() == 'my-app'
        imagesContainer.getByName('myDb').imageName.get() == 'my-db'
    }
}
