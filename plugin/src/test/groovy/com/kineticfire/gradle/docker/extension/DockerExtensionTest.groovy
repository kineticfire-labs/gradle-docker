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
}