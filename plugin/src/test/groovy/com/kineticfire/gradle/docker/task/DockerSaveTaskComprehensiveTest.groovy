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
    
    // ===== PHASE 2: DUAL-MODE TASK BEHAVIOR ENHANCEMENT TESTS =====
    
    def "DockerSaveTask pulls missing image with auth in SourceRef mode"() {
        given: "Task configured with sourceRef mode and authentication"
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("private.registry.io/secure/app:v1.0")
        task.pullIfMissing.set(true)
        task.auth.set(createAuthSpec("authuser", "authpass"))
        task.compression.set(SaveCompression.GZIP)
        task.outputFile.set(createOutputFile("authenticated-image.tar.gz"))
        
        when: "Save operation is executed"
        task.saveImage()
        
        then: "Image is checked, pulled with auth, then saved"
        1 * mockDockerService.imageExists("private.registry.io/secure/app:v1.0") >> CompletableFuture.completedFuture(false)
        1 * mockDockerService.pullImage("private.registry.io/secure/app:v1.0", _) >> { imageRef, authConfig ->
            assert authConfig != null
            assert authConfig.username == "authuser"
            assert authConfig.password == "authpass"
            return CompletableFuture.completedFuture(null)
        }
        1 * mockDockerService.saveImage("private.registry.io/secure/app:v1.0", _, SaveCompression.GZIP) >> CompletableFuture.completedFuture(null)
    }
    
    def "DockerSaveTask builds primary image reference in both modes"() {
        expect: "Primary reference is built correctly for different modes"
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        
        // Configure task based on mode
        if (sourceRef) {
            task.sourceRef.set(sourceRef)
        } else {
            if (registry) task.registry.set(registry)
            if (namespace) task.namespace.set(namespace)
            if (imageName) task.imageName.set(imageName)
            if (repository) task.repository.set(repository)
            if (tags) task.tags.set(tags)
        }
        
        def primaryRef = task.buildPrimaryImageReference()
        primaryRef == expectedRef
        
        where:
        sourceRef                    | registry      | namespace  | imageName | repository    | tags            | expectedRef
        "external:v1.0"             | null          | null       | null      | null          | null            | "external:v1.0"
        null                        | "docker.io"   | "company"  | "app"     | null          | ["1.0", "2.0"]  | "docker.io/company/app:1.0"
        null                        | "registry.io" | null       | null      | "org/project" | ["latest"]      | "registry.io/org/project:latest"
        null                        | null          | "ns"       | "app"     | null          | ["tag"]         | "ns/app:tag"
        "source:ref"                | "registry.io" | "ns"       | "app"     | null          | ["tag"]         | "source:ref"  // SourceRef takes precedence
    }
    
    def "DockerSaveTask uses auth configuration for pullIfMissing"() {
        given: "Task with various auth configurations"
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("registry.com/private:image")
        task.pullIfMissing.set(true)
        task.compression.set(SaveCompression.NONE)
        task.outputFile.set(createOutputFile("private-image.tar"))
        
        def authSpec = null
        
        if (authType == "userpass") {
            authSpec = createAuthSpec("user", "pass")
        } else if (authType == "token") {
            authSpec = createAuthSpec("tokenuser", null, "token")
        }
        
        if (authSpec) {
            task.auth.set(authSpec)
        }
        
        when: "Save operation is executed"
        task.saveImage()
        
        then: "Pull is called with correct auth configuration"
        1 * mockDockerService.imageExists("registry.com/private:image") >> CompletableFuture.completedFuture(false)
        if (authType == null) {
            1 * mockDockerService.pullImage("registry.com/private:image", null) >> CompletableFuture.completedFuture(null)
        } else if (authType == "userpass") {
            1 * mockDockerService.pullImage("registry.com/private:image", { it != null && it.username == "user" && it.password == "pass" }) >> CompletableFuture.completedFuture(null)
        } else if (authType == "token") {
            1 * mockDockerService.pullImage("registry.com/private:image", { it != null && it.username == "tokenuser" && it.registryToken == "token" }) >> CompletableFuture.completedFuture(null)
        }
        1 * mockDockerService.saveImage("registry.com/private:image", _, SaveCompression.NONE) >> CompletableFuture.completedFuture(null)
        
        where:
        authType << [null, "userpass", "token"]
    }
    
    // ===== PHASE 2: SAVECOMPRESSION SERVICE INTEGRATION TESTS =====
    
    def "DockerSaveTask passes SaveCompression enum to service correctly"() {
        given: "Task configured with specific compression type"
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("test:image")
        task.compression.set(SaveCompression.GZIP)
        task.outputFile.set(createOutputFile("image.tar.gz"))
        
        when: "Save operation is executed"
        task.saveImage()
        
        then: "Service is called with exact SaveCompression enum value"
        1 * mockDockerService.saveImage("test:image", _, SaveCompression.GZIP) >> { imageRef, outputFile, compression ->
            assert compression == SaveCompression.GZIP
            assert compression instanceof SaveCompression
            return CompletableFuture.completedFuture(null)
        }
    }
    
    def "DockerSaveTask handles all SaveCompression types"() {
        expect: "All compression types are passed correctly to service"
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("test:image")
        task.compression.set(compressionType)
        task.outputFile.set(createOutputFile("image${fileExtension}"))
        
        when: "Save operation is executed"
        task.saveImage()
        
        then: "Service receives correct enum value"
        1 * mockDockerService.saveImage("test:image", _, compressionType) >> { imageRef, outputFile, compression ->
            assert compression == compressionType
            assert compression instanceof SaveCompression
            return CompletableFuture.completedFuture(null)
        }
        
        where:
        compressionType          | fileExtension
        SaveCompression.NONE     | ".tar"
        SaveCompression.GZIP     | ".tar.gz"
        SaveCompression.ZIP      | ".zip"
        SaveCompression.BZIP2    | ".tar.bz2"
        SaveCompression.XZ       | ".tar.xz"
    }
    
    def "DockerSaveTask uses NONE compression by default"() {
        given: "Task without explicit compression setting"
        def task = project.tasks.create("dockerSaveTest", DockerSaveTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("test:image")
        task.outputFile.set(createOutputFile("image.tar"))
        // No compression explicitly set - should use default
        
        when: "Save operation is executed"
        task.saveImage()
        
        then: "Service receives default NONE compression"
        1 * mockDockerService.saveImage("test:image", _, SaveCompression.NONE) >> { imageRef, outputFile, compression ->
            assert compression == SaveCompression.NONE
            return CompletableFuture.completedFuture(null)
        }
    }
}