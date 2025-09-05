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
        task.group == 'docker'
        task.description.contains('Tag Docker image')
    }
    
    def "task has default property values"() {
        expect:
        task.tags.get() == []
        task.pullIfMissing.get() == false
    }

    def "tagImage action validates sourceImage property"() {
        when:
        task.sourceImage.set('sha256:abc123')
        task.tags.set(['latest', '1.0.0'])

        then:
        task.sourceImage.get() == 'sha256:abc123'
        task.tags.get() == ['latest', '1.0.0']
    }
    
    def "tagImage action validates sourceRef property"() {
        when:
        task.sourceRef.set('registry.example.com/test:latest')
        task.tags.set(['latest', '1.0.0'])
        task.pullIfMissing.set(true)

        then:
        task.sourceRef.get() == 'registry.example.com/test:latest'
        task.tags.get() == ['latest', '1.0.0']
        task.pullIfMissing.get() == true
    }
    
    def "task supports pullIfMissing configuration"() {
        when:
        task.sourceRef.set('registry.example.com/test:latest')
        task.tags.set(['latest', '1.0.0'])
        task.pullIfMissing.set(pullIfMissing)

        then:
        task.pullIfMissing.get() == pullIfMissing
        
        where:
        pullIfMissing << [true, false]
    }

    def "task fails when neither sourceImage nor sourceRef is set"() {
        given:
        task.tags.set(['latest'])

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    def "task fails when tags are not set"() {
        given:
        task.sourceImage.set('sha256:abc123')

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    def "task fails when tags list is empty"() {
        given:
        task.sourceImage.set('sha256:abc123')
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
            ['latest'],
            ['latest', '1.0.0'], 
            ['latest', '1.0.0', 'stable']
        ]
    }
    
    def "task allows both sourceRef and sourceImage to be set"() {
        when:
        task.sourceImage.set('image-name:latest')
        task.sourceRef.set('registry.example.com/source:latest')
        task.tags.set(['latest', '1.0.0'])

        then:
        task.sourceImage.get() == 'image-name:latest'
        task.sourceRef.get() == 'registry.example.com/source:latest'
        task.tags.get() == ['latest', '1.0.0']
    }
    
    def "dockerService property can be configured"() {
        when:
        task.dockerService.set(mockDockerService)

        then:
        task.dockerService.get() == mockDockerService
    }
}