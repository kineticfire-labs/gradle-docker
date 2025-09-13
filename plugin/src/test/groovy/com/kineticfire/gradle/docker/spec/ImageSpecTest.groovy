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
 * Unit tests for ImageSpec
 */
class ImageSpecTest extends Specification {

    def project
    def imageSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with name and project"() {
        expect:
        imageSpec != null
        imageSpec.name == 'testImage'
    }

    def "constructor with different names"() {
        given:
        def image1 = project.objects.newInstance(ImageSpec, 'webapp', project)
        def image2 = project.objects.newInstance(ImageSpec, 'api', project)

        expect:
        image1.name == 'webapp'
        image2.name == 'api'
    }

    // ===== BASIC PROPERTY TESTS =====

    def "context property works correctly"() {
        given:
        def contextDir = project.file('.')

        when:
        imageSpec.context.set(contextDir)

        then:
        imageSpec.context.present
        imageSpec.context.get().asFile == contextDir
    }

    def "dockerfile property works correctly"() {
        given:
        def dockerFile = project.file('Dockerfile')

        when:
        imageSpec.dockerfile.set(dockerFile)

        then:
        imageSpec.dockerfile.present
        imageSpec.dockerfile.get().asFile == dockerFile
    }

    def "buildArgs property works correctly"() {
        when:
        imageSpec.buildArgs.set(['VERSION': '1.0', 'ENVIRONMENT': 'production'])

        then:
        imageSpec.buildArgs.present
        imageSpec.buildArgs.get() == ['VERSION': '1.0', 'ENVIRONMENT': 'production']
    }

    def "tags property works correctly"() {
        when:
        imageSpec.tags.set(['latest', 'v1.0', 'stable'])

        then:
        imageSpec.tags.present
        imageSpec.tags.get() == ['latest', 'v1.0', 'stable']
    }

    def "sourceRef property works correctly"() {
        when:
        imageSpec.sourceRef.set('docker.io/library/alpine:3.16')

        then:
        imageSpec.sourceRef.present
        imageSpec.sourceRef.get() == 'docker.io/library/alpine:3.16'
    }

    // ===== SAVE CONFIGURATION TESTS =====

    def "save(Closure) configures save spec"() {
        given:
        def myOutputFile = project.file('myapp.tar')

        when:
        imageSpec.save {
            outputFile.set(myOutputFile)
            compression.set('gzip')
            pullIfMissing.set(true)
        }

        then:
        imageSpec.save.present
        imageSpec.save.get().outputFile.get().asFile == myOutputFile
        imageSpec.save.get().compression.get() == 'gzip'
        imageSpec.save.get().pullIfMissing.get() == true
    }

    def "save(Action) configures save spec"() {
        given:
        def outputFile = project.file('output.tar.gz')

        when:
        imageSpec.save(new Action<SaveSpec>() {
            @Override
            void execute(SaveSpec saveSpec) {
                saveSpec.outputFile.set(outputFile)
                saveSpec.compression.set('bzip2')
                saveSpec.pullIfMissing.set(false)
            }
        })

        then:
        imageSpec.save.present
        imageSpec.save.get().outputFile.get().asFile == outputFile
        imageSpec.save.get().compression.get() == 'bzip2'
        imageSpec.save.get().pullIfMissing.get() == false
    }

    def "save with defaults"() {
        given:
        def myOutputFile = project.file('default.tar')

        when:
        imageSpec.save {
            outputFile.set(myOutputFile)
        }

        then:
        imageSpec.save.present
        imageSpec.save.get().outputFile.get().asFile == myOutputFile
        imageSpec.save.get().compression.get() == 'gzip' // default
        imageSpec.save.get().pullIfMissing.get() == false // default
    }

    // ===== PUBLISH CONFIGURATION TESTS =====

    def "publish(Closure) configures publish spec"() {
        when:
        imageSpec.publish {
            to('dockerhub') {
                repository.set('docker.io/myuser/myapp')
                publishTags.set(['latest', 'v1.0'])
                auth {
                    username.set('myuser')
                    password.set('mypass')
                }
            }
        }

        then:
        imageSpec.publish.present
        imageSpec.publish.get().to.size() == 1
        
        def target = imageSpec.publish.get().to.getByName('dockerhub')
        target.repository.get() == 'docker.io/myuser/myapp'
        target.publishTags.get() == ['latest', 'v1.0']
        target.auth.present
        target.auth.get().username.get() == 'myuser'
        target.auth.get().password.get() == 'mypass'
    }

    def "publish(Action) configures publish spec"() {
        when:
        imageSpec.publish(new Action<PublishSpec>() {
            @Override
            void execute(PublishSpec publishSpec) {
                publishSpec.to('quay') {
                    repository.set('quay.io/myorg/myapp')
                    publishTags.set(['production'])
                }
            }
        })

        then:
        imageSpec.publish.present
        imageSpec.publish.get().to.size() == 1
        
        def target = imageSpec.publish.get().to.getByName('quay')
        target.repository.get() == 'quay.io/myorg/myapp'
        target.publishTags.get() == ['production']
    }

    def "publish with multiple targets"() {
        when:
        imageSpec.publish {
            to('dockerhub') {
                repository.set('docker.io/myuser/app')
                publishTags.set(['latest'])
            }
            to('ghcr') {
                repository.set('ghcr.io/myuser/app')
                publishTags.set(['main'])
            }
        }

        then:
        imageSpec.publish.present
        imageSpec.publish.get().to.size() == 2
        imageSpec.publish.get().to.getByName('dockerhub').repository.get() == 'docker.io/myuser/app'
        imageSpec.publish.get().to.getByName('ghcr').repository.get() == 'ghcr.io/myuser/app'
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def contextDir = project.file('build-context')
        def dockerFile = project.file('custom.Dockerfile')
        def saveFile = project.file('myapp-backup.tar.gz')

        when:
        imageSpec.context.set(contextDir)
        imageSpec.dockerfile.set(dockerFile)
        imageSpec.buildArgs.set(['BUILD_DATE': '2023-01-01', 'VERSION': '1.2.3'])
        imageSpec.tags.set(['1.2.3', 'latest'])
        imageSpec.sourceRef.set('node:18-alpine')
        imageSpec.save {
            outputFile.set(saveFile)
            compression.set('bzip2')
            pullIfMissing.set(true)
        }
        imageSpec.publish {
            to('production') {
                repository.set('prod.registry.com/myapp')
                publishTags.set(['1.2.3', 'stable'])
                auth {
                    registryToken.set('prod-token-123')
                    serverAddress.set('prod.registry.com')
                }
            }
        }

        then:
        imageSpec.name == 'testImage'
        imageSpec.context.get().asFile == contextDir
        imageSpec.dockerfile.get().asFile == dockerFile
        imageSpec.buildArgs.get() == ['BUILD_DATE': '2023-01-01', 'VERSION': '1.2.3']
        imageSpec.tags.get() == ['1.2.3', 'latest']
        imageSpec.sourceRef.get() == 'node:18-alpine'
        imageSpec.save.present
        imageSpec.publish.present
    }

    // ===== OPTIONAL PROPERTIES TESTS =====

    def "optional properties can be unset"() {
        expect:
        !imageSpec.context.present
        !imageSpec.dockerfile.present
        !imageSpec.sourceRef.present
        !imageSpec.save.present
        !imageSpec.publish.present
    }

    def "buildArgs and tags are initially empty"() {
        expect:
        // Properties are created but have empty default values
        imageSpec.buildArgs.get().isEmpty()
        imageSpec.tags.get().isEmpty()
    }

    // ===== EDGE CASES =====

    def "nested specs can be reconfigured"() {
        given:
        def file1 = project.file('save1.tar')
        def file2 = project.file('save2.tar')

        when:
        imageSpec.save {
            outputFile.set(file1)
            compression.set('gzip')
        }

        then:
        imageSpec.save.get().outputFile.get().asFile == file1
        imageSpec.save.get().compression.get() == 'gzip'

        when:
        imageSpec.save {
            outputFile.set(file2)
            compression.set('none')
        }

        then:
        imageSpec.save.get().outputFile.get().asFile == file2
        imageSpec.save.get().compression.get() == 'none'
    }

    def "empty collections are supported"() {
        when:
        imageSpec.buildArgs.set([:])
        imageSpec.tags.set([])

        then:
        imageSpec.buildArgs.get().isEmpty()
        imageSpec.tags.get().isEmpty()
    }

    def "properties can be updated after initial configuration"() {
        given:
        def initialContext = project.file('initial')
        def updatedContext = project.file('updated')

        when:
        imageSpec.context.set(initialContext)
        imageSpec.tags.set(['v1.0'])

        then:
        imageSpec.context.get().asFile == initialContext
        imageSpec.tags.get() == ['v1.0']

        when:
        imageSpec.context.set(updatedContext)
        imageSpec.tags.set(['v2.0', 'latest'])

        then:
        imageSpec.context.get().asFile == updatedContext
        imageSpec.tags.get() == ['v2.0', 'latest']
    }

    // ===== NEW CONTEXT TASK TESTS =====

    def "contextTask property works correctly"() {
        given:
        def copyTask = project.tasks.register('testContextTask', org.gradle.api.tasks.Copy)

        when:
        imageSpec.contextTask.set(copyTask)

        then:
        imageSpec.contextTask.present
        imageSpec.contextTask.get() == copyTask.get()
    }

    def "context(Closure) creates Copy task with correct configuration"() {
        given:
        def srcDir = project.file('src')
        def libsDir = project.file('libs')

        when:
        imageSpec.context {
            from(srcDir)
            from(libsDir) {
                include '*.jar'
            }
        }

        then:
        imageSpec.contextTask.present
        def copyTask = imageSpec.contextTask.get()
        copyTask.name == 'prepareTestImageContext'
        copyTask.group == 'docker'
        copyTask.description.contains('Prepare build context for Docker image')
    }

    def "context(Action) creates Copy task with correct configuration"() {
        given:
        def srcDir = project.file('src')

        when:
        imageSpec.context(new org.gradle.api.Action<org.gradle.api.tasks.Copy>() {
            @Override
            void execute(org.gradle.api.tasks.Copy task) {
                task.from(srcDir)
                task.include('**/*.txt')
            }
        })

        then:
        imageSpec.contextTask.present
        def copyTask = imageSpec.contextTask.get()
        copyTask.name == 'prepareTestImageContext'
        copyTask.group == 'docker'
        copyTask.description.contains('Prepare build context for Docker image')
    }

    def "context method overrides previous contextTask"() {
        given:
        def manualTask = project.tasks.register('manualContext', org.gradle.api.tasks.Copy)
        def srcDir = project.file('src')

        when:
        imageSpec.contextTask.set(manualTask)

        then:
        imageSpec.contextTask.get() == manualTask.get()

        when:
        imageSpec.context {
            from(srcDir)
        }

        then:
        imageSpec.contextTask.present
        imageSpec.contextTask.get() != manualTask.get()
        imageSpec.contextTask.get().name == 'prepareTestImageContext'
    }

    def "multiple context configurations create separate tasks"() {
        given:
        def image1 = project.objects.newInstance(ImageSpec, 'webapp', project)
        def image2 = project.objects.newInstance(ImageSpec, 'api', project)
        def srcDir = project.file('src')

        when:
        image1.context { from(srcDir) }
        image2.context { from(srcDir) }

        then:
        image1.contextTask.present
        image2.contextTask.present
        image1.contextTask.get().name == 'prepareWebappContext'
        image2.contextTask.get().name == 'prepareApiContext'
        image1.contextTask.get() != image2.contextTask.get()
    }
}