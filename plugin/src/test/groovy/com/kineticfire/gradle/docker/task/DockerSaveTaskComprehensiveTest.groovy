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

import com.kineticfire.gradle.docker.base.DockerTaskTestBase
import com.kineticfire.gradle.docker.model.SaveCompression
import com.kineticfire.gradle.docker.exception.DockerServiceException
import java.util.concurrent.CompletableFuture

/**
 * Comprehensive unit tests for DockerSaveTask
 */
class DockerSaveTaskComprehensiveTest extends DockerTaskTestBase {

    def "saveImage in build mode with nomenclature"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.registry.set("docker.io")
        task.namespace.set("company")
        task.imageName.set("app")
        task.version.set("1.0.0")
        task.tags.set(["1.0.0", "latest"])
        task.compression.set(SaveCompression.GZIP)
        task.outputFile.set(createOutputFile("image.tar.gz"))

        when:
        task.saveImage()

        then:
        1 * mockDockerService.saveImage(_, _, _) >> CompletableFuture.completedFuture(null)
    }
    
    def "saveImage in sourceRef mode with pullIfMissing false"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)

        // Reset the mock to clear any default behaviors
        mockDockerService = Mock(com.kineticfire.gradle.docker.service.DockerService)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("existing:image")
        task.pullIfMissing.set(false)
        task.compression.set(SaveCompression.NONE)
        task.outputFile.set(createOutputFile("image.tar"))

        when:
        task.saveImage()

        then:
        0 * mockDockerService.imageExists(_)
        0 * mockDockerService.pullImage(_, _)
        1 * mockDockerService.saveImage(_, _, _) >> CompletableFuture.completedFuture(null)
    }
    
    def "saveImage in sourceRef mode with pullIfMissing true and image exists"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("existing:image")
        task.pullIfMissing.set(true)
        task.compression.set(SaveCompression.GZIP)
        task.outputFile.set(createOutputFile("image.tar.gz"))

        when:
        task.saveImage()

        then:
        1 * mockDockerService.imageExists("existing:image") >> CompletableFuture.completedFuture(true)
        0 * mockDockerService.pullImage(_, _)
        1 * mockDockerService.saveImage("existing:image", _, SaveCompression.GZIP) >> CompletableFuture.completedFuture(null)
    }
    
    def "saveImage in sourceRef mode with pullIfMissing true and image missing"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("missing:image")
        task.pullIfMissing.set(true)
        task.compression.set(SaveCompression.ZIP)
        task.outputFile.set(createOutputFile("image.zip"))

        when:
        task.saveImage()

        then:
        1 * mockDockerService.imageExists("missing:image") >> CompletableFuture.completedFuture(false)
        1 * mockDockerService.pullImage("missing:image", null) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.saveImage("missing:image", _, SaveCompression.ZIP) >> CompletableFuture.completedFuture(null)
    }
    
    def "saveImage in sourceRef mode with pullIfMissing true and authentication"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("private.registry.io/image:tag")
        task.pullIfMissing.set(true)
        task.auth.set(createAuthSpec())
        task.compression.set(SaveCompression.BZIP2)
        task.outputFile.set(createOutputFile("image.tar.bz2"))

        when:
        task.saveImage()

        then:
        1 * mockDockerService.imageExists("private.registry.io/image:tag") >> CompletableFuture.completedFuture(false)
        1 * mockDockerService.pullImage("private.registry.io/image:tag", _) >> CompletableFuture.completedFuture(null)
        1 * mockDockerService.saveImage("private.registry.io/image:tag", _, SaveCompression.BZIP2) >> CompletableFuture.completedFuture(null)
    }
    
    def "saveImage with each compression type"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("test:image")
        task.compression.set(compression)
        task.outputFile.set(createOutputFile("image${extension}"))

        when:
        task.saveImage()

        then:
        1 * mockDockerService.saveImage("test:image", _, compression) >> CompletableFuture.completedFuture(null)

        where:
        compression              | extension
        SaveCompression.NONE     | ".tar"
        SaveCompression.GZIP     | ".tar.gz"
        SaveCompression.ZIP      | ".zip"
        SaveCompression.BZIP2    | ".tar.bz2"
        SaveCompression.XZ       | ".tar.xz"
    }
    
    def "saveImage validates outputFile is present"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("test:image")
        task.compression.set(SaveCompression.NONE)
        // No outputFile set
        
        when:
        task.saveImage()
        
        then:
        thrown(IllegalStateException)
        verifyNoDockerServiceCalls()
    }
    
    def "buildPrimaryImageReference in sourceRef mode"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.sourceRef.set("external:reference")
        task.tags.set(["some", "tags"])  // These should be ignored in sourceRef mode
        
        when:
        def ref = task.buildPrimaryImageReference()
        
        then:
        ref == "external:reference"
    }
    
    def "buildPrimaryImageReference in build mode with repository"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.registry.set("registry.io")
        task.repository.set("company/app")
        task.tags.set(["1.0.0", "latest"])
        
        when:
        def ref = task.buildPrimaryImageReference()
        
        then:
        ref == "registry.io/company/app:1.0.0"  // First tag is used
    }
    
    def "buildPrimaryImageReference in build mode with namespace and imageName"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.registry.set("docker.io")
        task.namespace.set("mycompany")
        task.imageName.set("myapp")
        task.tags.set(["v2.0", "stable"])
        
        when:
        def ref = task.buildPrimaryImageReference()
        
        then:
        ref == "docker.io/mycompany/myapp:v2.0"
    }
    
    def "buildPrimaryImageReference without registry in build mode"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.namespace.set("company")
        task.imageName.set("app")
        task.tags.set(["test"])
        
        when:
        def ref = task.buildPrimaryImageReference()
        
        then:
        ref == "company/app:test"
    }
    
    def "buildPrimaryImageReference without namespace in build mode"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.registry.set("registry.io")
        task.imageName.set("app")
        task.tags.set(["tag"])
        
        when:
        def ref = task.buildPrimaryImageReference()
        
        then:
        ref == "registry.io/app:tag"
    }
    
    def "buildPrimaryImageReference returns null when no tags in build mode"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.imageName.set("app")
        // No tags set
        
        when:
        def ref = task.buildPrimaryImageReference()
        
        then:
        ref == null
    }
    
    def "buildPrimaryImageReference returns null when no repository or imageName"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.registry.set("registry.io")
        task.tags.set(["tag"])
        // No repository or imageName set
        
        when:
        def ref = task.buildPrimaryImageReference()
        
        then:
        ref == null
    }
    
    def "buildPrimaryImageReference returns null when no sourceRef and no build properties"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        // No sourceRef, repository, or imageName set
        
        when:
        def ref = task.buildPrimaryImageReference()
        
        then:
        ref == null
    }
    
    def "saveImage handles null primary reference"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.compression.set(SaveCompression.NONE)
        task.outputFile.set(createOutputFile("image.tar"))
        // No sourceRef or build properties set
        
        when:
        task.saveImage()
        
        then:
        thrown(IllegalStateException)
        verifyNoDockerServiceCalls()
    }
    
    def "saveImage handles service failure"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("test:image")
        task.compression.set(SaveCompression.NONE)
        task.outputFile.set(createOutputFile("image.tar"))

        def error = new DockerServiceException(
            DockerServiceException.ErrorType.SAVE_FAILED,
            "Save failed"
        )

        when:
        task.saveImage()

        then:
        1 * mockDockerService.saveImage("test:image", _, SaveCompression.NONE) >> CompletableFuture.failedFuture(error)
        def ex = thrown(java.util.concurrent.ExecutionException)
        ex.cause == error
    }
    
    def "saveImage handles authentication failure during pull"() {
        given:
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("private:image")
        task.pullIfMissing.set(true)
        task.auth.set(createAuthSpec("wronguser", "wrongpass"))
        task.compression.set(SaveCompression.NONE)
        task.outputFile.set(createOutputFile("image.tar"))

        def authError = new DockerServiceException(
            DockerServiceException.ErrorType.PULL_FAILED,
            "Authentication failed"
        )

        when:
        task.saveImage()

        then:
        1 * mockDockerService.imageExists("private:image") >> CompletableFuture.completedFuture(false)
        1 * mockDockerService.pullImage(_, _) >> CompletableFuture.failedFuture(authError)
        def ex = thrown(Exception)
        ex.cause == authError
    }
}