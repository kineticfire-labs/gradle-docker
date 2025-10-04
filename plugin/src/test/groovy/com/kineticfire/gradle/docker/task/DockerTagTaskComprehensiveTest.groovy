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
        def imageSpec = createImageSpec()
        imageSpec.sourceRef.set("source:image")
        imageSpec.tags.set(["new:tag1", "new:tag2"])
        task.imageSpec.set(imageSpec)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("source:image", ["new:tag1", "new:tag2"]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage in build mode with repository"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        def imageSpec = createImageSpec()
        imageSpec.registry.set("docker.io")
        imageSpec.repository.set("company/app")
        imageSpec.tags.set(["1.0.0", "latest"])
        task.imageSpec.set(imageSpec)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("docker.io/company/app:1.0.0", ["docker.io/company/app:latest"]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage in build mode with namespace and imageName"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        def imageSpec = createImageSpec()
        imageSpec.registry.set("ghcr.io")
        imageSpec.namespace.set("mycompany")
        imageSpec.imageName.set("myapp")
        imageSpec.tags.set(["v1.0", "stable", "production"])
        task.imageSpec.set(imageSpec)

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
        def imageSpec = createImageSpec()
        imageSpec.namespace.set("company")
        imageSpec.imageName.set("app")
        imageSpec.tags.set(["1.0", "latest"])
        task.imageSpec.set(imageSpec)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("company/app:1.0", ["company/app:latest"]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage without namespace in build mode"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        def imageSpec = createImageSpec()
        imageSpec.registry.set("registry.io")
        imageSpec.imageName.set("app")
        imageSpec.tags.set(["test", "experimental"])
        task.imageSpec.set(imageSpec)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("registry.io/app:test", ["registry.io/app:experimental"]) >> CompletableFuture.completedFuture(null)
    }
    
    def "tagImage with single tag is no-op in build mode"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        def imageSpec = createImageSpec()
        imageSpec.imageName.set("app")
        imageSpec.tags.set(["single"])
        task.imageSpec.set(imageSpec)

        when:
        task.tagImage()

        then:
        // Should be no-op, no Docker service calls expected
        0 * mockDockerService.tagImage(_, _)
        verifyNoDockerServiceCalls()
    }
    
    def "buildImageReferences in dual-mode scenarios"() {
        expect:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        def imageSpec = createImageSpec()
        if (sourceRef) imageSpec.sourceRef.set(sourceRef)
        if (repository) imageSpec.repository.set(repository)
        if (namespace) imageSpec.namespace.set(namespace)
        if (imageName) imageSpec.imageName.set(imageName)
        if (registry) imageSpec.registry.set(registry)
        imageSpec.tags.set(tags)
        task.imageSpec.set(imageSpec)

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
        def imageSpec = createImageSpec()
        imageSpec.sourceRef.set("source:image")
        // No tags set (empty list)
        imageSpec.tags.set([])
        task.imageSpec.set(imageSpec)

        when:
        task.tagImage()

        then:
        // Should not throw in sourceRef mode with empty tags (it's a no-op)
        notThrown(IllegalStateException)
        verifyNoDockerServiceCalls()
    }
    
    def "tagImage validates either repository OR imageName when not using sourceRef"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        def imageSpec = createImageSpec()
        imageSpec.registry.set("registry.io")
        imageSpec.tags.set(["tag"])
        // No sourceRef, repository, or imageName set
        task.imageSpec.set(imageSpec)

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
        def imageSpec = createImageSpec()
        imageSpec.sourceRef.set("complex.registry.io:5000/deep/namespace/app:v1.0.0")
        imageSpec.tags.set([
            "simple:tag",
            "registry.io/target:latest",
            "another.registry.io:8080/namespace/app:stable"
        ])
        task.imageSpec.set(imageSpec)

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
        def imageSpec = createImageSpec()
        imageSpec.sourceRef.set("source:image")
        imageSpec.tags.set(["target:tag"])
        task.imageSpec.set(imageSpec)

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
        def imageSpec = createImageSpec()
        imageSpec.sourceRef.set("source:image")
        imageSpec.tags.set([])
        task.imageSpec.set(imageSpec)

        when:
        def refs = task.buildImageReferences()

        then:
        refs == ["source:image"]  // Source is still included even with no target tags
    }
    
    def "buildImageReferences with repository format and multiple tags"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        def imageSpec = createImageSpec()
        imageSpec.registry.set("registry.example.com")
        imageSpec.repository.set("organization/project")
        imageSpec.tags.set(["1.0.0", "1.0", "latest", "stable", "production"])
        task.imageSpec.set(imageSpec)

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
        def imageSpec = createImageSpec()
        imageSpec.registry.set("ghcr.io")
        imageSpec.namespace.set("myorganization/team")
        imageSpec.imageName.set("application")
        imageSpec.tags.set(["dev", "test", "staging", "prod"])
        task.imageSpec.set(imageSpec)

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
        def imageSpec = createImageSpec()
        imageSpec.imageName.set("app")
        imageSpec.tags.set(["latest"])
        task.imageSpec.set(imageSpec)

        when:
        def refs = task.buildImageReferences()

        then:
        refs == ["app:latest"]
    }
    
    // ===== PHASE 2: DUAL-MODE TASK BEHAVIOR ENHANCEMENT TESTS =====
    
    def "DockerTagTask builds references correctly in SourceRef mode"() {
        given: "Task configured with sourceRef mode"
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        def imageSpec = createImageSpec()
        imageSpec.sourceRef.set("external.registry.io/existing/app:v2.1.0")
        imageSpec.tags.set(["local:latest", "local:stable", "test:v2.1.0"])
        task.imageSpec.set(imageSpec)

        when: "Building image references"
        def refs = task.buildImageReferences()

        then: "References are built correctly for SourceRef mode"
        refs.size() == 4
        refs[0] == "external.registry.io/existing/app:v2.1.0"  // Source reference
        refs[1] == "local:latest"   // Target tags
        refs[2] == "local:stable"
        refs[3] == "test:v2.1.0"
    }
    
    def "DockerTagTask builds references correctly in Build mode"() {
        given: "Task configured with Build mode using nomenclature"
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        def imageSpec = createImageSpec()
        imageSpec.registry.set("build.registry.io")
        imageSpec.namespace.set("mycompany/team")
        imageSpec.imageName.set("newapp")
        imageSpec.tags.set(["v1.0.0", "latest", "release"])
        task.imageSpec.set(imageSpec)

        when: "Building image references"
        def refs = task.buildImageReferences()

        then: "References are built correctly for Build mode"
        refs.size() == 3
        refs[0] == "build.registry.io/mycompany/team/newapp:v1.0.0"  // Primary reference
        refs[1] == "build.registry.io/mycompany/team/newapp:latest"  // Additional tags
        refs[2] == "build.registry.io/mycompany/team/newapp:release"
    }
    
    def "DockerTagTask validates mode requirements correctly"() {
        when: "Task has neither sourceRef nor imageName/repository"
        def task1 = project.tasks.create("dockerTagTest1", DockerTagTask)
        def imageSpec1 = createImageSpec()
        imageSpec1.registry.set("registry.io")
        imageSpec1.tags.set(["tag"])
        // Missing both sourceRef and imageName/repository
        task1.imageSpec.set(imageSpec1)
        task1.buildImageReferences()

        then: "Validation fails for insufficient configuration"
        thrown(IllegalStateException)

        when: "Task has both sourceRef and build mode properties"
        def task2 = project.tasks.create("dockerTagTest2", DockerTagTask)
        def imageSpec2 = createImageSpec()
        imageSpec2.sourceRef.set("source:image")
        imageSpec2.imageName.set("conflicting")
        imageSpec2.tags.set(["tag"])
        task2.imageSpec.set(imageSpec2)
        def refs2 = task2.buildImageReferences()

        then: "SourceRef takes precedence (legacy behavior preserved)"
        refs2[0] == "source:image"
        refs2[1] == "tag"
    }
    
    def "DockerTagTask handles empty tag lists appropriately"() {
        given: "Task with sourceRef but no tags"
        def task1 = project.tasks.create("dockerTagTest1", DockerTagTask)
        def imageSpec1 = createImageSpec()
        imageSpec1.sourceRef.set("source:image")
        imageSpec1.tags.set([])
        task1.imageSpec.set(imageSpec1)

        when: "Building references with empty tags"
        def refs1 = task1.buildImageReferences()

        then: "Source reference is preserved even without targets"
        refs1 == ["source:image"]

        and: "Task with build mode but no tags"
        def task2 = project.tasks.create("dockerTagTest2", DockerTagTask)
        def imageSpec2 = createImageSpec()
        imageSpec2.imageName.set("app")
        imageSpec2.tags.set([])
        task2.imageSpec.set(imageSpec2)

        when: "Building references with empty tags in build mode"
        task2.buildImageReferences()

        then: "IllegalStateException thrown for build mode without tags"
        thrown(IllegalStateException)
    }
    
    def "DockerTagTask detects mode correctly based on properties"() {
        expect: "Mode detection works correctly for various configurations"
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        def imageSpec = createImageSpec()
        if (sourceRef) imageSpec.sourceRef.set(sourceRef)
        if (imageName) imageSpec.imageName.set(imageName)
        if (repository) imageSpec.repository.set(repository)
        imageSpec.tags.set(tags)
        task.imageSpec.set(imageSpec)

        def refs = task.buildImageReferences()
        def isSourceRefMode = refs.size() > 0 && refs[0] == sourceRef
        isSourceRefMode == expectedSourceRefMode

        where:
        sourceRef     | imageName | repository   | tags        | expectedSourceRefMode
        "src:tag"     | null      | null         | ["target"]  | true    // SourceRef mode
        null          | "app"     | null         | ["tag"]     | false   // Build mode (imageName)
        null          | null      | "repo/app"   | ["tag"]     | false   // Build mode (repository)
        "src:tag"     | "app"     | null         | ["target"]  | true    // SourceRef takes precedence
        "src:tag"     | null      | "repo/app"   | ["target"]  | true    // SourceRef takes precedence
    }
    
    def "tagImage with single tag in various build configurations is no-op"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        def imageSpec = createImageSpec()
        if (registry) imageSpec.registry.set(registry)
        if (namespace) imageSpec.namespace.set(namespace)
        if (imageName) imageSpec.imageName.set(imageName)
        if (repository) imageSpec.repository.set(repository)
        imageSpec.tags.set([singleTag])
        task.imageSpec.set(imageSpec)

        when:
        task.tagImage()

        then:
        0 * mockDockerService.tagImage(_, _)

        where:
        registry    | namespace | imageName | repository   | singleTag
        null        | null      | "app"     | null         | "latest"
        "reg.io"    | null      | "app"     | null         | "stable"
        null        | "ns"      | "app"     | null         | "v1.0"
        "reg.io"    | "ns"      | "app"     | null         | "test"
        null        | null      | null      | "repo/app"   | "latest"
        "reg.io"    | null      | null      | "repo/app"   | "stable"
    }
    
    def "tagImage with multiple tags works correctly in build mode"() {
        given:
        def task = project.tasks.create("dockerTagTest", DockerTagTask)
        task.dockerService.set(mockDockerService)
        def imageSpec = createImageSpec()
        imageSpec.imageName.set("testapp")
        imageSpec.tags.set(["primary", "secondary", "tertiary"])
        task.imageSpec.set(imageSpec)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("testapp:primary", ["testapp:secondary", "testapp:tertiary"]) >> CompletableFuture.completedFuture(null)
    }
}