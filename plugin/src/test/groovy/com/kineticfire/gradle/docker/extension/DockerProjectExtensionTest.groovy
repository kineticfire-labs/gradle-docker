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
        extension.spec.image.present
        extension.spec.test.present
        extension.spec.onSuccess.present
        extension.spec.onFailure.present
    }

    // ===== IMAGE DSL TESTS =====

    def "image closure configures ProjectImageSpec"() {
        when:
        extension.image {
            name.set('my-app')
            tags.set(['1.0.0', 'latest'])
            jarFrom.set(':app:jar')
        }

        then:
        extension.spec.image.get().name.get() == 'my-app'
        extension.spec.image.get().tags.get() == ['1.0.0', 'latest']
        extension.spec.image.get().jarFrom.get() == ':app:jar'
    }

    def "image action configures ProjectImageSpec"() {
        when:
        extension.image({ ProjectImageSpec imageSpec ->
            imageSpec.name.set('action-app')
            imageSpec.dockerfile.set('docker/Dockerfile')
        } as org.gradle.api.Action<ProjectImageSpec>)

        then:
        extension.spec.image.get().name.get() == 'action-app'
        extension.spec.image.get().dockerfile.get() == 'docker/Dockerfile'
    }

    def "image closure configures sourceRef mode"() {
        when:
        extension.image {
            sourceRefRegistry.set('docker.io')
            sourceRefNamespace.set('library')
            sourceRefImageName.set('nginx')
            sourceRefTag.set('1.25')
            pullIfMissing.set(true)
        }

        then:
        extension.spec.image.get().sourceRefRegistry.get() == 'docker.io'
        extension.spec.image.get().sourceRefNamespace.get() == 'library'
        extension.spec.image.get().sourceRefImageName.get() == 'nginx'
        extension.spec.image.get().sourceRefTag.get() == '1.25'
        extension.spec.image.get().pullIfMissing.get() == true
        extension.spec.image.get().isSourceRefMode() == true
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
        extension.image {
            name.set('my-service')
            tags.set(['1.0.0', 'latest'])
            jarFrom.set(':service:jar')
            dockerfile.set('docker/Dockerfile')
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
        extension.spec.image.get().isBuildMode() == true
        extension.spec.image.get().name.get() == 'my-service'
        extension.spec.test.get().compose.get() == 'src/integrationTest/resources/compose/app.yml'
        extension.spec.onSuccess.get().saveFile.get() == 'build/images/service.tar.gz'
        extension.spec.onFailure.get().additionalTags.get() == ['failed']
    }

    def "complete sourceRef mode configuration via extension"() {
        when:
        extension.image {
            sourceRefRegistry.set('docker.io')
            sourceRefNamespace.set('library')
            sourceRefImageName.set('nginx')
            sourceRefTag.set('1.25-alpine')
            pullIfMissing.set(true)
            tags.set(['my-nginx', 'latest'])
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
        extension.spec.image.get().isSourceRefMode() == true
        extension.spec.image.get().sourceRefImageName.get() == 'nginx'
    }

    // ===== SPEC ACCESS TESTS =====

    def "getSpec returns the underlying DockerProjectSpec"() {
        when:
        extension.image {
            name.set('test-app')
        }
        def spec = extension.spec

        then:
        spec != null
        spec.image.get().name.get() == 'test-app'
    }

    def "spec is same instance on multiple access"() {
        when:
        def spec1 = extension.spec
        def spec2 = extension.spec

        then:
        spec1.is(spec2)
    }

    // ===== MULTIPLE CONFIGURATION CALLS =====

    def "multiple closure calls accumulate configuration"() {
        when:
        extension.image {
            name.set('my-app')
        }
        extension.image {
            tags.set(['1.0.0'])
        }
        extension.image {
            jarFrom.set(':app:jar')
        }

        then:
        extension.spec.image.get().name.get() == 'my-app'
        extension.spec.image.get().tags.get() == ['1.0.0']
        extension.spec.image.get().jarFrom.get() == ':app:jar'
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
            image {
                name.set('project-app')
            }
        }

        then:
        def ext = testProject.extensions.getByType(DockerProjectExtension)
        ext.spec.image.get().name.get() == 'project-app'
    }
}
