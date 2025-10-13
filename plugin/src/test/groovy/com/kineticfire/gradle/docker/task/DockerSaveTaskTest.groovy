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

import com.kineticfire.gradle.docker.model.SaveCompression
import com.kineticfire.gradle.docker.service.DockerService
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for DockerSaveTask
 */
class DockerSaveTaskTest extends Specification {

    Project project
    DockerSaveTask task
    DockerService mockDockerService = Mock()

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testDockerSave', DockerSaveTask).get()
        task.dockerService.set(mockDockerService)
    }

    // ===== BASIC TASK TESTS =====

    def "task can be created"() {
        expect:
        task != null
        task instanceof DockerSaveTask
    }

    def "task extends DefaultTask"() {
        expect:
        task instanceof org.gradle.api.DefaultTask
    }

    def "task can be created"() {
        expect:
        task != null
        task instanceof DockerSaveTask
    }

    def "task has saveImage action"() {
        expect:
        task.actions.size() == 1
        task.actions[0].displayName.contains('saveImage')
    }
    
    def "task has default property values"() {
        expect:
        task.compression.present
        task.compression.get() == SaveCompression.NONE
        task.sourceRef.get() == ""
    }

    // ===== TASK EXECUTION TESTS =====

    def "saveImage action validates imageName property"() {
        given:
        task.imageName.set('test-image:latest')
        task.outputFile.set(project.file('test.tar.gz'))
        task.compression.set(SaveCompression.GZIP)
        // Mock the service to avoid actual Docker calls in unit tests
        task.dockerService.set(mockDockerService)
        mockDockerService.saveImage(_, _, _) >> CompletableFuture.completedFuture(null)

        when:
        def imageName = task.imageName.get()
        def outputFile = task.outputFile.get()
        def compression = task.compression.get()

        then:
        imageName == 'test-image:latest'
        outputFile.asFile.name == 'test.tar.gz'
        compression == SaveCompression.GZIP
    }
    
    def "saveImage action validates sourceRef property"() {
        given:
        task.sourceRef.set('registry.example.com/test:latest')
        task.outputFile.set(project.file('test.tar.gz'))
        task.compression.set(SaveCompression.NONE)
        when:
        def sourceRef = task.sourceRef.get()

        then:
        sourceRef == 'registry.example.com/test:latest'
    }

    def "task configuration supports all properties"() {
        when:
        task.sourceRef.set('registry.example.com/test:latest')
        task.imageName.set('local-image:latest')
        task.outputFile.set(project.file('test.tar.gz'))
        task.compression.set(SaveCompression.BZIP2)

        then:
        task.sourceRef.get() == 'registry.example.com/test:latest'
        task.imageName.get() == 'local-image:latest'
        task.outputFile.get().asFile.name == 'test.tar.gz'
        task.compression.get() == SaveCompression.BZIP2
    }
    
    def "task fails when neither imageName nor sourceRef is set"() {
        given:
        task.outputFile.set(project.file('test.tar.gz'))
        task.compression.set(SaveCompression.GZIP)

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
    }
    
    def "task fails when outputFile is not set"() {
        given:
        task.imageName.set('test-image:latest')
        task.compression.set(SaveCompression.GZIP)

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
    }
    
    def "task fails when compression is not set"() {
        given:
        task.imageName.set('test-image:latest')
        task.outputFile.set(project.file('test.tar'))

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
    }

    // ===== TASK CONFIGURATION TESTS =====

    def "task can be configured with different compression types"() {
        given:
        task.compression.set(SaveCompression.NONE)
        
        expect:
        task.compression.get() == SaveCompression.NONE
    }
    
    def "task can be configured with different names"() {
        given:
        def task1 = project.tasks.register('saveWebapp', DockerSaveTask).get()
        def task2 = project.tasks.register('saveApi', DockerSaveTask).get()

        expect:
        task1.name == 'saveWebapp'
        task2.name == 'saveApi'
        task1 != task2
    }

    def "task inherits DefaultTask properties"() {
        expect:
        task.hasProperty('group')
        task.hasProperty('description')
        task.hasProperty('enabled')
        task.hasProperty('logger')
    }

    def "task is enabled by default"() {
        expect:
        task.enabled == true
    }

    def "task can be disabled"() {
        when:
        task.enabled = false

        then:
        task.enabled == false
    }

    // ===== TASK TYPE TESTS =====

    def "task type is DockerSaveTask"() {
        expect:
        task.class.simpleName == 'DockerSaveTask_Decorated'
        DockerSaveTask.isAssignableFrom(task.class)
    }

    def "multiple tasks can be created"() {
        given:
        def task1 = project.tasks.register('save1', DockerSaveTask).get()
        def task2 = project.tasks.register('save2', DockerSaveTask).get()
        def task3 = project.tasks.register('save3', DockerSaveTask).get()

        expect:
        [task1, task2, task3].every { it instanceof DockerSaveTask }
        [task1, task2, task3].collect { it.name } == ['save1', 'save2', 'save3']
    }

    // ===== COMPRESSION TYPE TESTS =====
    
    def "task supports all compression types"() {
        when:
        task.compression.set(compressionType)

        then:
        task.compression.get() == compressionType
        
        where:
        compressionType << [SaveCompression.NONE, SaveCompression.GZIP, SaveCompression.BZIP2, SaveCompression.XZ, SaveCompression.ZIP]
    }
    
    // ===== EDGE CASES =====

    def "task allows both sourceRef and imageName to be set"() {
        when:
        task.imageName.set('image-name:latest')
        task.sourceRef.set('registry.example.com/source:latest')
        task.outputFile.set(project.file('test.tar.gz'))
        task.compression.set(SaveCompression.XZ)

        then:
        task.imageName.get() == 'image-name:latest'
        task.sourceRef.get() == 'registry.example.com/source:latest'
        task.outputFile.get().asFile.name == 'test.tar.gz'
        task.compression.get() == SaveCompression.XZ
    }
    
    def "dockerService property can be configured"() {
        when:
        task.dockerService.set(mockDockerService)

        then:
        task.dockerService.get() == mockDockerService
    }

    // ===== ADDITIONAL VALIDATION TESTS =====
    
    def "task validates property combinations correctly"() {
        expect:
        // Test various property combination scenarios
        task.imageName.set('test-image:latest')
        task.imageName.get() == 'test-image:latest'
        
        task.sourceRef.set('registry.example.com/test:latest')
        task.sourceRef.get() == 'registry.example.com/test:latest'
        
        task.compression.present  // Has default compression
        task.compression.get() == SaveCompression.NONE
    }
    
    def "task can be configured with different compression formats"() {
        when:
        task.compression.set(compressionType)
        
        then:
        task.compression.get() == compressionType
        
        where:
        compressionType << [SaveCompression.NONE, SaveCompression.GZIP, SaveCompression.BZIP2, SaveCompression.XZ, SaveCompression.ZIP]
    }
    

    def "task properly handles file path configurations"() {
        when:
        task.outputFile.set(project.file(filePath))

        then:
        task.outputFile.get().asFile.name == expectedName

        where:
        filePath           | expectedName
        'test.tar'         | 'test.tar'
        'output.tar.gz'    | 'output.tar.gz'
        'image.tar.bz2'    | 'image.tar.bz2'
        'docker.tar.xz'    | 'docker.tar.xz'
        'backup.zip'       | 'backup.zip'
    }

    // ===== BUILDPRIMARYIMAGEREFERENCE TESTS =====

    def "buildPrimaryImageReference returns sourceRef when sourceRef is set"() {
        given:
        task.sourceRef.set('myregistry.com/myapp:1.2.3')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myregistry.com/myapp:1.2.3'
    }

    def "buildPrimaryImageReference builds from repository when set"() {
        given:
        task.repository.set('myorg/myapp')
        task.tags.set(['v1.0.0'])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myorg/myapp:v1.0.0'
    }

    def "buildPrimaryImageReference builds from repository with registry"() {
        given:
        task.registry.set('myregistry.com')
        task.repository.set('myorg/myapp')
        task.tags.set(['latest'])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myregistry.com/myorg/myapp:latest'
    }

    def "buildPrimaryImageReference builds from imageName when set"() {
        given:
        task.imageName.set('webapp')
        task.tags.set(['dev'])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'webapp:dev'
    }

    def "buildPrimaryImageReference builds from imageName with namespace"() {
        given:
        task.namespace.set('myorg')
        task.imageName.set('webapp')
        task.tags.set(['v2.0'])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myorg/webapp:v2.0'
    }

    def "buildPrimaryImageReference builds from imageName with registry and namespace"() {
        given:
        task.registry.set('registry.example.com')
        task.namespace.set('team')
        task.imageName.set('myapp')
        task.tags.set(['stable'])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'registry.example.com/team/myapp:stable'
    }

    def "buildPrimaryImageReference builds from imageName with registry only"() {
        given:
        task.registry.set('localhost:5000')
        task.imageName.set('testimage')
        task.tags.set(['test'])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'localhost:5000/testimage:test'
    }

    def "buildPrimaryImageReference returns null when tags are empty"() {
        given:
        task.imageName.set('myapp')
        task.tags.set([])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == null
    }

    def "buildPrimaryImageReference returns null when neither sourceRef nor imageName/repository set"() {
        given:
        task.tags.set(['latest'])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == null
    }

    def "buildPrimaryImageReference prefers sourceRef over build mode"() {
        given:
        task.sourceRef.set('source:1.0')
        task.imageName.set('built')
        task.tags.set(['2.0'])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'source:1.0'
    }

    // ===== SOURCEREF COMPONENT MODE TESTS =====

    def "buildPrimaryImageReference builds from sourceRefImageName and sourceRefTag"() {
        given:
        task.sourceRefImageName.set('alpine')
        task.sourceRefTag.set('latest')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'alpine:latest'
    }

    def "buildPrimaryImageReference builds from sourceRefNamespace, sourceRefImageName, and sourceRefTag"() {
        given:
        task.sourceRefNamespace.set('myorg')
        task.sourceRefImageName.set('myapp')
        task.sourceRefTag.set('v1.0.0')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myorg/myapp:v1.0.0'
    }

    def "buildPrimaryImageReference builds from sourceRefRegistry, sourceRefNamespace, sourceRefImageName, and sourceRefTag"() {
        given:
        task.sourceRefRegistry.set('registry.example.com')
        task.sourceRefNamespace.set('team')
        task.sourceRefImageName.set('webapp')
        task.sourceRefTag.set('stable')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'registry.example.com/team/webapp:stable'
    }

    def "buildPrimaryImageReference builds from sourceRefRepository and sourceRefTag"() {
        given:
        task.sourceRefRepository.set('myorg/myapp')
        task.sourceRefTag.set('v2.0.0')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myorg/myapp:v2.0.0'
    }

    def "buildPrimaryImageReference builds from sourceRefRegistry, sourceRefRepository, and sourceRefTag"() {
        given:
        task.sourceRefRegistry.set('localhost:5000')
        task.sourceRefRepository.set('internal/service')
        task.sourceRefTag.set('test')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'localhost:5000/internal/service:test'
    }

    def "buildPrimaryImageReference prefers sourceRef string over components"() {
        given:
        task.sourceRef.set('direct:1.0')
        task.sourceRefImageName.set('component')
        task.sourceRefTag.set('2.0')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'direct:1.0'
    }

    def "buildPrimaryImageReference prefers sourceRef components over build mode"() {
        given:
        task.sourceRefImageName.set('sourceapp')
        task.sourceRefTag.set('1.0')
        task.imageName.set('buildapp')
        task.tags.set(['2.0'])

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'sourceapp:1.0'
    }

    def "buildPrimaryImageReference defaults to latest when sourceRefTag is missing"() {
        given:
        task.sourceRefImageName.set('myapp')
        // Missing sourceRefTag - should default to "latest"

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myapp:latest'
    }

    def "buildPrimaryImageReference with sourceRefRegistry only and imageName/tag"() {
        given:
        task.sourceRefRegistry.set('myregistry.com')
        task.sourceRefImageName.set('alpine')
        task.sourceRefTag.set('3.16')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myregistry.com/alpine:3.16'
    }

    // ===== PULLSOURCEREFIFNEEDED TESTS (using reflection for private method) =====

    private void invokePullSourceRefIfNeeded() {
        def method = DockerSaveTask.getDeclaredMethod('pullSourceRefIfNeeded')
        method.accessible = true
        method.invoke(task)
    }

    def "pullSourceRefIfNeeded does nothing when pullIfMissing is false"() {
        given:
        task.pullIfMissing.set(false)
        task.effectiveSourceRef.set('someimage:tag')

        when:
        invokePullSourceRefIfNeeded()

        then:
        0 * mockDockerService._
    }

    def "pullSourceRefIfNeeded does nothing when effectiveSourceRef is empty"() {
        given:
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('')

        when:
        invokePullSourceRefIfNeeded()

        then:
        0 * mockDockerService._
    }

    def "pullSourceRefIfNeeded pulls image when missing"() {
        given:
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('alpine:3.16')
        mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(false)
        mockDockerService.pullImage('alpine:3.16', null) >> CompletableFuture.completedFuture(null)

        when:
        invokePullSourceRefIfNeeded()

        then:
        1 * mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(false)
        1 * mockDockerService.pullImage('alpine:3.16', null) >> CompletableFuture.completedFuture(null)
    }

    def "pullSourceRefIfNeeded does not pull when image exists"() {
        given:
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('ubuntu:22.04')
        mockDockerService.imageExists('ubuntu:22.04') >> CompletableFuture.completedFuture(true)

        when:
        invokePullSourceRefIfNeeded()

        then:
        1 * mockDockerService.imageExists('ubuntu:22.04') >> CompletableFuture.completedFuture(true)
        0 * mockDockerService.pullImage(_, _)
    }

    // ===== SAVEIMAGE EXECUTION TESTS =====

    def "saveImage executes successfully with sourceRef"() {
        given:
        task.sourceRef.set('alpine:3.16')
        task.tags.set(['latest'])
        task.outputFile.set(project.file('output.tar'))
        task.compression.set(SaveCompression.NONE)
        mockDockerService.saveImage(_, _, _) >> CompletableFuture.completedFuture(null)

        when:
        task.saveImage()

        then:
        1 * mockDockerService.saveImage('alpine:3.16', _, SaveCompression.NONE) >> CompletableFuture.completedFuture(null)
    }

    def "saveImage executes successfully with repository"() {
        given:
        task.repository.set('myorg/app')
        task.tags.set(['v1.0.0'])
        task.outputFile.set(project.file('app.tar.gz'))
        task.compression.set(SaveCompression.GZIP)
        mockDockerService.saveImage(_, _, _) >> CompletableFuture.completedFuture(null)

        when:
        task.saveImage()

        then:
        1 * mockDockerService.saveImage('myorg/app:v1.0.0', _, SaveCompression.GZIP) >> CompletableFuture.completedFuture(null)
    }

    def "saveImage executes successfully with imageName"() {
        given:
        task.imageName.set('webapp')
        task.tags.set(['dev'])
        task.outputFile.set(project.file('webapp.tar'))
        task.compression.set(SaveCompression.BZIP2)
        mockDockerService.saveImage(_, _, _) >> CompletableFuture.completedFuture(null)

        when:
        task.saveImage()

        then:
        1 * mockDockerService.saveImage('webapp:dev', _, SaveCompression.BZIP2) >> CompletableFuture.completedFuture(null)
    }

    def "saveImage fails when dockerService is null"() {
        given:
        task.sourceRef.set('alpine:latest')
        task.tags.set(['latest'])
        task.outputFile.set(project.file('output.tar'))
        task.dockerService.set(null)

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
    }

    def "saveImage fails when outputFile returns null"() {
        given:
        task.sourceRef.set('alpine:latest')
        task.tags.set(['latest'])
        task.outputFile.set(null)

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
    }

    def "saveImage pulls source if needed before saving"() {
        given:
        task.sourceRef.set('alpine:3.16')
        task.tags.set(['latest'])
        task.outputFile.set(project.file('output.tar'))
        task.pullIfMissing.set(true)
        task.effectiveSourceRef.set('alpine:3.16')
        mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(false)
        mockDockerService.pullImage('alpine:3.16', null) >> CompletableFuture.completedFuture(null)
        mockDockerService.saveImage(_, _, _) >> CompletableFuture.completedFuture(null)

        when:
        task.saveImage()

        then:
        1 * mockDockerService.imageExists('alpine:3.16') >> CompletableFuture.completedFuture(false)
        1 * mockDockerService.pullImage('alpine:3.16', null) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.saveImage('alpine:3.16', _, SaveCompression.NONE) >> CompletableFuture.completedFuture(null)
    }

    // ===== MISSING NULL FUTURE TEST =====

    def "saveImage fails when saveImage future is null"() {
        given:
        task.sourceRef.set('alpine:latest')
        task.tags.set(['latest'])
        task.outputFile.set(project.file('output.tar'))
        mockDockerService.saveImage(_, _, _) >> null

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
    }

    // ===== MISSING SOURCEREF COMPONENT EDGE CASES =====

    def "buildPrimaryImageReference with sourceRefRepository and empty registry"() {
        given:
        task.sourceRefRegistry.set('')
        task.sourceRefRepository.set('myorg/myapp')
        task.sourceRefTag.set('v1.0')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myorg/myapp:v1.0'
    }

    def "buildPrimaryImageReference with sourceRefImageName and empty registry and namespace"() {
        given:
        task.sourceRefRegistry.set('')
        task.sourceRefNamespace.set('')
        task.sourceRefImageName.set('myapp')
        task.sourceRefTag.set('dev')

        when:
        def result = task.buildPrimaryImageReference()

        then:
        result == 'myapp:dev'
    }

}