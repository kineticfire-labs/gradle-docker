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
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * Unit tests for DockerBuildTask
 */
class DockerBuildTaskTest extends Specification {

    Project project
    DockerBuildTask task
    DockerService mockDockerService = Mock()

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testDockerBuild', DockerBuildTask).get()
        task.dockerService.set(mockDockerService)
        // Set up output file for imageId
        task.imageId.set(project.layout.buildDirectory.file('test-image-id.txt'))
    }

    def "task can be created"() {
        expect:
        task != null
        task.group == 'docker'
        task.description.contains('Build Docker image')
    }

    def "task has correct inputs configured"() {
        given:
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.tags.set(['test:latest'])

        expect:
        task.dockerfile.get().asFile == project.file('Dockerfile')
        task.contextPath.get().asFile == project.file('.')
        task.tags.get() == ['test:latest']
    }

    def "buildImage action executes docker service build"() {
        given:
        // Create test files
        project.file('Dockerfile').createNewFile()
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.tags.set(['myapp:latest'])
        task.buildArgs.set([VERSION: '1.0.0'])

        and:
        mockDockerService.buildImage(_) >> CompletableFuture.completedFuture('sha256:abc123')

        when:
        task.buildImage()

        then:
        1 * mockDockerService.buildImage(_) >> { args ->
            def context = args[0]
            assert context.dockerfile.toString().endsWith('Dockerfile')
            assert context.tags == ['myapp:latest']
            assert context.buildArgs == [VERSION: '1.0.0']
            return CompletableFuture.completedFuture('sha256:abc123')
        }
    }

    def "task fails when dockerfile is not set"() {
        given:
        task.contextPath.set(project.file('.'))
        task.tags.set(['test:latest'])

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }

    def "task fails when tags are not set"() {
        given:
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }

    def "task outputs imageId property"() {
        given:
        // Create test files
        project.file('Dockerfile').createNewFile()
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.tags.set(['test:latest'])
        mockDockerService.buildImage(_) >> CompletableFuture.completedFuture('sha256:abc123')

        when:
        task.buildImage()

        then:
        task.imageId.get().asFile.text.trim() == 'sha256:abc123'
    }

    def "buildArgs default to empty map"() {
        expect:
        task.buildArgs.get() == [:]
    }
}