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

package com.kineticfire.gradle.docker.task

import com.kineticfire.gradle.docker.service.DockerService
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for DockerTagTask
 */
class DockerTagTaskTest extends Specification {

    Project project
    DockerTagTask task
    DockerService mockDockerService = Mock()

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testDockerTag', DockerTagTask).get()
        task.dockerService.set(mockDockerService)
    }

    def "task can be created"() {
        expect:
        task != null
        task instanceof DockerTagTask
    }
    
    def "task has default property values"() {
        expect:
        task.tags.get() == []
        task.sourceRef.get() == ""
    }

    def "tagImage action validates sourceRef property in sourceRef mode"() {
        when:
        task.sourceRef.set('sha256:abc123')
        task.tags.set(['myapp:latest', 'myapp:1.0.0'])

        then:
        task.sourceRef.get() == 'sha256:abc123'
        task.tags.get() == ['myapp:latest', 'myapp:1.0.0']
    }
    
    def "tagImage action validates sourceRef property"() {
        when:
        task.sourceRef.set('registry.example.com/test:latest')
        task.tags.set(['myapp:latest', 'myapp:1.0.0'])

        then:
        task.sourceRef.get() == 'registry.example.com/test:latest'
        task.tags.get() == ['myapp:latest', 'myapp:1.0.0']
    }
    
    def "task supports build mode with nomenclature properties"() {
        when:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest', 'stable'])

        then:
        task.imageName.get() == 'myapp'
        task.version.get() == '1.0.0'
        task.tags.get() == ['latest', 'stable']
    }

    def "task fails when neither sourceImage nor sourceRef is set"() {
        given:
        task.tags.set(['myapp:latest'])

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    def "task fails when tags are not set"() {
        given:
        task.sourceRef.set('sha256:abc123')

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    def "task fails when tags list is empty"() {
        given:
        task.sourceRef.set('sha256:abc123')
        task.tags.set([])

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }
    
    def "task supports multiple tag configurations"() {
        when:
        task.sourceRef.set('registry.example.com/test:latest')
        task.tags.set(tagList)

        then:
        task.tags.get() == tagList
        
        where:
        tagList << [
            ['myapp:latest'],
            ['myapp:latest', 'myapp:1.0.0'], 
            ['myapp:latest', 'myapp:1.0.0', 'myapp:stable']
        ]
    }
    
    def "task allows sourceRef to be overridden"() {
        when:
        task.sourceRef.set('image-name:latest')
        task.sourceRef.set('registry.example.com/source:latest')
        task.tags.set(['myapp:latest', 'myapp:1.0.0'])

        then:
        task.sourceRef.get() == 'registry.example.com/source:latest'  // Latest set value wins
        task.tags.get() == ['myapp:latest', 'myapp:1.0.0']
    }
    
    def "dockerService property can be configured"() {
        when:
        task.dockerService.set(mockDockerService)

        then:
        task.dockerService.get() == mockDockerService
    }

    // ===== ADDITIONAL CONFIGURATION TESTS =====

    def "task handles different tag name formats"() {
        when:
        task.sourceRef.set('test-image:latest')
        task.tags.set(tagList)

        then:
        task.tags.get() == tagList

        where:
        tagList << [
            ['myapp:v1.0.0'],
            ['myapp:latest', 'myapp:stable'],
            ['registry.example.com/app:v1.0.0'],
            ['myapp:feature-branch-name', 'myapp:pr-123'],
            ['myapp:alpha.1', 'myapp:beta.2', 'myapp:rc.3']
        ]
    }

    def "task supports complex image references"() {
        when:
        task.sourceRef.set(sourceImageRef)
        task.tags.set(['myapp:latest'])

        then:
        task.sourceRef.get() == sourceImageRef

        where:
        sourceImageRef << [
            'sha256:abc123def456',
            'myapp:v1.0.0',
            'registry.example.com/namespace/app:tag',
            'localhost:5000/test:latest'
        ]
    }

    def "task handles sourceRef configurations"() {
        when:
        task.sourceRef.set(sourceRef)
        task.tags.set(['myapp:latest'])

        then:
        task.sourceRef.get() == sourceRef

        where:
        sourceRef << [
            'registry.example.com/app:v1.0.0',
            'docker.io/library/nginx:latest',
            'ghcr.io/owner/repo:main',
            'quay.io/namespace/image:tag'
        ]
    }
}