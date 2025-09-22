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
import com.kineticfire.gradle.docker.exception.DockerServiceException

import java.util.concurrent.CompletableFuture

/**
 * Comprehensive unit tests for DockerTagTask
 */
class DockerTagTaskComprehensiveTest extends DockerTaskTestBase {

    def "tagImage in sourceRef mode"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("source:image")
        task.tags.set(["new:tag1", "new:tag2"])

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("source:image", ["new:tag1", "new:tag2"]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage in build mode with repository"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.registry.set("docker.io")
        task.repository.set("company/app")
        task.tags.set(["1.0.0", "latest"])

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("docker.io/company/app:1.0.0", ["docker.io/company/app:latest"]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage in build mode with namespace and imageName"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.registry.set("ghcr.io")
        task.namespace.set("mycompany")
        task.imageName.set("myapp")
        task.tags.set(["v1.0", "stable", "production"])

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("ghcr.io/mycompany/myapp:v1.0", [
            "ghcr.io/mycompany/myapp:stable",
            "ghcr.io/mycompany/myapp:production"
        ]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage without registry in build mode"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.namespace.set("company")
        task.imageName.set("app")
        task.tags.set(["1.0", "latest"])

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("company/app:1.0", ["company/app:latest"]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage without namespace in build mode"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.registry.set("registry.io")
        task.imageName.set("app")
        task.tags.set(["test", "experimental"])

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("registry.io/app:test", ["registry.io/app:experimental"]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage with single tag throws exception for insufficient targets"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.imageName.set("app")
        task.tags.set(["single"])

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
        verifyNoDockerServiceCalls()
    }
    
    def "buildImageReferences in dual-mode scenarios"() {
        expect:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        if (sourceRef) task.sourceRef.set(sourceRef)
        if (repository) task.repository.set(repository)
        if (namespace) task.namespace.set(namespace)
        if (imageName) task.imageName.set(imageName)
        if (registry) task.registry.set(registry)
        task.tags.set(tags)
        
        def refs = task.buildImageReferences()
        refs == expectedRefs
        
        where:
        sourceRef   | repository   | namespace | imageName | registry     | tags              | expectedRefs
        "old:1.0"   | null         | null      | null      | null         | ["new:2.0"]       | ["old:1.0", "new:2.0"]
        null        | "repo/name"  | null      | null      | null         | ["1.0", "2.0"]    | ["repo/name:1.0", "repo/name:2.0"]
        null        | null         | "ns"      | "app"     | null         | ["tag1", "tag2"]  | ["ns/app:tag1", "ns/app:tag2"]
        null        | null         | null      | "app"     | "reg.io"     | ["latest"]        | ["reg.io/app:latest"]
        "src:tag"   | "repo/name"  | null      | null      | null         | ["target"]        | ["src:tag", "target"]  // sourceRef takes precedence
    }
    
    def "tagImage validates at least one tag"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("source:image")
        // No tags set
        
        when:
        task.tagImage()
        
        then:
        thrown(IllegalStateException)
        verifyNoDockerServiceCalls()
    }
    
    def "tagImage validates either repository OR imageName when not using sourceRef"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.registry.set("registry.io")
        task.tags.set(["tag"])
        // No sourceRef, repository, or imageName set
        
        when:
        task.tagImage()
        
        then:
        thrown(IllegalStateException)
        verifyNoDockerServiceCalls()
    }
    
    def "tagImage with complex image references"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("complex.registry.io:5000/deep/namespace/app:v1.0.0")
        task.tags.set([
            "simple:tag",
            "registry.io/target:latest",
            "another.registry.io:8080/namespace/app:stable"
        ])

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("complex.registry.io:5000/deep/namespace/app:v1.0.0", [
            "simple:tag",
            "registry.io/target:latest",
            "another.registry.io:8080/namespace/app:stable"
        ]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage handles service failure"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        task.sourceRef.set("source:image")
        task.tags.set(["target:tag"])

        def error = new DockerServiceException(
            DockerServiceException.ErrorType.TAG_FAILED,
            "Tag failed"
        )

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("source:image", ["target:tag"]) >> CompletableFuture.failedFuture(error)
        def ex = thrown(java.util.concurrent.ExecutionException)
        ex.cause == error
    }
    
    def "buildImageReferences handles empty tags gracefully"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.sourceRef.set("source:image")
        task.tags.set([])
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == ["source:image"]  // Source is still included even with no target tags
    }
    
    def "buildImageReferences with repository format and multiple tags"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.registry.set("registry.example.com")
        task.repository.set("organization/project")
        task.tags.set(["1.0.0", "1.0", "latest", "stable", "production"])
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == [
            "registry.example.com/organization/project:1.0.0",
            "registry.example.com/organization/project:1.0",
            "registry.example.com/organization/project:latest",
            "registry.example.com/organization/project:stable",
            "registry.example.com/organization/project:production"
        ]
    }
    
    def "buildImageReferences with namespace format and multiple tags"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.registry.set("ghcr.io")
        task.namespace.set("myorganization/team")
        task.imageName.set("application")
        task.tags.set(["dev", "test", "staging", "prod"])
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == [
            "ghcr.io/myorganization/team/application:dev",
            "ghcr.io/myorganization/team/application:test",
            "ghcr.io/myorganization/team/application:staging",
            "ghcr.io/myorganization/team/application:prod"
        ]
    }
    
    def "buildImageReferences with minimal configuration"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.imageName.set("app")
        task.tags.set(["latest"])
        
        when:
        def refs = task.buildImageReferences()
        
        then:
        refs == ["app:latest"]
    }
}