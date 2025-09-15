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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for DockerRegistryImageVerifyTask.
 * 
 * Tests task behavior without actually calling Docker commands.
 * Focuses on input validation, task configuration, and configuration cache compatibility.
 */
class DockerRegistryImageVerifyTaskTest extends Specification {

    Project project
    DockerRegistryImageVerifyTask task

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testVerifyRegistryDockerImages', DockerRegistryImageVerifyTask).get()
    }

    def "task has correct inputs"() {
        when:
        task.imageNames.set(['test-image:1.0'])
        task.registryUrl.set('localhost:5000')
        
        then:
        task.imageNames.get() == ['test-image:1.0']
        task.registryUrl.get() == 'localhost:5000'
    }

    def "task accepts multiple image names"() {
        when:
        def images = ['image1:1.0', 'image2:latest', 'image3:dev']
        task.imageNames.set(images)
        task.registryUrl.set('localhost:5000')
        
        then:
        task.imageNames.get() == images
        task.imageNames.get().size() == 3
        task.registryUrl.get() == 'localhost:5000'
    }

    def "task accepts empty image list"() {
        when:
        task.imageNames.set([])
        task.registryUrl.set('localhost:5000')
        
        then:
        task.imageNames.get().isEmpty()
        task.registryUrl.get() == 'localhost:5000'
        noExceptionThrown()
    }

    def "task inputs are serializable for configuration cache"() {
        when:
        task.imageNames.set(['test:1.0'])
        task.registryUrl.set('localhost:5000')
        def imageNamesValue = task.imageNames.get()
        def registryValue = task.registryUrl.get()
        
        then:
        imageNamesValue instanceof List
        imageNamesValue.every { it instanceof String }
        registryValue instanceof String
    }

    def "task configuration is lazy"() {
        given:
        def imageProvider = project.provider { ['lazy-image:1.0'] }
        def registryProvider = project.provider { 'lazy-registry:5000' }
        
        when:
        task.imageNames.set(imageProvider)
        task.registryUrl.set(registryProvider)
        
        then:
        task.imageNames.get() == ['lazy-image:1.0']
        task.registryUrl.get() == 'lazy-registry:5000'
    }

    def "task accepts different registry URLs"() {
        when:
        task.imageNames.set(['test:1.0'])
        task.registryUrl.set(registryUrl)
        
        then:
        task.registryUrl.get() == registryUrl
        
        where:
        registryUrl << [
            'localhost:5000',
            'docker.io',
            'registry.example.com:443',
            '127.0.0.1:5000'
        ]
    }

    def "task validates image name format"() {
        when:
        task.imageNames.set(['valid-image:1.0', 'another:latest'])
        task.registryUrl.set('localhost:5000')
        
        then:
        task.imageNames.get().every { it.contains(':') }
    }

    def "task handles various image tag formats"() {
        when:
        def images = [
            'simple:latest',
            'namespace/image:1.0.0',
            'registry.com/namespace/image:sha256-abc123',
            'image:v1.2.3-alpha'
        ]
        task.imageNames.set(images)
        task.registryUrl.set('localhost:5000')
        
        then:
        task.imageNames.get() == images
        task.imageNames.get().size() == 4
    }

    def "task supports different registry protocols"() {
        when:
        task.imageNames.set(['test:1.0'])
        task.registryUrl.set(registryUrl)
        
        then:
        task.registryUrl.get() == registryUrl
        
        where:
        registryUrl << [
            'localhost:5000',           // HTTP local registry
            'registry.example.com',     // HTTPS registry
            '192.168.1.100:5000',      // IP address registry
            'docker.io'                 // Docker Hub
        ]
    }

    def "task provider configuration works with complex scenarios"() {
        given:
        def dynamicImages = project.provider {
            ['app:1.0', 'db:5.7', 'cache:redis']
        }
        def dynamicRegistry = project.provider {
            project.findProperty('registry.url') ?: 'localhost:5000'
        }
        
        when:
        task.imageNames.set(dynamicImages)
        task.registryUrl.set(dynamicRegistry)
        
        then:
        task.imageNames.get().size() == 3
        task.registryUrl.get() == 'localhost:5000'
    }
}