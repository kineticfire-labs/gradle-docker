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
        task.group == null || task.group == 'docker'
    }

    def "task has correct inputs configured"() {
        given:
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])

        expect:
        task.dockerfile.get().asFile == project.file('Dockerfile')
        task.contextPath.get().asFile == project.file('.')
        task.imageName.get() == 'myapp'
        task.version.get() == '1.0.0'
        task.tags.get() == ['latest']
    }

    def "buildImage action executes docker service build"() {
        given:
        // Create test files
        project.file('Dockerfile').createNewFile()
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        task.buildArgs.set([VERSION: '1.0.0'])

        and:
        mockDockerService.buildImage(_) >> CompletableFuture.completedFuture('sha256:abc123')

        when:
        task.buildImage()

        then:
        1 * mockDockerService.buildImage(_) >> { args ->
            def context = args[0]
            assert context.dockerfile.toString().endsWith('Dockerfile')
            assert context.buildArgs == [VERSION: '1.0.0']
            return CompletableFuture.completedFuture('sha256:abc123')
        }
    }

    def "task fails when dockerfile is not set"() {
        given:
        task.contextPath.set(project.file('.'))
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }

    def "task fails when no image reference can be built"() {
        given:
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        // No imageName or repository set

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
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])
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

    def "labels default to empty map"() {
        expect:
        task.labels.get() == [:]
    }

    def "registry defaults to empty string"() {
        expect:
        task.registry.get() == ""
    }

    def "namespace defaults to empty string"() {
        expect:
        task.namespace.get() == ""
    }

    def "tags default to empty list"() {
        expect:
        task.tags.get() == []
    }

    def "buildImageReferences returns correct references for imageName format"() {
        given:
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest', 'v1.0.0'])

        when:
        def references = task.buildImageReferences()

        then:
        references == ['myapp:latest', 'myapp:v1.0.0']
    }

    def "buildImageReferences returns correct references for repository format"() {
        given:
        task.repository.set('mycompany/myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest', 'v1.0.0'])

        when:
        def references = task.buildImageReferences()

        then:
        references == ['mycompany/myapp:latest', 'mycompany/myapp:v1.0.0']
    }

    def "buildImageReferences returns correct references with registry and namespace"() {
        given:
        task.registry.set('my-registry.com')
        task.namespace.set('mycompany')
        task.imageName.set('myapp')
        task.version.set('1.0.0')
        task.tags.set(['latest'])

        when:
        def references = task.buildImageReferences()

        then:
        references == ['my-registry.com/mycompany/myapp:latest']
    }

    def "buildImageReferences returns empty list when no image reference can be built"() {
        given:
        task.version.set('1.0.0')
        task.tags.set(['latest'])
        // No imageName or repository set

        when:
        def references = task.buildImageReferences()

        then:
        references == []
    }
}