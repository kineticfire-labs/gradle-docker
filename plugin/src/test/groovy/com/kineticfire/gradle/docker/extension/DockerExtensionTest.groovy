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
        extension = project.objects.newInstance(DockerExtension, project.objects, project)
    }

    def "extension can be created"() {
        expect:
        extension != null
        extension.images != null
    }

    def "can configure single docker image"() {
        when:
        extension.images {
            testImage {
                dockerfile.set(project.file('Dockerfile'))
                tags.set(['latest'])
                buildArgs.set([VERSION: '1.0.0'])
            }
        }

        then:
        extension.images.size() == 1
        extension.images.getByName('testImage') != null

        and:
        ImageSpec imageSpec = extension.images.getByName('testImage')
        imageSpec.dockerfile.get().asFile == project.file('Dockerfile')
        imageSpec.tags.get() == ['latest']
        imageSpec.buildArgs.get() == [VERSION: '1.0.0']
    }

    def "can configure multiple docker images"() {
        when:
        extension.images {
            webImage {
                dockerfile.set(project.file('web/Dockerfile'))
                tags.set(['latest'])
            }
            apiImage {
                dockerfile.set(project.file('api/Dockerfile'))
                tags.set(['latest', '1.0.0'])
            }
        }

        then:
        extension.images.size() == 2
        extension.images.getByName('webImage') != null
        extension.images.getByName('apiImage') != null

        and:
        extension.images.getByName('webImage').tags.get() == ['latest']
        extension.images.getByName('apiImage').tags.get() == ['latest', '1.0.0']
    }

    def "can configure image with context path"() {
        when:
        extension.images {
            contextImage {
                dockerfile.set(project.file('docker/Dockerfile'))
                context.set(project.file('docker'))
                tags.set(['latest'])
            }
        }

        then:
        ImageSpec imageSpec = extension.images.getByName('contextImage')
        imageSpec.context.get().asFile == project.file('docker')
        imageSpec.dockerfile.get().asFile == project.file('docker/Dockerfile')
    }

    def "context and dockerfile use defaults when not specified"() {
        when:
        extension.images {
            defaultImage {
                tags.set(['latest'])
            }
        }

        then:
        ImageSpec imageSpec = extension.images.getByName('defaultImage')
        // These should use the default values from configureImageDefaults
        imageSpec.context.isPresent()
        // Note: dockerfile defaults are now handled in GradleDockerPlugin, not in extension
        !imageSpec.dockerfile.isPresent()
    }

    def "can configure build args"() {
        when:
        extension.images {
            argImage {
                dockerfile.set(project.file('Dockerfile'))
                tags.set(['latest'])
                buildArgs.set([
                    VERSION: '2.0.0',
                    BUILD_DATE: '2025-01-01',
                    COMMIT_SHA: 'abc123'
                ])
            }
        }

        then:
        ImageSpec imageSpec = extension.images.getByName('argImage')
        imageSpec.buildArgs.get() == [
            VERSION: '2.0.0',
            BUILD_DATE: '2025-01-01',
            COMMIT_SHA: 'abc123'
        ]
    }

    def "can access configured images by name"() {
        given:
        extension.images {
            image1 { tags.set(['latest']) }
            image2 { tags.set(['latest']) }
            image3 { tags.set(['latest']) }
        }

        expect:
        extension.images.getByName('image1') != null
        extension.images.getByName('image2') != null  
        extension.images.getByName('image3') != null
        extension.images.getByName('image1').tags.get() == ['latest']
    }

    // ===== VALIDATION TESTS =====

    def "validate passes for valid image configuration"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            validImage {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate fails when context directory does not exist"() {
        given:
        extension.images {
            invalidImage {
                context.set(project.file('non-existent-directory'))
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Docker context directory does not exist")
        ex.message.contains('invalidImage')
        ex.message.contains("Create the directory or update the context path")
    }

    def "validate fails when dockerfile does not exist"() {
        given:
        project.file('docker').mkdirs()
        
        extension.images {
            missingDockerfile {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/NonExistentDockerfile'))
                tags.set(['latest'])
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Dockerfile does not exist")
        ex.message.contains('missingDockerfile')
        ex.message.contains("Create the Dockerfile or update the dockerfile path")
    }

    def "validate fails when tag format is invalid"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            invalidTags {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                tags.set(['invalid tag format', 'another:bad:tag:format'])
            }
        }

        when:
        extension.validate()

        then:
        def ex = thrown(GradleException)
        ex.message.contains("Invalid Docker tag name")
        ex.message.contains('invalidTags')
        ex.message.contains("Image tags should be simple names")
    }

    def "validate accepts simple tag names like latest - bug fix verification"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            timeServer {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                tags.set(['latest']) // This was the failing case that is now fixed!
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }
    
    def "validate accepts multiple simple tag names - bug fix verification"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            myApp {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                tags.set(['latest', 'v1.0.0', 'dev', 'stable']) // All of these should work now!
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate handles empty tags list"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            emptyTags {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                tags.set([])
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes when tags not specified"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            noTags {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                // tags not set
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    // ===== TAG VALIDATION TESTS =====
    
    def "isValidTagName accepts valid Docker tag names"() {
        expect:
        extension.isValidTagName('latest')
        extension.isValidTagName('v1.0.0')
        extension.isValidTagName('dev')
        extension.isValidTagName('stable')
        extension.isValidTagName('1.2.3-alpha')
        extension.isValidTagName('release-candidate-2')
        extension.isValidTagName('build123')
        extension.isValidTagName('v2.1.0-rc.1')
        extension.isValidTagName('1tag') // starts with number
        extension.isValidTagName('a') // minimal valid case
        extension.isValidTagName('tag_with_underscore') // Docker allows underscores
        extension.isValidTagName('UPPER') // Docker allows uppercase
        extension.isValidTagName('Mixed_Case.tag-123') // complex valid case
    }
    
    def "isValidTagName rejects invalid Docker tag names"() {
        expect:
        !extension.isValidTagName(null)
        !extension.isValidTagName('')
        !extension.isValidTagName('tag:with-colon') // should not contain colon
        !extension.isValidTagName('tag with spaces')
        !extension.isValidTagName('-starts-with-dash')
        !extension.isValidTagName('.starts-with-dot')
        !extension.isValidTagName('repo/tag') // should not contain slash
        !extension.isValidTagName('registry.com/repo:tag') // full image reference, not tag name
        !extension.isValidTagName('a' * 129) // over 128 character limit
    }
    
    def "isValidImageReference accepts valid Docker image references"() {
        expect:
        extension.isValidImageReference('myapp:latest')
        extension.isValidImageReference('registry.example.com/myapp:1.0.0')
        extension.isValidImageReference('user/repo:tag-123')
        extension.isValidImageReference('my-app:1.2.3-alpha')
        extension.isValidImageReference('repo.domain.com/namespace/app:stable')
        extension.isValidImageReference('a:b') // minimal valid case
        extension.isValidImageReference('1app:1tag') // starts with number
        extension.isValidImageReference('private-registry.company.com/team/app:1.0.0')
        extension.isValidImageReference('gcr.io/project-id/app:latest')
        extension.isValidImageReference('docker.io/library/nginx:alpine')
    }
    
    def "isValidImageReference rejects invalid Docker image references"() {
        expect:
        !extension.isValidImageReference(null)
        !extension.isValidImageReference('')
        !extension.isValidImageReference('no-colon-separator')
        !extension.isValidImageReference(':no-repository')
        !extension.isValidImageReference('repo:')
        !extension.isValidImageReference('invalid tag with spaces:latest')
        !extension.isValidImageReference('repo:tag with spaces')
        !extension.isValidImageReference('-starts-with-dash:latest')
        !extension.isValidImageReference('repo:-starts-with-dash')
        !extension.isValidImageReference('app') // no tag separator
        !extension.isValidImageReference(':') // empty repo and tag
        !extension.isValidImageReference('::') // double colon
    }

    def "isValidDockerTag validates correct tag formats"() {
        expect:
        extension.isValidDockerTag('myapp:latest')
        extension.isValidDockerTag('registry.example.com/myapp:1.0.0')
        extension.isValidDockerTag('user/repo:tag-123')
        extension.isValidDockerTag('my-app:1.2.3-alpha')
        extension.isValidDockerTag('repo.domain.com/namespace/app:stable')
        extension.isValidDockerTag('a:b') // minimal valid case
        extension.isValidDockerTag('1app:1tag') // starts with number
    }

    def "isValidDockerTag rejects invalid tag formats"() {
        expect:
        !extension.isValidDockerTag(null)
        !extension.isValidDockerTag('')
        !extension.isValidDockerTag('no-colon-separator')
        !extension.isValidDockerTag(':no-repository')
        !extension.isValidDockerTag('repo:')
        !extension.isValidDockerTag('invalid tag with spaces:latest')
        !extension.isValidDockerTag('repo:tag with spaces')
        !extension.isValidDockerTag('-starts-with-dash:latest')
        !extension.isValidDockerTag('repo:-starts-with-dash')
        !extension.isValidDockerTag('localhost:5000/myapp:dev') // port number conflicts with colon separator
        !extension.isValidDockerTag('app') // no tag separator
        !extension.isValidDockerTag(':') // empty repo and tag
        !extension.isValidDockerTag('::') // double colon
    }

    def "supports registry authentication in tag validation"() {
        expect:
        extension.isValidDockerTag('private-registry.company.com/team/app:1.0.0')
        extension.isValidDockerTag('gcr.io/project-id/app:latest')
        extension.isValidDockerTag('docker.io/library/nginx:alpine')
    }

    // ===== CONFIGURATION DSL TESTS =====

    def "can configure images using Action syntax"() {
        when:
        extension.images { container ->
            container.create('actionImage') { image ->
                image.context.set(project.file('docker'))
                image.tags.set(['test'])
            }
        }

        then:
        extension.images.size() == 1
        extension.images.getByName('actionImage').tags.get() == ['test']
    }

    def "can configure complex image with all properties"() {
        given:
        project.file('complex/docker').mkdirs()
        project.file('complex/docker/Dockerfile.prod').text = 'FROM alpine'
        
        when:
        extension.images {
            complexImage {
                context.set(project.file('complex/docker'))
                dockerfile.set(project.file('complex/docker/Dockerfile.prod'))
                tags.set([
                    'latest',
                    '1.0.0',
                    'prod'
                ])
                buildArgs.set([
                    VERSION: '1.0.0',
                    BUILD_DATE: '2025-01-01',
                    ENVIRONMENT: 'production',
                    COMMIT_SHA: 'abc123def456'
                ])
            }
        }

        then:
        def imageSpec = extension.images.getByName('complexImage')
        imageSpec.context.get().asFile == project.file('complex/docker')
        imageSpec.dockerfile.get().asFile == project.file('complex/docker/Dockerfile.prod')
        imageSpec.tags.get().size() == 3
        imageSpec.tags.get().containsAll(['latest', '1.0.0', 'prod'])
        imageSpec.buildArgs.get().size() == 4
        imageSpec.buildArgs.get()['VERSION'] == '1.0.0'
        imageSpec.buildArgs.get()['ENVIRONMENT'] == 'production'
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
                context.set(project.file('web'))
                dockerfile.set(project.file('web/Dockerfile'))
                tags.set(['latest'])
                buildArgs.set([SERVICE: 'web'])
            }
            apiImage {
                context.set(project.file('api'))
                dockerfile.set(project.file('api/Dockerfile'))
                tags.set(['latest', '1.0.0'])
                buildArgs.set([SERVICE: 'api', NODE_ENV: 'production'])
            }
        }

        then:
        extension.images.size() == 2
        
        def webImage = extension.images.getByName('webImage')
        webImage.tags.get() == ['latest']
        webImage.buildArgs.get()['SERVICE'] == 'web'
        
        def apiImage = extension.images.getByName('apiImage')
        apiImage.tags.get().size() == 2
        apiImage.buildArgs.get()['NODE_ENV'] == 'production'
    }

    def "validation handles multiple images with mixed validity"() {
        given:
        project.file('valid').mkdirs()
        project.file('valid/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            validImage {
                context.set(project.file('valid'))
                dockerfile.set(project.file('valid/Dockerfile'))
                tags.set(['latest'])
            }
            invalidImage {
                context.set(project.file('non-existent'))
                tags.set(['latest'])
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
                context.set(project.file('non-existent-first'))
                tags.set(['latest'])
            }
            secondInvalid {
                context.set(project.file('non-existent-second'))
                tags.set(['latest'])
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
                tags.set(['latest'])
            }
        }
        extension.validate()

        then:
        noExceptionThrown()
        extension.images.getByName('customDockerfile').dockerfile.get().asFile.name == 'Dockerfile.custom'
    }

    def "supports registry authentication in tag validation"() {
        expect:
        extension.isValidDockerTag('private-registry.company.com/team/app:1.0.0')
        extension.isValidDockerTag('gcr.io/project-id/app:latest')
        extension.isValidDockerTag('docker.io/library/nginx:alpine')
    }

    def "validateImageSpec method can be called independently"() {
        given:
        project.file('test-context').mkdirs()
        project.file('test-context/Dockerfile').text = 'FROM alpine'
        
        def imageSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.ImageSpec, 
            'testImage', 
            project
        )
        imageSpec.context.set(project.file('test-context'))
        imageSpec.dockerfile.set(project.file('test-context/Dockerfile'))
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
                tags.set(['latest', 'v1.0.0'])  // Simple tag names
                publish {
                    to('registry') {
                        repository = 'localhost:5000/test-app'
                        publishTags = ['latest', 'stable']  // Also simple tag names
                    }
                }
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validatePublishTarget fails when repository format is invalid"() {
        given: "A publish target with invalid repository"
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget, 
            'testTarget', 
            project
        )
        publishTarget.repository.set('')  // Invalid empty repository
        publishTarget.publishTags.set(['latest'])

        when: "validation is performed on target with invalid repository"
        extension.validatePublishTarget(publishTarget, 'testImage')

        then: "validation fails with descriptive error"
        def ex = thrown(GradleException)
        ex.message.contains("Invalid repository name")
        ex.message.contains('testTarget')
        ex.message.contains('testImage')
    }

    def "validatePublishTarget fails when target has invalid tag names"() {
        given: "A publish target with invalid tag names"
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget, 
            'testTarget', 
            project
        )
        publishTarget.repository.set('localhost:5000/test-app')
        publishTarget.publishTags.set(['invalid tag with spaces'])  // Invalid tag

        when: "validation is performed directly on the publish target"
        extension.validatePublishTarget(publishTarget, 'testImage')

        then: "validation fails with descriptive error"
        def ex = thrown(GradleException)
        ex.message.contains("Invalid tag name")
        ex.message.contains("invalid tag with spaces")
        ex.message.contains('testTarget')
        ex.message.contains('testImage')
    }

    def "validatePublishConfiguration fails when configuration has no targets"() {
        given: "A publish configuration with empty targets"
        def publishSpec = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishSpec,
            project
        )
        // publishSpec.to is empty by default

        when: "validation is performed on empty publish configuration"
        extension.validatePublishConfiguration(publishSpec, 'testImage')

        then: "validation fails with descriptive error"
        def ex = thrown(GradleException)
        ex.message.contains("must specify at least one target")
        ex.message.contains('testImage')
    }

    def "validate fails when publish target has no repository"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        // Create publishTarget manually to test missing repository
        def publishTarget = project.objects.newInstance(
            com.kineticfire.gradle.docker.spec.PublishTarget, 
            'testTarget', 
            project
        )
        // Don't set repository - leave it empty
        publishTarget.publishTags.set(['latest'])

        when:
        extension.validatePublishTarget(publishTarget, 'testImage')

        then:
        def ex = thrown(GradleException)
        ex.message.contains("must specify a repository")
        ex.message.contains('testTarget')
        ex.message.contains('testImage')
    }

    def "validation distinguishes between image tags and publish target tags"() {
        given: "Image with both image tags and publish configuration"
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            mixedTagsImage {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                tags.set(['dev', 'latest'])  // Image tags - simple names
                publish {
                    to('prod') {
                        repository = 'registry.company.com/team/app'
                        publishTags = ['prod', 'stable']  // Publish tags - also simple names
                    }
                    to('staging') {
                        repository = 'localhost:5000/staging/app'  
                        publishTags = ['staging', 'test']  // Multiple publish targets
                    }
                }
            }
        }

        when: "validation is performed"
        extension.validate()

        then: "both contexts validate correctly without confusion"
        noExceptionThrown()
    }

    def "regression test - exact configuration from integration tests"() {
        given: "Configuration matching integration test patterns"
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        
        extension.images {
            timeServer {  // Exact name from integration test
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                tags.set(['1.0.0', 'latest'])  // Version and latest tags
                publish {
                    to('basic') {
                        repository = 'localhost:25000/time-server-integration'
                        publishTags = ['latest']  // This should work correctly
                    }
                }
            }
        }

        when: "validation is performed on real-world configuration"
        extension.validate()

        then: "validation passes without issues"
        noExceptionThrown()
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
                contextTask = copyTask
                dockerfile.set(project.file('Dockerfile'))
                tags.set(['latest'])
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
                context.set(project.file('docker'))
                contextTask = copyTask
                tags.set(['latest'])
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
                sourceRef.set('alpine:3.16')
                tags.set(['latest'])
                // Don't set dockerfile for sourceRef images
            }
        }

        when:
        extension.validate()

        then:
        noExceptionThrown()
    }

    def "validate passes for image with inline context block"() {
        given:
        def srcDir = project.file('src')
        srcDir.mkdirs()
        project.file('Dockerfile').text = 'FROM alpine'
        
        when:
        extension.images {
            inlineContextImage {
                context {
                    from(srcDir)
                }
                dockerfile.set(project.file('Dockerfile'))
                tags.set(['latest'])
            }
        }
        extension.validate()

        then:
        noExceptionThrown()
    }

    // ===== DOCKERFILE NAME VALIDATION TESTS =====

    def "validate fails when both dockerfile and dockerfileName are set"() {
        given:
        project.file('docker').mkdirs()
        project.file('docker/Dockerfile').text = 'FROM alpine'
        project.file('docker/Dockerfile.custom').text = 'FROM alpine'
        
        extension.images {
            conflictingDockerfile {
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile'))
                dockerfileName.set('Dockerfile.custom')
                tags.set(['latest'])
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
                context.set(project.file('docker'))
                dockerfileName.set('Dockerfile.prod')
                tags.set(['latest'])
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
                context.set(project.file('docker'))
                dockerfile.set(project.file('docker/Dockerfile.custom'))
                tags.set(['latest'])
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
}