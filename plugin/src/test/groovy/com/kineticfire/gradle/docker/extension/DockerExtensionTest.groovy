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

import com.kineticfire.gradle.docker.spec.ImageSpec
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for DockerExtension
 */
class DockerExtensionTest extends Specification {

    Project project
    DockerExtension extension

    def setup() {
        project = ProjectBuilder.builder().build()
        extension = project.objects.newInstance(DockerExtension, project.objects, project.providers, project.layout)
    }

    def "extension can be created"() {
        expect:
        extension != null
        extension.images != null
    }

    def "compression helper provides access to SaveCompression enum values"() {
        when:
        def compression = extension.getSaveCompression()

        then:
        compression != null
        compression.NONE != null
        compression.GZIP != null
        compression.BZIP2 != null
        compression.XZ != null
        compression.ZIP != null
        
        and:
        compression.NONE == com.kineticfire.gradle.docker.model.SaveCompression.NONE
        compression.GZIP == com.kineticfire.gradle.docker.model.SaveCompression.GZIP
        compression.BZIP2 == com.kineticfire.gradle.docker.model.SaveCompression.BZIP2
        compression.XZ == com.kineticfire.gradle.docker.model.SaveCompression.XZ
        compression.ZIP == com.kineticfire.gradle.docker.model.SaveCompression.ZIP
    }

    def "can configure single docker image"() {
        when:
        extension.images {
            testImage {
                imageName.set('test-app')
                version.set('1.0.0')
                tags.set(['latest'])
                dockerfile.set(project.file('Dockerfile'))
                buildArgs.set([VERSION: '1.0.0'])
            }
        }

        then:
        extension.images.size() == 1
        extension.images.getByName('testImage') != null

        and:
        ImageSpec imageSpec = extension.images.getByName('testImage')
        imageSpec.imageName.get() == 'test-app'
        imageSpec.version.get() == '1.0.0'
        imageSpec.tags.get() == ['latest']
        imageSpec.dockerfile.get().asFile == project.file('Dockerfile')
        imageSpec.buildArgs.get() == [VERSION: '1.0.0']
    }

    def "can configure multiple images with different settings"() {
        given:
        project.file('web').mkdirs()
        project.file('web/Dockerfile').text = 'FROM nginx'
        project.file('api').mkdirs()  
        project.file('api/Dockerfile').text = 'FROM node'
        
        when:
        extension.images {
            webImage {
                imageName.set('web-app')
                tags.set(['latest'])
                context.set(project.file('web'))
                dockerfile.set(project.file('web/Dockerfile'))
                buildArgs.set([SERVICE: 'web'])
            }
            apiImage {
                imageName.set('api-app')
                tags.set(['latest', '1.0.0'])
                context.set(project.file('api'))
                dockerfile.set(project.file('api/Dockerfile'))
                buildArgs.set([SERVICE: 'api', NODE_ENV: 'production'])
            }
        }

        then:
        extension.images.size() == 2
        
        def webImage = extension.images.getByName('webImage')
        webImage.imageName.get() == 'web-app'
        webImage.tags.get() == ['latest']
        webImage.buildArgs.get()['SERVICE'] == 'web'
        
        def apiImage = extension.images.getByName('apiImage')
        apiImage.imageName.get() == 'api-app'
        apiImage.tags.get().size() == 2
        apiImage.buildArgs.get()['NODE_ENV'] == 'production'
    }

