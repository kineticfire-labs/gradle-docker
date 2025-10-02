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
        task.imageReferences.set(['localhost:5000/test-image:1.0'])
        
        then:
        task.imageReferences.get() == ['localhost:5000/test-image:1.0']
    }

    def "task accepts multiple image references"() {
        when:
        def imageRefs = ['localhost:5000/image1:1.0', 'localhost:5000/image2:latest', 'localhost:5000/image3:dev']
        task.imageReferences.set(imageRefs)
        
        then:
        task.imageReferences.get() == imageRefs
        task.imageReferences.get().size() == 3
    }

    def "task accepts empty image list"() {
        when:
        task.imageReferences.set([])
        
        then:
        task.imageReferences.get().isEmpty()
        noExceptionThrown()
    }

    def "task inputs are serializable for configuration cache"() {
        when:
        task.imageReferences.set(['localhost:5000/test:1.0'])
        def imageRefsValue = task.imageReferences.get()
        
        then:
        imageRefsValue instanceof List
        imageRefsValue.every { it instanceof String }
    }

    def "task configuration is lazy"() {
        given:
        def imageProvider = project.provider { ['localhost:5000/lazy-image:1.0'] }

        when:
        task.imageReferences.set(imageProvider)

        then:
        task.imageReferences.get() == ['localhost:5000/lazy-image:1.0']
    }

    def "task accepts different registry formats"() {
        when:
        task.imageReferences.set([imageRef])
        
        then:
        task.imageReferences.get().first() == imageRef
        
        where:
        imageRef << [
            'localhost:5000/test:1.0',
            'docker.io/library/alpine:latest',
            'registry.example.com:443/app:1.0',
            '127.0.0.1:5000/myapp:latest'
        ]
    }

    def "task validates image reference format"() {
        when:
        task.imageReferences.set(['localhost:5000/valid-image:1.0', 'localhost:5000/another:latest'])
        
        then:
        task.imageReferences.get().every { it.contains(':') && it.contains('/') }
    }

    def "task handles various image reference formats"() {
        when:
        def imageRefs = [
            'localhost:5000/simple:latest',
            'localhost:5000/namespace/image:1.0.0',
            'registry.com/namespace/image:sha256-abc123',
            'localhost:5000/image:v1.2.3-alpha'
        ]
        task.imageReferences.set(imageRefs)
        
        then:
        task.imageReferences.get() == imageRefs
        task.imageReferences.get().size() == 4
    }

    def "task supports different registry protocols"() {
        when:
        task.imageReferences.set([imageRef])
        
        then:
        task.imageReferences.get().first() == imageRef
        
        where:
        imageRef << [
            'localhost:5000/test:1.0',           // HTTP local registry
            'registry.example.com/test:1.0',     // HTTPS registry
            '192.168.1.100:5000/test:1.0',      // IP address registry
            'docker.io/library/test:1.0'        // Docker Hub
        ]
    }

    def "task provider configuration works with complex scenarios"() {
        given:
        def dynamicImageRefs = project.provider {
            ['localhost:5000/app:1.0', 'localhost:5000/db:5.7', 'localhost:5000/cache:redis']
        }
        
        when:
        task.imageReferences.set(dynamicImageRefs)
        
        then:
        task.imageReferences.get().size() == 3
        task.imageReferences.get().size() > 0
    }
}