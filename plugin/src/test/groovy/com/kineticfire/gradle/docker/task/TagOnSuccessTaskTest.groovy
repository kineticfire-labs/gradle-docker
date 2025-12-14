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

import com.kineticfire.gradle.docker.generator.PipelineStateFile
import com.kineticfire.gradle.docker.service.DockerService
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for TagOnSuccessTask
 */
class TagOnSuccessTaskTest extends Specification {

    @TempDir
    Path tempDir

    Project project
    TagOnSuccessTask task
    DockerService mockDockerService = Mock()
    File testResultFile

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        task = project.tasks.register('testTagOnSuccess', TagOnSuccessTask).get()
        task.dockerService.set(mockDockerService)
        testResultFile = tempDir.resolve("test-result.json").toFile()
    }

    // ===== BASIC TESTS =====

    def "task can be created"() {
        expect:
        task != null
        task instanceof TagOnSuccessTask
    }

    def "task has default property values"() {
        expect:
        task.imageName.get() == ""
        task.additionalTags.get() == []
        task.sourceImageRef.get() == ""
    }

    // ===== SUCCESSFUL TAGGING TESTS =====

    def "tagImage applies tags when tests passed"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, true, "Tests passed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("myapp")
        task.additionalTags.set(["tested", "verified"])

        mockDockerService.tagImage("myapp:latest", ["myapp:tested", "myapp:verified"]) >>
            CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("myapp:latest", ["myapp:tested", "myapp:verified"]) >>
            CompletableFuture.completedFuture(null)
    }

    def "tagImage uses sourceImageRef when specified"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, true, "Tests passed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("myapp")
        task.sourceImageRef.set("myapp:1.0.0")
        task.additionalTags.set(["tested"])

        mockDockerService.tagImage("myapp:1.0.0", ["myapp:tested"]) >>
            CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("myapp:1.0.0", ["myapp:tested"]) >>
            CompletableFuture.completedFuture(null)
    }

    def "tagImage handles tags with full references"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, true, "Tests passed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("myapp")
        task.additionalTags.set(["registry.example.com/myapp:tested"])

        mockDockerService.tagImage("myapp:latest", ["registry.example.com/myapp:tested"]) >>
            CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("myapp:latest", ["registry.example.com/myapp:tested"]) >>
            CompletableFuture.completedFuture(null)
    }

    // ===== SKIP CONDITIONS TESTS =====

    def "tagImage skips when tests failed"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, false, "Tests failed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("myapp")
        task.additionalTags.set(["tested"])

        when:
        task.tagImage()

        then:
        0 * mockDockerService.tagImage(_, _)
    }

    def "tagImage skips when test result file not found"() {
        given:
        def nonExistentFile = tempDir.resolve("non-existent.json").toFile()
        task.testResultFile.set(nonExistentFile)
        task.imageName.set("myapp")
        task.additionalTags.set(["tested"])

        when:
        task.tagImage()

        then:
        0 * mockDockerService.tagImage(_, _)
    }

    def "tagImage skips when no additional tags specified"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, true, "Tests passed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("myapp")
        task.additionalTags.set([])

        when:
        task.tagImage()

        then:
        0 * mockDockerService.tagImage(_, _)
    }

    // ===== ERROR TESTS =====

    def "tagImage throws when dockerService is null"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, true, "Tests passed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("myapp")
        task.additionalTags.set(["tested"])
        task.dockerService.set(null)

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    def "tagImage throws when imageName is empty"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, true, "Tests passed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("")
        task.additionalTags.set(["tested"])

        when:
        task.tagImage()

        then:
        thrown(IllegalStateException)
    }

    // ===== PROPERTY CONFIGURATION TESTS =====

    def "task allows property configuration"() {
        when:
        task.imageName.set("my-image")
        task.additionalTags.set(["v1.0.0", "stable"])
        task.sourceImageRef.set("my-image:build-123")

        then:
        task.imageName.get() == "my-image"
        task.additionalTags.get() == ["v1.0.0", "stable"]
        task.sourceImageRef.get() == "my-image:build-123"
    }

    def "task allows adding tags incrementally"() {
        when:
        task.additionalTags.add("tag1")
        task.additionalTags.add("tag2")
        task.additionalTags.add("tag3")

        then:
        task.additionalTags.get() == ["tag1", "tag2", "tag3"]
    }

    def "dockerService property can be configured"() {
        when:
        task.dockerService.set(mockDockerService)

        then:
        task.dockerService.get() == mockDockerService
    }

    // ===== VARIOUS TAG FORMATS TESTS =====

    def "tagImage handles various tag formats"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, true, "Tests passed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("myapp")
        task.additionalTags.set(tagList)

        mockDockerService.tagImage(_, _) >> CompletableFuture.completedFuture(null)

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("myapp:latest", _) >> CompletableFuture.completedFuture(null)

        where:
        tagList << [
            ["v1.0.0"],
            ["tested", "stable"],
            ["v1.0.0", "latest", "stable"],
            ["registry.example.com/myapp:v1.0.0"]
        ]
    }

    def "tagImage constructs full tag references correctly"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, true, "Tests passed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("myapp")
        task.additionalTags.set(["v1", "v1.0"])

        mockDockerService.tagImage("myapp:latest", _) >> { String source, List<String> tags ->
            assert tags == ["myapp:v1", "myapp:v1.0"]
            return CompletableFuture.completedFuture(null)
        }

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("myapp:latest", ["myapp:v1", "myapp:v1.0"]) >>
            CompletableFuture.completedFuture(null)
    }

    // ===== MIXED TAG REFERENCE TESTS =====

    def "tagImage handles mix of simple tags and full references"() {
        given:
        PipelineStateFile.writeTestResult(testResultFile, true, "Tests passed", 123L)
        task.testResultFile.set(testResultFile)
        task.imageName.set("myapp")
        task.additionalTags.set(["tested", "registry.example.com/myapp:tested"])

        mockDockerService.tagImage("myapp:latest", _) >> { String source, List<String> tags ->
            assert tags.contains("myapp:tested")
            assert tags.contains("registry.example.com/myapp:tested")
            return CompletableFuture.completedFuture(null)
        }

        when:
        task.tagImage()

        then:
        1 * mockDockerService.tagImage("myapp:latest", ["myapp:tested", "registry.example.com/myapp:tested"]) >>
            CompletableFuture.completedFuture(null)
    }
}
