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
import com.kineticfire.gradle.docker.model.BuildContext
import com.kineticfire.gradle.docker.exception.DockerServiceException

import java.util.concurrent.CompletableFuture

/**
 * Comprehensive unit tests for DockerBuildTask
 */
class DockerBuildTaskComprehensiveTest extends DockerTaskTestBase {

    def "buildImage with new nomenclature - repository format"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.repository.set("namespace/imagename")
        task.registry.set("ghcr.io")
        task.version.set("1.0.0")
        task.tags.set(["1.0.0", "latest"])
        task.labels.set(["maintainer": "team"])
        task.buildArgs.set(["JAR_FILE": "app.jar"])
        task.imageId.set(createOutputFile("image-id.txt"))

        when:
        task.buildImage()

        then:
        1 * mockDockerService.buildImage(_) >> CompletableFuture.completedFuture("sha256:repository123")
    }
    
    def "buildImage with nomenclature - namespace and imageName format"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.namespace.set("mycompany")
        task.imageName.set("myapp")
        task.registry.set("docker.io")
        task.version.set("2.0.0")
        task.tags.set(["v2.0"])
        task.imageId.set(createOutputFile("image-id.txt"))
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == ["docker.io/mycompany/myapp:v2.0"]
    }
    
    def "buildImage validates mutual exclusivity between repository and namespace+imageName"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.repository.set("repo/name")
        task.namespace.set("namespace")
        task.imageName.set("name")
        task.tags.set(["tag"])
        task.imageId.set(createOutputFile("image-id.txt"))

        when:
        task.buildImage()

        then:
        def ex = thrown(Exception)
        ex instanceof IllegalStateException || ex.cause instanceof IllegalStateException
        verifyNoDockerServiceCalls()
    }
    
    def "buildImage requires either repository OR imageName"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.registry.set("docker.io")
        task.tags.set(["tag"])
        task.imageId.set(createOutputFile("image-id.txt"))
        // Neither repository nor imageName set
        
        when:
        task.buildImage()
        
        then:
        thrown(IllegalStateException)
        verifyNoDockerServiceCalls()
    }
    
    def "buildImage validates dockerfile is present"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.contextPath.set(createContextDirectory())
        task.imageName.set("test")
        task.tags.set(["latest"])
        task.imageId.set(createOutputFile("image-id.txt"))
        // No dockerfile set
        
        when:
        task.buildImage()
        
        then:
        thrown(IllegalStateException)
        verifyNoDockerServiceCalls()
    }
    
    def "buildImage with no registry uses default"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.imageName.set("myapp")
        task.tags.set(["latest"])
        task.imageId.set(createOutputFile("image-id.txt"))
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == ["myapp:latest"]
    }
    
    def "buildImage with namespace but no registry"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.namespace.set("mycompany")
        task.imageName.set("myapp")
        task.tags.set(["1.0", "latest"])
        task.imageId.set(createOutputFile("image-id.txt"))
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == ["mycompany/myapp:1.0", "mycompany/myapp:latest"]
    }
    
    def "buildImage with multiple tags"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.repository.set("company/app")
        task.registry.set("registry.io")
        task.tags.set(["1.0.0", "1.0", "latest", "stable"])
        task.imageId.set(createOutputFile("image-id.txt"))
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == [
            "registry.io/company/app:1.0.0",
            "registry.io/company/app:1.0", 
            "registry.io/company/app:latest",
            "registry.io/company/app:stable"
        ]
    }
    
    def "buildImage with all possible labels"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.imageName.set("app")
        task.version.set("1.0.0")
        task.tags.set(["test"])
        task.labels.set([
            "maintainer": "team@company.com",
            "version": "1.0.0",
            "org.opencontainers.image.source": "https://github.com/company/repo",
            "org.opencontainers.image.revision": "abc123def",
            "org.opencontainers.image.created": "2023-01-01T00:00:00Z",
            "custom.label": "custom value"
        ])
        task.imageId.set(createOutputFile("image-id.txt"))

        when:
        task.buildImage()

        then:
        1 * mockDockerService.buildImage(_) >> CompletableFuture.completedFuture("sha256:labelstest123")
    }
    
    def "buildImage with all possible build args"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.imageName.set("app")
        task.version.set("1.0.0")
        task.tags.set(["test"])
        task.buildArgs.set([
            "JAR_FILE": "app-1.0.0.jar",
            "JAVA_VERSION": "17",
            "BASE_IMAGE": "openjdk:17-jre",
            "WORKDIR": "/app",
            "USER_ID": "1000",
            "CUSTOM_ARG": "custom_value"
        ])
        task.imageId.set(createOutputFile("image-id.txt"))

        when:
        task.buildImage()

        then:
        1 * mockDockerService.buildImage(_) >> CompletableFuture.completedFuture("sha256:buildargstest123")
    }
    
    def "buildImage writes image ID to output file"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.imageName.set("app")
        task.version.set("1.0.0")
        task.tags.set(["test"])
        def outputFile = createOutputFile("image-id.txt")
        task.imageId.set(outputFile)

        when:
        task.buildImage()

        then:
        1 * mockDockerService.buildImage(_) >> CompletableFuture.completedFuture("sha256:12345")
        outputFile.asFile.exists()
        outputFile.asFile.text.trim() == "sha256:12345"
    }
    
    def "buildImage handles service failure"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.dockerService.set(mockDockerService)
        task.dockerfile.set(createDockerfile())
        task.contextPath.set(createContextDirectory())
        task.imageName.set("app")
        task.tags.set(["test"])
        task.imageId.set(createOutputFile("image-id.txt"))

        def error = new DockerServiceException(
            DockerServiceException.ErrorType.BUILD_FAILED,
            "Build failed"
        )

        when:
        task.buildImage()

        then:
        1 * mockDockerService.buildImage(_) >> CompletableFuture.failedFuture(error)
        def ex = thrown(java.util.concurrent.ExecutionException)
        ex.cause == error
    }
    
    def "buildImageReferences handles empty repository"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.imageName.set("app")
        task.tags.set(["1.0", "latest"])
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == ["app:1.0", "app:latest"]
    }
    
    def "buildImageReferences handles empty namespace"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.registry.set("docker.io")
        task.imageName.set("app")
        task.tags.set(["test"])
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == ["docker.io/app:test"]
    }
    
    def "buildImageReferences returns empty list when no image name or repository"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.registry.set("docker.io")
        task.tags.set(["test"])
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs.isEmpty()
    }
    
    def "buildImageReferences returns empty list when no tags"() {
        given:
        def task = project.tasks.create("dockerBuildTest", DockerBuildTask)
        task.imageName.set("app")
        // No tags set
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs.isEmpty()
    }
}