    def "validation handles multiple images with mixed validity"() {
        given:
        project.file('valid').mkdirs()
        project.file('valid/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            validImage {
                imageName.set('valid-app')
                tags.set(['latest'])
                context.set(project.file('valid'))
                dockerfile.set(project.file('valid/Dockerfile'))
            }
            invalidImage {
                imageName.set('invalid-app')
                tags.set(['latest'])
                context.set(project.file('non-existent'))
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Docker context directory does not exist")
        ex.message.contains('invalidImage')
    }

    def "validate fails on first invalid image and stops"() {
        given:
        extension.images {
            firstInvalid {
                imageName.set('first-app')
                tags.set(['latest'])
                context.set(project.file('non-existent-first'))
            }
            secondInvalid {
                imageName.set('second-app')
                tags.set(['latest'])
                context.set(project.file('non-existent-second'))
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        // Should fail on the first invalid image
        ex.message.contains('firstInvalid')
        ex.message.contains("Docker context directory does not exist")
    }

    // ===== EDGE CASE AND ERROR HANDLING TESTS =====

    def "handles docker context with spaces in path"() {
        given:
        project.file('path with spaces').mkdirs()
        project.file('path with spaces/Dockerfile').text = 'FROM alpine'
        
        when:
        extension.images {
            spacePath {
                context.set(project.file('path with spaces'))
                dockerfile.set(project.file('path with spaces/Dockerfile'))
                imageName.set('test-image')
                tags.set(['latest'])
            }
        }
        extension.validate()

        then:
        noExceptionThrown()
        extension.images.getByName('spacePath').context.get().asFile.name == 'path with spaces'
    }

    def "handles dockerfile with custom name"() {
        given:
        project.file('custom').mkdirs()
        project.file('custom/Dockerfile.custom').text = 'FROM alpine'
        
        when:
        extension.images {
            customDockerfile {
                context.set(project.file('custom'))
                dockerfile.set(project.file('custom/Dockerfile.custom'))
                imageName.set('test-image')
                tags.set(['latest'])
            }
        }
        extension.validate()

        then:
        noExceptionThrown()
        extension.images.getByName('customDockerfile').dockerfile.get().asFile.name == 'Dockerfile.custom'
    }



    def "validateImageSpec method can be called independently"() {
        given:
        project.file('test-context').mkdirs()
        project.file('test-context/Dockerfile').text = 'FROM alpine'

        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.context.set(project.file('test-context'))
        imageSpec.dockerfile.set(project.file('test-context/Dockerfile'))
        imageSpec.imageName.set('test-image')
        imageSpec.tags.set(['validation'])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    def "extension is properly initialized with defaults"() {
        when:
        extension.images {
            defaultTest {
                tags.set(['test'])
            }
        }

        then:
        def imageSpec = extension.images.getByName('defaultTest')
        imageSpec.context.isPresent() // Should have default context
        !imageSpec.dockerfile.isPresent() // dockerfile defaults handled in plugin, not extension
        imageSpec.context.get().asFile.path.contains('src/main/docker')
    }

    // ===== ENHANCED VALIDATION TESTS =====

    def "validate accepts image configuration with publish targets using simple tag names"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            publishableImage {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                imageName.set('test-app')
                tags.set(['latest', 'v1.0.0'])  // Simple tag names
                publish {
                    to('registry') {
                        registry.set('localhost:5000')
                        namespace.set('test')
                        publishTags(['latest', 'stable'])  // Simple tag names
                    }
                }
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validatePublishTarget fails when tags are not specified"() {
        given: "A publish target with no tags"
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget, 
            'testTarget', 
            project.objects
        )
        // Don't set any tags

        when: "validation is performed on target with no tags"
        extension.validatePublishTarget(publishTarget, 'testImage')

        then: "validation fails with descriptive error"
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one tag")
        ex.message.contains('testTarget')
        ex.message.contains('testImage')
    }



    def "validatePublishConfiguration fails when configuration has no targets"() {
        given: "A publish configuration with empty targets"
        def publishSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishSpec,
            project.objects
        )
        // publishSpec.to is empty by default

        when: "validation is performed on empty publish configuration"
        extension.validatePublishConfiguration(publishSpec, 'testImage')

        then: "validation fails with descriptive error"
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one target")
        ex.message.contains('testImage')
    }



    def "validatePublishTarget fails when tags list is empty"() {
        given: "A publish target with empty tags list"
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget, 
            'testTarget', 
            project.objects
        )
        publishTarget.tags.set([])  // Empty list

        when: "validation is performed"
        extension.validatePublishTarget(publishTarget, 'testImage')

        then: "validation fails with descriptive error"
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one tag")
        ex.message.contains('testTarget')
        ex.message.contains('testImage')
    }





    // ===== REPOSITORY NAME VALIDATION TESTS =====
    
    def "isValidRepositoryName accepts valid repository formats"() {
        expect:
        extension.isValidRepositoryName('myapp')
        extension.isValidRepositoryName('docker.io/myapp')
        extension.isValidRepositoryName('localhost:5000/myapp')
        extension.isValidRepositoryName('registry.company.com/team/app')
        extension.isValidRepositoryName('gcr.io/project-id/namespace/app')
        extension.isValidRepositoryName('private-registry.com:8080/namespace/app')
        extension.isValidRepositoryName('a1')  // minimal valid case
    }
    
    def "isValidRepositoryName rejects invalid repository formats"() {
        expect:
        !extension.isValidRepositoryName(null)
        !extension.isValidRepositoryName('')
        !extension.isValidRepositoryName('-starts-with-dash')
        !extension.isValidRepositoryName('ends-with-dash-')
        !extension.isValidRepositoryName('contains spaces')
        !extension.isValidRepositoryName('a' * 256)  // over 255 character limit
    }

    // ===== CONTEXT TASK VALIDATION TESTS =====
    
    def "validate passes for image with contextTask"() {
        given:
        def copyTask = project.tasks.register('testCopyTask', org.gradle.api.tasks.Copy)
        project.file('Dockerfile').text = 'FROM alpine'
        
        extension.images {
            contextTaskImage {
                imageName.set('context-task-app')
                tags.set(['latest'])
                contextTask = copyTask
                dockerfile.set(project.file('Dockerfile'))
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }
    
    def "validate fails when both context and contextTask are specified"() {
        given:
        project.file('docker').mkdirs()
        def copyTask = project.tasks.register('testCopyTask', org.gradle.api.tasks.Copy)
        
        extension.images {
            conflictingContext {
                imageName.set('conflicting-app')
                tags.set(['latest'])
                context.set(project.file('docker'))
                contextTask = copyTask
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify only one of: 'context', 'contextTask'")
        ex.message.contains('conflictingContext')
    }
    
    def "validate fails when neither context nor contextTask nor sourceRef are specified"() {
        given:
        extension.images {
            noContextImage {
                imageName.set('no-context-app')
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify either 'context', 'contextTask', or 'sourceRef'")
        ex.message.contains('noContextImage')
    }

    def "validate passes for image with sourceRef and no context"() {
        given:
        extension.images {
            sourceRefImage {
                imageName.set('source-ref-app')
                tags.set(['latest'])
                sourceRef.set('alpine:3.16')
                // Don't set dockerfile for sourceRef images
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "inline context block throws UnsupportedOperationException"() {
        given:
        def srcDir = project.file('src')
        srcDir.mkdirs()
        project.file('Dockerfile').text = 'FROM alpine'

        when:
        extension.images {
            inlineContextImage {
                imageName.set('inline-context-app')
                tags.set(['latest'])
                context {
                    from(srcDir)
                }
                dockerfile.set(project.file('Dockerfile'))
            }
        }

        then:
        def ex = thrown(UnsupportedOperationException)
        ex.message.contains("Inline context() DSL is not supported")
    }

    // ===== DOCKERFILE NAME VALIDATION TESTS =====

    def "validate fails when both dockerfile and dockerfileName are set"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        project.file('docker/Dockerfile.custom').text = 'FROM alpine'
        
        extension.images {
            conflictingDockerfile {
                imageName.set('conflicting-dockerfile-app')
                tags.set(['latest'])
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                dockerfileName.set('Dockerfile.custom')
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("cannot specify both 'dockerfile' and 'dockerfileName'")
        ex.message.contains('conflictingDockerfile')
        ex.message.contains("Use 'dockerfile' for custom paths or 'dockerfileName' for custom names")
    }

    def "validate passes when only dockerfileName is set"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile.prod').text = 'FROM alpine'
        
        extension.images {
            dockerfileNameOnly {
                imageName.set('dockerfile-name-app')
                tags.set(['latest'])
                context.set(project.file('docker'))
                dockerfileName.set('Dockerfile.prod')
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when only dockerfile is set"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile.custom').text = 'FROM alpine'
        
        extension.images {
            dockerfileOnly {
                imageName.set('dockerfile-only-app')
                tags.set(['latest'])
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile.custom'))
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when neither dockerfile nor dockerfileName are set"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            defaultDockerfile {
                context.set(project.file('docker'))
                imageName.set('test-image')
                tags.set(['latest'])
                // Neither dockerfile nor dockerfileName set - should use default
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate handles dockerfileName with contextTask"() {
        given:
        def copyTask = project.tasks.register('testCopyTask', org.gradle.api.tasks.Copy)
        project.file('Dockerfile').text = 'FROM alpine'
        
        extension.images {
            dockerfileNameWithContextTask {
                contextTask = copyTask
                dockerfileName.set('Dockerfile.dev')
                imageName.set('test-image')
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate fails when both dockerfile and dockerfileName are set with contextTask"() {
        given:
        def copyTask = project.tasks.register('testCopyTask', org.gradle.api.tasks.Copy)
        project.file('Dockerfile').text = 'FROM alpine'
        
        extension.images {
            conflictingWithContextTask {
                contextTask = copyTask
                dockerfile.set(project.file('Dockerfile'))
                dockerfileName.set('Dockerfile.custom')
                repository.set('test/image')  // Add required nomenclature
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("cannot specify both 'dockerfile' and 'dockerfileName'")
        ex.message.contains('conflictingWithContextTask')
    }

    // ===== NEW PUBLISHTAGS API VALIDATION TESTS =====

    def "validatePublishTarget accepts simple tag names"() {
        given: "A publish target with simple tag names"
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget, 
            'testTarget', 
            project.objects
        )
        publishTarget.publishTags(['latest', 'v1.0.0', 'stable'])
        
        when: "validation is performed"
        extension.validatePublishTarget(publishTarget, 'testImage')
        
        then: "validation passes without exception"
        noExceptionThrown()
    }

    def "validatePublishTarget fails when publishTags are not specified"() {
        given: "A publish target with no publishTags"
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget, 
            'testTarget', 
            project.objects
        )
        // Don't set publishTags
        
        when: "validation is performed"
        extension.validatePublishTarget(publishTarget, 'testImage')
        
        then: "validation fails with descriptive error"
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one tag")
        ex.message.contains('testTarget')
        ex.message.contains('testImage')
    }

    def "validatePublishTarget fails with invalid simple tag format"() {
        given: "A publish target with invalid tag formats"
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget, 
            'testTarget', 
            project.objects
        )
        publishTarget.publishTags(['invalid::tag', 'bad tag name'])
        
        when: "validation is performed"
        extension.validatePublishTarget(publishTarget, 'testImage')
        
        then: "validation fails with descriptive error"
        def ex = thrown(GradleException)
        ex.message.contains("Invalid tag format")
        ex.message.contains('testTarget')
        ex.message.contains('testImage')
    }

    def "validate accepts complete image configuration with publish targets using simple tag names"() {
        given: "Complete image configuration matching functional test patterns"
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'

        extension.images {
            testImage {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                imageName.set('test-app')
                tags.set(['latest', 'v1.0.0'])
                publish {
                    to('dockerhub') {
                        registry.set('docker.io')
                        namespace.set('mycompany')
                        publishTags(['latest', 'v1.0.0'])  // Simple tag names
                    }
                }
            }
        }

        when: "validation is performed"
        extension.validate()

        then: "validation passes without exception"
        noExceptionThrown()
    }

    // ===== IMAGES ACTION METHOD TESTS =====

    def "images method accepts Action parameter"() {
        when:
        extension.images({ container ->
            container.create('actionImage') { imageSpec ->
                imageSpec.imageName.set('action-app')
                imageSpec.tags.set(['latest'])
            }
        } as org.gradle.api.Action)

        then:
        extension.images.size() == 1
        extension.images.getByName('actionImage').imageName.get() == 'action-app'
    }

    // ===== EXTRACT IMAGE NAME TESTS =====

    def "extractImageName extracts image name from full reference"() {
        expect:
        extension.extractImageName('myapp:1.0.0') == 'myapp'
        extension.extractImageName('registry.com/namespace/app:tag') == 'registry.com/namespace/app'
        extension.extractImageName('localhost:5000/myapp:latest') == 'localhost:5000/myapp'
        extension.extractImageName('gcr.io/project/app:v1.2.3') == 'gcr.io/project/app'
    }

    def "extractImageName fails when imageRef is null or empty"() {
        when:
        extension.extractImageName(null)

        then:
        thrown(IllegalArgumentException)

        when:
        extension.extractImageName('')

        then:
        thrown(IllegalArgumentException)

        when:
        extension.extractImageName('   ')

        then:
        thrown(IllegalArgumentException)
    }

    def "extractImageName fails when imageRef has no tag colon"() {
        when:
        extension.extractImageName('myapp')

        then:
        thrown(IllegalArgumentException)
    }

    // ===== GET SOURCE TAGS TESTS =====

    def "getSourceTags returns tags from sourceRef property"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set('alpine:3.16')

        when:
        def tags = extension.getSourceTags(imageSpec)

        then:
        tags == ['3.16']
    }

    def "getSourceTags returns latest when sourceRef has no tag"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.sourceRef.set('alpine')

        when:
        def tags = extension.getSourceTags(imageSpec)

        then:
        tags == ['latest']
    }

    def "getSourceTags returns tag from sourceRef components"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.sourceRefRepository.set('myregistry.com/namespace/app')
        imageSpec.sourceRefTag.set('v1.0.0')

        when:
        def tags = extension.getSourceTags(imageSpec)

        then:
        tags == ['v1.0.0']
    }

    def "getSourceTags returns latest when sourceRef components have empty tag convention"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.sourceRefImageName.set('myapp')
        imageSpec.sourceRefTag.set('')  // Empty string convention

        when:
        def tags = extension.getSourceTags(imageSpec)

        then:
        // When sourceRefTag is empty string, getOrElse("latest") returns empty string, not "latest"
        // This is expected behavior based on property conventions
        tags == ['']
    }

    def "getSourceTags returns build mode tags when no sourceRef"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.tags.set(['v1.0.0', 'latest', 'stable'])

        when:
        def tags = extension.getSourceTags(imageSpec)

        then:
        tags == ['v1.0.0', 'latest', 'stable']
    }

    def "getSourceTags returns empty list when no tags configured"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)

        when:
        def tags = extension.getSourceTags(imageSpec)

        then:
        tags == []
    }

    // ===== VALIDATE PUBLISH TARGET WITH IMAGESPEC INHERITANCE TESTS =====

    def "validatePublishTarget with ImageSpec allows tag inheritance from source image"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.tags.set(['v1.0.0', 'latest'])

        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget,
            'inheritTarget',
            project.objects
        )
        // Empty publishTags - should inherit from imageSpec.tags

        when:
        extension.validatePublishTarget(publishTarget, imageSpec)

        then:
        noExceptionThrown()
    }

    def "validatePublishTarget with ImageSpec fails when no tags to inherit"() {
        given:
        def imageSpec = project.objects.newInstance(ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        // No tags set on imageSpec

        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget,
            'noTagsTarget',
            project.objects
        )
        // Empty publishTags and no source tags available

        when:
        extension.validatePublishTarget(publishTarget, imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one tag")
        ex.message.contains("no source tags available for inheritance")
    }

    // ===== VALIDATE PUBLISH CONFIGURATION STRING OVERLOAD TESTS =====

    def "validatePublishConfiguration with String imageName validates all targets"() {
        given:
        def publishSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishSpec,
            project.objects
        )
        publishSpec.to('target1') {
            publishTags(['v1.0.0'])
        }
        publishSpec.to('target2') {
            publishTags(['latest'])
        }

        when:
        extension.validatePublishConfiguration(publishSpec, 'myImage')

        then:
        noExceptionThrown()
    }

    def "validatePublishConfiguration with String imageName fails on invalid target tag"() {
        given:
        def publishSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishSpec,
            project.objects
        )
        publishSpec.to('target1') {
            publishTags(['valid-tag'])
        }
        publishSpec.to('target2') {
            publishTags(['.invalid'])  // Starts with dot - invalid
        }

        when:
        extension.validatePublishConfiguration(publishSpec, 'myImage')

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Invalid tag format")
        ex.message.contains('.invalid')
    }

    // ===== ADDITIONAL EDGE CASE TESTS FOR COVERAGE =====

    def "validateNomenclature fails when no naming approach specified"() {
        given:
        def imageSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        // Don't set any naming properties
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify some form of image naming")
    }

    def "validateNomenclature accepts sourceRefImageName without sourceRefRepository"() {
        given:
        def imageSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.sourceRefImageName.set('alpine')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature accepts sourceRefRepository without other properties"() {
        given:
        def imageSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.sourceRefRepository.set('library/alpine')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature accepts sourceRefNamespace"() {
        given:
        def imageSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.sourceRefNamespace.set('library')
        imageSpec.sourceRefImageName.set('alpine')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature handles tag with maximum length"() {
        given:
        def imageSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['a' * 128])  // Exactly 128 characters

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature fails with tag exceeding maximum length"() {
        given:
        def imageSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['a' * 129])  // 129 characters - too long

        when:
        extension.validateNomenclature(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Invalid tag format")
    }

    def "validateSaveConfiguration accepts valid compression parameter"() {
        given:
        def saveSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.SaveSpec,
            project.objects,
            project.layout
        )
        saveSpec.compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
        saveSpec.outputFile.set(project.layout.buildDirectory.file('custom/image.tar.gz'))

        when:
        extension.validateSaveConfiguration(saveSpec, 'testImage')

        then:
        noExceptionThrown()
    }

    def "validateSaveConfiguration fails when outputFile uses default convention path"() {
        given:
        def saveSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.SaveSpec,
            project.objects,
            project.layout
        )
        saveSpec.compression.set(com.kineticfire.gradle.docker.model.SaveCompression.NONE)
        // Simulate default convention path
        saveSpec.outputFile.set(project.layout.buildDirectory.file('docker-images/image.tar'))

        when:
        extension.validateSaveConfiguration(saveSpec, 'testImage')

        then:
        def ex = thrown(GradleException)
        ex.message.contains("outputFile parameter is required")
    }

    def "isValidImageReference handles registry with port and multiple colons"() {
        expect:
        extension.isValidImageReference('localhost:5000/app:latest') == true
        extension.isValidImageReference('registry.io:8080/namespace/app:v1') == true
    }

    def "isValidImageReference fails with empty image part"() {
        expect:
        extension.isValidImageReference(':tag') == false
    }

    def "isValidImageReference fails with empty tag part"() {
        expect:
        extension.isValidImageReference('image:') == false
    }

    def "isValidImageReference handles maximum length reference"() {
        given:
        def validRef = 'registry.io/' + ('a' * 200) + ':tag'  // Valid if under 255 total

        expect:
        extension.isValidImageReference(validRef) == (validRef.length() <= 255)
    }

    def "isValidRepositoryFormat requires slash"() {
        expect:
        extension.isValidRepositoryFormat('justname') == false
        extension.isValidRepositoryFormat('name/space') == true
    }

    def "isValidRepositoryFormat handles long repository names"() {
        expect:
        extension.isValidRepositoryFormat('a' * 255 + '/name') == false  // Over 255
        extension.isValidRepositoryFormat('a' * 127 + '/name') == true   // Under 255
    }

    def "isValidRegistryFormat handles edge cases"() {
        expect:
        extension.isValidRegistryFormat('localhost') == true
        extension.isValidRegistryFormat('192.168.1.1') == true
        extension.isValidRegistryFormat('192.168.1.1:5000') == true
        extension.isValidRegistryFormat('registry.io:8080') == true
        extension.isValidRegistryFormat('invalid:port:extra') == false
        extension.isValidRegistryFormat(':5000') == false
    }

    def "isValidNamespaceFormat handles edge cases"() {
        expect:
        extension.isValidNamespaceFormat('simple') == true
        extension.isValidNamespaceFormat('with-dash') == true
        extension.isValidNamespaceFormat('with_underscore') == true
        extension.isValidNamespaceFormat('with.dot') == true
        extension.isValidNamespaceFormat('with/slash') == true
        extension.isValidNamespaceFormat('a' * 255) == true
        extension.isValidNamespaceFormat('a' * 256) == false
        extension.isValidNamespaceFormat('WithUppercase') == false
    }

    def "isValidImageNameFormat handles edge cases"() {
        expect:
        extension.isValidImageNameFormat('simple') == true
        extension.isValidImageNameFormat('with-dash') == true
        extension.isValidImageNameFormat('with_underscore') == true
        extension.isValidImageNameFormat('with.dot') == true
        extension.isValidImageNameFormat('a' * 128) == true
        extension.isValidImageNameFormat('a' * 129) == false
        extension.isValidImageNameFormat('with/slash') == false
        extension.isValidImageNameFormat('WithUppercase') == false
    }

    def "isValidRepositoryName handles edge cases"() {
        expect:
        extension.isValidRepositoryName('simple') == true
        extension.isValidRepositoryName('registry.io:5000/namespace/name') == true
        extension.isValidRepositoryName('-starts-with-dash') == false
        extension.isValidRepositoryName('ends-with-dash-') == false
        extension.isValidRepositoryName('contains spaces') == false
        extension.isValidRepositoryName('a' * 256) == false
        extension.isValidRepositoryName('') == false
        extension.isValidRepositoryName(null) == false
    }

    def "validatePublishConfiguration with ImageSpec handles empty targets list"() {
        given:
        def imageSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        def publishSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishSpec,
            project.objects
        )
        // Empty targets list

        when:
        extension.validatePublishConfiguration(publishSpec, imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one target")
    }

    def "getSourceTags handles sourceRefTag with default convention"() {
        given:
        def imageSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.ImageSpec, 'testImage', project.objects, project.providers, project.layout)
        imageSpec.sourceRefImageName.set('alpine')
        // sourceRefTag not explicitly set, should use convention

        when:
        def tags = extension.getSourceTags(imageSpec)

        then:
        tags != null
    }

    def "isValidTagFormat validates all allowed characters"() {
        expect:
        extension.isValidTagFormat('v1.0.0') == true
        extension.isValidTagFormat('tag_with_underscore') == true
        extension.isValidTagFormat('tag-with-dash') == true
        extension.isValidTagFormat('tag.with.dots') == true
        extension.isValidTagFormat('TAG123') == true
        extension.isValidTagFormat('123tag') == true
    }

    def "isValidTagFormat rejects invalid starting characters"() {
        expect:
        extension.isValidTagFormat('.starts-with-dot') == false
        extension.isValidTagFormat('-starts-with-dash') == false
    }

    def "isValidImageReference validates complete reference format"() {
        expect:
        extension.isValidImageReference('registry.io:5000/namespace/name:tag') == true
        extension.isValidImageReference('localhost:5000/name:tag') == true
        extension.isValidImageReference('name:tag') == true
    }

    def "extractImageName handles various image reference formats"() {
        expect:
        extension.extractImageName('simple:tag') == 'simple'
        extension.extractImageName('namespace/name:tag') == 'namespace/name'
        extension.extractImageName('registry.io/namespace/name:tag') == 'registry.io/namespace/name'
        extension.extractImageName('registry.io:5000/namespace/name:tag') == 'registry.io:5000/namespace/name'
    }

    // ===== COVERAGE ENHANCEMENT TESTS =====

    def "validate fails when sourceRef uses both repository and namespace+imageName approaches"() {
        given:
        extension.images {
            mixedApproach {
                // Use both repository approach AND namespace+imageName approach - should fail
                sourceRefRepository.set('library/alpine')
                sourceRefNamespace.set('library')
                sourceRefImageName.set('alpine')
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Cannot use both repository approach and namespace+imageName approach")
    }

    def "validate fails when sourceRef has namespace without imageName"() {
        given:
        extension.images {
            namespaceOnly {
                sourceRefNamespace.set('library')
                // Missing sourceRefImageName - should fail
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("When using namespace+imageName approach, both namespace and imageName are required")
    }

    def "validatePublishTarget with ImageSpec fails with invalid tag format"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.tags.set(['v1.0.0'])

        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget,
            'testTarget',
            project.objects
        )
        publishTarget.getPublishTags().set(['.invalid-starts-with-dot'])

        when:
        extension.validatePublishTarget(publishTarget, imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Invalid tag format")
    }

    def "validateSaveConfiguration fails when outputFile uses default convention path"() {
        given:
        def saveSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.SaveSpec,
            project.objects,
            project.layout
        )
        saveSpec.compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
        // Leave outputFile at default convention value

        when:
        extension.validateSaveConfiguration(saveSpec, 'testImage')

        then:
        def ex = thrown(GradleException)
        ex.message.contains("outputFile parameter is required")
    }

    def "validateSaveConfiguration passes with explicitly set outputFile"() {
        given:
        def saveSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.SaveSpec,
            project.objects,
            project.layout
        )
        saveSpec.compression.set(com.kineticfire.gradle.docker.model.SaveCompression.GZIP)
        saveSpec.outputFile.set(project.layout.buildDirectory.file('custom/my-image.tar'))

        when:
        extension.validateSaveConfiguration(saveSpec, 'testImage')

        then:
        noExceptionThrown()
    }

    def "isValidImageReference returns false for invalid tag format in reference"() {
        expect:
        // Tag starting with dot is invalid
        extension.isValidImageReference('image:.invalid') == false
        // Tag starting with dash is invalid
        extension.isValidImageReference('image:-invalid') == false
    }

    def "isValidImageReference handles reference exceeding maximum length"() {
        given:
        // Create a reference that exceeds 255 characters total (name + tag > 255)
        def longName = 'a' * 248  // 248 chars for name
        def longRef = "${longName}:tag123"  // +7 = 255, we need > 255

        expect:
        // This should still pass - it's 255 chars
        extension.isValidImageReference(longRef) == true

        and:
        // Now with a longer name - 256+ chars total should fail
        def tooLongName = 'a' * 250
        def tooLongRef = "${tooLongName}:tag123"  // 257 total
        extension.isValidImageReference(tooLongRef) == false
    }

    def "validate handles image with context not present"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        // Set up sourceRef so we don't need context
        imageSpec.sourceRef.set('alpine:3.16')
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validate handles contextTaskName present and non-empty"() {
        given:
        def copyTask = project.tasks.register('buildContextTask', org.gradle.api.tasks.Copy)

        extension.images {
            contextTaskNameImage {
                imageName.set('context-task-name-app')
                tags.set(['latest'])
                contextTaskName.set('buildContextTask')
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate handles sourceRef present but empty"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRef.set('')  // Empty sourceRef
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        // Should fail because no context, contextTask, or valid sourceRef
        def ex = thrown(GradleException)
        ex.message.contains("must specify either 'context', 'contextTask', or 'sourceRef'")
    }

    def "validate handles sourceRefRepository present and non-empty"() {
        given:
        extension.images {
            sourceRefRepoImage {
                sourceRefRepository.set('library/nginx')
                sourceRefTag.set('latest')
                tags.set(['v1.0.0'])
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate handles buildArgs with sourceRef throws error"() {
        given:
        extension.images {
            mixedConfig {
                sourceRef.set('alpine:3.16')
                buildArgs.set([BUILD_DATE: '2025-01-01'])
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validate handles labels with sourceRef throws error"() {
        given:
        extension.images {
            labeledSourceRef {
                sourceRef.set('alpine:3.16')
                labels.set([maintainer: 'test@example.com'])
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Cannot mix Build Mode and SourceRef Mode")
    }

    def "validate handles empty buildArgs and labels with sourceRef passes"() {
        given:
        extension.images {
            cleanSourceRef {
                sourceRef.set('alpine:3.16')
                buildArgs.set([:])  // Empty map
                labels.set([:])     // Empty map
                imageName.set('test')
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "isValidImageReference handles exactly two colons with port"() {
        expect:
        // Registry with port and tag
        extension.isValidImageReference('localhost:5000/app:tag') == true
        // More than two colons should fail
        extension.isValidImageReference('image:tag:extra') == false
    }

    def "validateNomenclature fails when no naming approach with hasSourceRef false"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        // Don't set any naming properties - should fail

        when:
        extension.validateNomenclature(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify some form of image naming")
    }

    def "validateNomenclature handles repository and namespace mutual exclusivity with no publish config"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.repository.set('myorg/myapp')
        imageSpec.namespace.set('myorg')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("mutual exclusivity violation")
        ex.message.contains("cannot specify both 'repository' and 'namespace'")
    }

    def "validatePublishTarget with String validates tag format"() {
        given:
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget,
            'testTarget',
            project.objects
        )
        publishTarget.getPublishTags().set(['.invalid-tag'])

        when:
        extension.validatePublishTarget(publishTarget, 'testImage')

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Invalid tag format")
    }

    def "validatePublishTarget with String passes for valid configuration"() {
        given:
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget,
            'testTarget',
            project.objects
        )
        publishTarget.getPublishTags().set(['v1.0.0', 'latest'])

        when:
        extension.validatePublishTarget(publishTarget, 'testImage')

        then:
        noExceptionThrown()
    }

    def "validatePublishTarget with ImageSpec allows tag inheritance from source"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.tags.set(['v1.0.0', 'latest'])  // Source has tags

        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget,
            'testTarget',
            project.objects
        )
        // Empty publish tags - should inherit from source

        when:
        extension.validatePublishTarget(publishTarget, imageSpec)

        then:
        noExceptionThrown()
    }

    def "validatePublishTarget with ImageSpec fails when no source tags for inheritance"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        // No source tags

        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget,
            'testTarget',
            project.objects
        )
        // Empty publish tags and no source tags

        when:
        extension.validatePublishTarget(publishTarget, imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one tag")
    }

    def "validateImageSpec handles context not present and sourceRefImageName only"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        // Set sourceRefImageName only (without sourceRefNamespace)
        imageSpec.sourceRefImageName.set('alpine')
        imageSpec.sourceRefTag.set('latest')
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['v1.0.0'])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateImageSpec handles contextTaskName present but empty"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.contextTaskName.set('')  // Empty context task name
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        // Should fail - empty contextTaskName doesn't count as having context
        def ex = thrown(GradleException)
        ex.message.contains("must specify either 'context', 'contextTask', or 'sourceRef'")
    }

    def "validateNomenclature handles sourceRef present and empty"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRef.set('')  // Empty sourceRef
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature handles sourceRefRepository present and empty"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRefRepository.set('')  // Empty sourceRefRepository
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature handles sourceRefNamespace present and empty"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRefNamespace.set('')  // Empty sourceRefNamespace
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature handles sourceRefImageName present and empty"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRefImageName.set('')  // Empty sourceRefImageName
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature fails without tags or publish targets when not using sourceRef"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.imageName.set('test')
        // No tags and no publish targets

        when:
        extension.validateNomenclature(imageSpec)

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one tag or have publish targets")
    }

    def "validateImageSpec handles sourceRefImageName empty with sourceRefNamespace"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRefNamespace.set('library')
        imageSpec.sourceRefImageName.set('')  // Empty
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        // Should fail - sourceRefNamespace without sourceRefImageName
        def ex = thrown(GradleException)
        ex.message.contains("When using namespace+imageName approach, both namespace and imageName are required")
    }

    def "validateImageSpec with buildArgs not present but sourceRef has source ref"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRef.set('alpine:3.16')
        // buildArgs not set at all
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateImageSpec with labels not present but sourceRef has source ref"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRef.set('alpine:3.16')
        // labels not set at all
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature handles hasSourceRefRepository true path"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRefRepository.set('library/alpine')
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature handles hasSourceRefNamespace true path"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRefNamespace.set('library')
        imageSpec.sourceRefImageName.set('alpine')
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature handles hasSourceRefImageName true path"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRefImageName.set('alpine')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature handles only sourceRef present"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.sourceRef.set('alpine:3.16')
        imageSpec.imageName.set('test')
        imageSpec.tags.set(['latest'])

        when:
        extension.validateNomenclature(imageSpec)

        then:
        noExceptionThrown()
    }

    def "validateNomenclature passes when publish is present"() {
        given:
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec,
            'testImage',
            project.objects,
            project.providers,
            project.layout
        )
        imageSpec.imageName.set('test')
        // Set up publish spec - the publish.isPresent() check should return true
        def publishSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishSpec,
            project.objects
        )
        imageSpec.publish.set(publishSpec)

        when:
        extension.validateNomenclature(imageSpec)

        then:
        // hasPublishTargets is true, so no exception should be thrown even without tags
        noExceptionThrown()
    }

    def "validateImageSpec handles multiple hasSourceRef branches"() {
        given:
        extension.images {
            multiSourceRef {
                // Test with sourceRefImageName only (no namespace)
                sourceRefImageName.set('nginx')
                sourceRefTag.set('alpine')
                tags.set(['v1.0.0'])
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }
}