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

import java.util.concurrent.CompletableFuture

/**
 * Comprehensive unit tests for DockerSaveTask
 * Tests saveImage functionality with various compression types and configurations
 */
class DockerSaveTaskComprehensiveTest extends Specification {

    Project project
    DockerSaveTask task
    DockerService mockDockerService = Mock()

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register('testDockerSave', DockerSaveTask).get()
        task.dockerService.set(mockDockerService)

        // pullIfMissing is now a direct property on the task (defaults to false)
        task.pullIfMissing.set(false)
    }

    def "saveImage handles successful save operation"() {
        given:
        task.sourceRef.set("existing:image")
        task.compression.set(SaveCompression.NONE)
        task.outputFile.set(createOutputFile("image.tar"))
        
        // pullIfMissing already set to false in setup

        when:
        task.saveImage()

        then:
        1 * mockDockerService.saveImage("existing:image", _, SaveCompression.NONE) >> CompletableFuture.completedFuture(null)
        0 * mockDockerService.imageExists(_)
        0 * mockDockerService.pullImage(_, _)
    }

    def "saveImage handles compression correctly"() {
        given:
        task.sourceRef.set("test:image")
        task.compression.set(compressionType)
        task.outputFile.set(createOutputFile("image.tar"))
        
        // pullIfMissing already set to false in setup

        when:
        task.saveImage()

        then:
        1 * mockDockerService.saveImage("test:image", _, compressionType) >> CompletableFuture.completedFuture(null)

        where:
        compressionType << [SaveCompression.NONE, SaveCompression.GZIP, SaveCompression.BZIP2, SaveCompression.XZ, SaveCompression.ZIP]
    }

    def "task fails when outputFile is not set"() {
        given:
        task.sourceRef.set("test:image")
        task.compression.set(SaveCompression.NONE)
        // outputFile not set
        
        // pullIfMissing already set to false in setup

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
        0 * mockDockerService._
    }

    def "task fails when dockerService is not set"() {
        given:
        task.dockerService.set(null)
        task.sourceRef.set("test:image")
        task.outputFile.set(createOutputFile("image.tar"))
        
        // pullIfMissing already set to false in setup

        when:
        task.saveImage()

        then:
        thrown(IllegalStateException)
    }

    def "task builds correct image reference"() {
        when:
        task.sourceRef.set("test:image")
        def result = task.buildPrimaryImageReference()

        then:
        result == "test:image"
    }

    def "task builds image reference from nomenclature when sourceRef is empty"() {
        when:
        task.sourceRef.set("")
        task.tags.set(["latest", "v1.0"])
        task.repository.set("myapp")
        task.registry.set("docker.io")
        def result = task.buildPrimaryImageReference()

        then:
        result == "docker.io/myapp:latest"
    }

    private File createOutputFile(String name) {
        def file = new File(project.buildDir, name)
        file.parentFile.mkdirs()
        return file
    }
}