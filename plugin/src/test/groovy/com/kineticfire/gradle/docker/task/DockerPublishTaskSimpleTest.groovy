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

/**
 * Simple unit tests for DockerPublishTask to debug issues
 */
class DockerPublishTaskSimpleTest extends Specification {

    Project project
    DockerPublishTask task
    DockerService mockDockerService = Mock()

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testPublish', DockerPublishTask).get()
        task.dockerService.set(mockDockerService)
    }

    def "task can be created"() {
        expect:
        task != null
        task instanceof DockerPublishTask
        task.group == 'docker'
        task.description == 'Publishes Docker image to configured registries'
    }

    def "task has required properties"() {
        expect:
        task.hasProperty('imageName')
        task.hasProperty('publishTargets')
        task.hasProperty('imageIdFile')
        task.hasProperty('dockerService')
    }

    def "task properties can be configured"() {
        when:
        task.dockerService.set(mockDockerService)
        task.imageName.set('test')
        task.tags.set(['latest'])
        
        then:
        task.dockerService.get() == mockDockerService
        task.imageName.get() == 'test'
        task.tags.get() == ['latest']
    }

    def "task executes with no targets configured"() {
        given:
        task.dockerService.set(mockDockerService)
        task.imageName.set('test')
        task.tags.set(['latest'])
        task.publishTargets.set([])
        
        when:
        task.publishImage()
        
        then:
        noExceptionThrown()
        0 * mockDockerService._
    }
}