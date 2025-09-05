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

import com.kineticfire.gradle.docker.model.CompressionType
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

    def "task has correct group and description"() {
        expect:
        task.group == 'docker'
        task.description == 'Save Docker image to file'
    }

    def "task has saveImage action"() {
        expect:
        task.actions.size() == 1
        task.actions[0].displayName.contains('saveImage')
    }
    
    def "task has default property values"() {
        expect:
        task.compression.get() == CompressionType.GZIP
        task.pullIfMissing.get() == false
    }

    // ===== TASK EXECUTION TESTS =====

    def "saveImage action validates imageName property"() {
        given:
        task.imageName.set('test-image:latest')
        task.outputFile.set(project.file('test.tar.gz'))
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
        compression == CompressionType.GZIP
    }
    
    def "saveImage action validates sourceRef property"() {
        given:
        task.sourceRef.set('registry.example.com/test:latest')
        task.outputFile.set(project.file('test.tar.gz'))
        task.pullIfMissing.set(true)

        when:
        def sourceRef = task.sourceRef.get()
        def pullIfMissing = task.pullIfMissing.get()

        then:
        sourceRef == 'registry.example.com/test:latest'
        pullIfMissing == true
    }

    def "task configuration supports all properties"() {
        when:
        task.sourceRef.set('registry.example.com/test:latest')
        task.imageName.set('local-image:latest')
        task.outputFile.set(project.file('test.tar.gz'))
        task.compression.set(CompressionType.BZIP2)
        task.pullIfMissing.set(true)

        then:
        task.sourceRef.get() == 'registry.example.com/test:latest'
        task.imageName.get() == 'local-image:latest'
        task.outputFile.get().asFile.name == 'test.tar.gz'
        task.compression.get() == CompressionType.BZIP2
        task.pullIfMissing.get() == true
    }
    
    def "task fails when neither imageName nor sourceRef is set"() {
        given:
        task.outputFile.set(project.file('test.tar.gz'))

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
    }
    
    def "task fails when outputFile is not set"() {
        given:
        task.imageName.set('test-image:latest')

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
    }

    // ===== TASK CONFIGURATION TESTS =====

    def "task can be configured with different compression types"() {
        given:
        task.compression.set(CompressionType.NONE)
        
        expect:
        task.compression.get() == CompressionType.NONE
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
        compressionType << [CompressionType.NONE, CompressionType.GZIP, CompressionType.BZIP2, CompressionType.XZ, CompressionType.ZIP]
    }
    
    // ===== EDGE CASES =====

    def "task allows both sourceRef and imageName to be set"() {
        when:
        task.imageName.set('image-name:latest')
        task.sourceRef.set('registry.example.com/source:latest')
        task.outputFile.set(project.file('test.tar.gz'))

        then:
        task.imageName.get() == 'image-name:latest'
        task.sourceRef.get() == 'registry.example.com/source:latest'
        task.outputFile.get().asFile.name == 'test.tar.gz'
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
        
        task.compression.get() == CompressionType.GZIP  // default
        task.pullIfMissing.get() == false  // default
    }
    
    def "task can be configured with different compression formats"() {
        when:
        task.compression.set(compressionType)
        
        then:
        task.compression.get() == compressionType
        
        where:
        compressionType << [CompressionType.NONE, CompressionType.GZIP, CompressionType.BZIP2, CompressionType.XZ, CompressionType.ZIP]
    }
    
    def "task supports pullIfMissing configurations"() {
        when:
        task.pullIfMissing.set(value)
        
        then:
        task.pullIfMissing.get() == value
        
        where:
        value << [true, false]
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
}