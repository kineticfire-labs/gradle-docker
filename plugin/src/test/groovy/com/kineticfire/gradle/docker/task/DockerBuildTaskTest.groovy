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

    def "buildImage validates dockerfile exists at execution time via BuildContext"() {
        given:
        // Set up minimal required configuration
        def contextDir = project.layout.buildDirectory.dir('docker-context').get().asFile
        contextDir.mkdirs()
        def nonExistentDockerfile = new File(contextDir, 'NonExistentDockerfile')

        task.contextPath.set(contextDir)
        task.dockerfile.set(nonExistentDockerfile)
        task.imageName.set('test-image')
        task.tags.set(['latest'])

        when:
        task.buildImage()

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Dockerfile does not exist")
        ex.message.contains(nonExistentDockerfile.absolutePath)
    }

    // ===== ERROR PATH TESTS =====

    def "buildImage fails when dockerService is null"() {
        given:
        project.file('Dockerfile').createNewFile()
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        task.dockerService.set(null)

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }

    def "buildImage fails when contextPath is null"() {
        given:
        project.file('Dockerfile').createNewFile()
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set((File) null)
        task.imageName.set('myapp')
        task.tags.set(['latest'])

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }

    def "buildImage fails when imageId is null"() {
        given:
        project.file('Dockerfile').createNewFile()
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        task.imageId.set((File) null)
        mockDockerService.buildImage(_) >> CompletableFuture.completedFuture('sha256:abc123')

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }

    def "buildImage fails when using both repository and namespace/imageName"() {
        given:
        project.file('Dockerfile').createNewFile()
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.repository.set('myrepo/myapp')
        task.namespace.set('mycompany')
        task.imageName.set('myapp')
        task.tags.set(['latest'])

        when:
        task.buildImage()

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Cannot use both 'repository' and 'namespace/imageName' nomenclature simultaneously")
    }

    def "buildImage fails when using both repository and imageName only"() {
        given:
        project.file('Dockerfile').createNewFile()
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.repository.set('myrepo/myapp')
        task.imageName.set('myapp')
        task.tags.set(['latest'])

        when:
        task.buildImage()

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Cannot use both 'repository' and 'namespace/imageName' nomenclature simultaneously")
    }

    // ===== SOURCEREFOMODE TESTS =====

    def "task is skipped when sourceRefMode is true"() {
        given:
        task.sourceRefMode.set(true)
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.imageName.set('myapp')
        task.tags.set(['latest'])

        when:
        def result = task.onlyIf.isSatisfiedBy(task)

        then:
        !result
    }

    def "task executes when sourceRefMode is false"() {
        given:
        task.sourceRefMode.set(false)
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.imageName.set('myapp')
        task.tags.set(['latest'])

        when:
        def result = task.onlyIf.isSatisfiedBy(task)

        then:
        result
    }

    // ===== MISSING NULL CHECKS FOR DEFENSIVE CODE =====

    def "buildImage fails when contextPath.asFile is null via groovy safe navigation"() {
        given:
        def dockerfileFile = project.file('Dockerfile')
        dockerfileFile.createNewFile()
        task.dockerfile.set(dockerfileFile)
        // Mock contextPath to return non-null DirectoryProperty but with null asFile
        def mockContextPath = Mock(org.gradle.api.file.DirectoryProperty)
        mockContextPath.get() >> Mock(org.gradle.api.file.Directory) {
            getAsFile() >> null
        }
        task.contextPath.set(mockContextPath.get())
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        mockDockerService.buildImage(_) >> CompletableFuture.completedFuture('sha256:abc123')

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }

    def "buildImage fails when dockerfile.asFile is null via groovy safe navigation"() {
        given:
        task.contextPath.set(project.file('.'))
        // Mock dockerfile to return non-null RegularFileProperty but with null asFile
        def mockDockerfile = Mock(org.gradle.api.file.RegularFileProperty)
        mockDockerfile.isPresent() >> true
        mockDockerfile.get() >> Mock(org.gradle.api.file.RegularFile) {
            getAsFile() >> null
        }
        task.dockerfile.set(mockDockerfile.get())
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        mockDockerService.buildImage(_) >> CompletableFuture.completedFuture('sha256:abc123')

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }

    def "buildImage fails when both contextFile and dockerfileFile are null"() {
        given:
        // Mock both to return null asFile
        def mockContextPath = Mock(org.gradle.api.file.DirectoryProperty)
        mockContextPath.get() >> Mock(org.gradle.api.file.Directory) {
            getAsFile() >> null
        }
        def mockDockerfile = Mock(org.gradle.api.file.RegularFileProperty)
        mockDockerfile.isPresent() >> true
        mockDockerfile.get() >> Mock(org.gradle.api.file.RegularFile) {
            getAsFile() >> null
        }
        task.contextPath.set(mockContextPath.get())
        task.dockerfile.set(mockDockerfile.get())
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        mockDockerService.buildImage(_) >> CompletableFuture.completedFuture('sha256:abc123')

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }

    def "buildImage fails when imageId.get() returns null RegularFile"() {
        given:
        project.file('Dockerfile').createNewFile()
        task.dockerfile.set(project.file('Dockerfile'))
        task.contextPath.set(project.file('.'))
        task.imageName.set('myapp')
        task.tags.set(['latest'])
        // Mock imageId to return null
        def mockImageId = Mock(org.gradle.api.file.RegularFileProperty)
        mockImageId.get() >> null
        task.imageId.set(mockImageId.get())
        mockDockerService.buildImage(_) >> CompletableFuture.completedFuture('sha256:abc123')

        when:
        task.buildImage()

        then:
        thrown(IllegalStateException)
    }
}