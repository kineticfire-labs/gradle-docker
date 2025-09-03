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
                tags.set(['test:latest'])
                buildArgs.set([VERSION: '1.0.0'])
            }
        }

        then:
        extension.images.size() == 1
        extension.images.getByName('testImage') != null

        and:
        ImageSpec imageSpec = extension.images.getByName('testImage')
        imageSpec.dockerfile.get().asFile == project.file('Dockerfile')
        imageSpec.tags.get() == ['test:latest']
        imageSpec.buildArgs.get() == [VERSION: '1.0.0']
    }

    def "can configure multiple docker images"() {
        when:
        extension.images {
            webImage {
                dockerfile.set(project.file('web/Dockerfile'))
                tags.set(['myapp/web:latest'])
            }
            apiImage {
                dockerfile.set(project.file('api/Dockerfile'))
                tags.set(['myapp/api:latest', 'myapp/api:1.0.0'])
            }
        }

        then:
        extension.images.size() == 2
        extension.images.getByName('webImage') != null
        extension.images.getByName('apiImage') != null

        and:
        extension.images.getByName('webImage').tags.get() == ['myapp/web:latest']
        extension.images.getByName('apiImage').tags.get() == ['myapp/api:latest', 'myapp/api:1.0.0']
    }

    def "can configure image with context path"() {
        when:
        extension.images {
            contextImage {
                dockerfile.set(project.file('docker/Dockerfile'))
                context.set(project.file('docker'))
                tags.set(['context:latest'])
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
                tags.set(['default:latest'])
            }
        }

        then:
        ImageSpec imageSpec = extension.images.getByName('defaultImage')
        // These should use the default values from configureImageDefaults
        imageSpec.context.isPresent()
        imageSpec.dockerfile.isPresent()
    }

    def "can configure build args"() {
        when:
        extension.images {
            argImage {
                dockerfile.set(project.file('Dockerfile'))
                tags.set(['args:latest'])
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
            image1 { tags.set(['test1:latest']) }
            image2 { tags.set(['test2:latest']) }
            image3 { tags.set(['test3:latest']) }
        }

        expect:
        extension.images.getByName('image1') != null
        extension.images.getByName('image2') != null  
        extension.images.getByName('image3') != null
        extension.images.getByName('image1').tags.get() == ['test1:latest']
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
                tags.set(['myapp:latest'])
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
                tags.set(['test:latest'])
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
                tags.set(['test:latest'])
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
        ex.message.contains("Invalid Docker tag format")
        ex.message.contains('invalidTags')
        ex.message.contains("Use format 'repository:tag'")
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
                image.tags.set(['action:test'])
            }
        }

        then:
        extension.images.size() == 1
        extension.images.getByName('actionImage').tags.get() == ['action:test']
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
                    'myapp:latest',
                    'myapp:1.0.0',
                    'registry.example.com/myapp:prod'
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
        imageSpec.tags.get().containsAll(['myapp:latest', 'myapp:1.0.0', 'registry.example.com/myapp:prod'])
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
                tags.set(['myapp/web:latest'])
                buildArgs.set([SERVICE: 'web'])
            }
            apiImage {
                context.set(project.file('api'))
                dockerfile.set(project.file('api/Dockerfile'))
                tags.set(['myapp/api:latest', 'myapp/api:1.0.0'])
                buildArgs.set([SERVICE: 'api', NODE_ENV: 'production'])
            }
        }

        then:
        extension.images.size() == 2
        
        def webImage = extension.images.getByName('webImage')
        webImage.tags.get() == ['myapp/web:latest']
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
                tags.set(['valid:latest'])
            }
            invalidImage {
                context.set(project.file('non-existent'))
                tags.set(['invalid:latest'])
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
                tags.set(['first:latest'])
            }
            secondInvalid {
                context.set(project.file('non-existent-second'))
                tags.set(['second:latest'])
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
                tags.set(['spaces:latest'])
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
                tags.set(['custom:latest'])
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
        imageSpec.tags.set(['test:validation'])

        when:
        extension.validateImageSpec(imageSpec)

        then:
        noExceptionThrown()
    }

    def "extension is properly initialized with defaults"() {
        when:
        extension.images {
            defaultTest {
                tags.set(['default:test'])
            }
        }

        then:
        def imageSpec = extension.images.getByName('defaultTest')
        imageSpec.context.isPresent() // Should have default context
        imageSpec.dockerfile.isPresent() // Should have default dockerfile
        imageSpec.context.get().asFile.path.contains('src/main/docker')
    }
}