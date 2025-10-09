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

package com.kineticfire.gradle.docker.service

import com.github.dockerjava.api.command.*
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.BuildResponseItem
import com.kineticfire.gradle.docker.base.DockerServiceTestBase
import com.kineticfire.gradle.docker.exception.DockerServiceException
import com.kineticfire.gradle.docker.mocks.DockerTestDataFactory
import com.kineticfire.gradle.docker.model.*
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ExecutionException

/**
 * Comprehensive unit tests for DockerServiceImpl
 * NOTE: Temporarily disabled due to Spock CannotCreateMockException with Docker Java API classes.
 * The issue is that Spock cannot create mocks for certain Docker Java API command classes.
 * Alternative: DockerServiceLabelsTest provides label-specific unit tests without this limitation.
 * TODO: Investigate Spock mock-maker configuration or alternative mocking approach
 */
@spock.lang.Ignore("Spock CannotCreateMockException - see DockerServiceLabelsTest for label tests")
class DockerServiceImplComprehensiveTest extends DockerServiceTestBase {

    @TempDir
    Path tempDir

    def "buildImage with all nomenclature parameters"() {
        given:
        def context = DockerTestDataFactory.createBuildContext(tempDir)
        def buildCmd = Mock(BuildImageCmd)
        def buildCallback = Mock(BuildImageResultCallback)

        mockDockerClient.buildImageCmd() >> buildCmd
        buildCmd.withDockerfile(_) >> buildCmd
        buildCmd.withBaseDirectory(_) >> buildCmd
        buildCmd.withTag(_) >> buildCmd
        buildCmd.withNoCache(_) >> buildCmd
        buildCmd.withBuildArg(_, _) >> buildCmd
        buildCmd.withLabels(_) >> buildCmd
        buildCmd.exec(_) >> buildCallback
        buildCallback.awaitImageId() >> "sha256:12345abcdef"

        when:
        def result = service.buildImage(context).get()

        then:
        result == "sha256:12345abcdef"
        1 * buildCmd.withDockerfile(context.dockerfile.toFile())
        1 * buildCmd.withBaseDirectory(context.contextPath.toFile())
        context.buildArgs.each { key, value ->
            1 * buildCmd.withBuildArg(key, value)
        }
        1 * buildCmd.withLabels(context.labels)
    }
    
    def "buildImage with multiple tags applies additional tags"() {
        given:
        def context = new BuildContext(
            Paths.get("/tmp/context"),
            Paths.get("/tmp/context/Dockerfile"),
            [:],
            ["registry.io/namespace/name:1.0.0", "registry.io/namespace/name:latest"],
            [:]
        )
        def buildCmd = Mock(BuildImageCmd)
        def buildCallback = Mock(BuildImageResultCallback)
        def tagCmd = Mock(TagImageCmd)
        
        mockDockerClient.buildImageCmd() >> buildCmd
        buildCmd.withDockerfile(_) >> buildCmd
        buildCmd.withBaseDirectory(_) >> buildCmd
        buildCmd.withTag("registry.io/namespace/name:1.0.0") >> buildCmd
        buildCmd.withNoCache(_) >> buildCmd
        buildCmd.exec(_) >> buildCallback
        buildCallback.awaitImageId() >> "sha256:12345abcdef"
        
        mockDockerClient.tagImageCmd("sha256:12345abcdef", "registry.io/namespace/name", "latest") >> tagCmd
        
        when:
        service.buildImage(context).get()
        
        then:
        1 * tagCmd.exec()
    }
    
    def "buildImage handles DockerException"() {
        given:
        def context = DockerTestDataFactory.createBuildContext()
        def buildCmd = Mock(BuildImageCmd)
        
        mockDockerClient.buildImageCmd() >> buildCmd
        buildCmd.withDockerfile(_) >> buildCmd
        buildCmd.withBaseDirectory(_) >> buildCmd
        buildCmd.withTag(_) >> buildCmd
        buildCmd.withNoCache(_) >> buildCmd
        buildCmd.withBuildArg(_, _) >> buildCmd
        buildCmd.exec(_) >> { throw new DockerException("Build failed", 500) }
        
        when:
        service.buildImage(context).get()
        
        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof DockerServiceException
        ex.cause.errorType == DockerServiceException.ErrorType.BUILD_FAILED
        ex.cause.message.contains("Build failed")
    }

    def "buildImage applies labels when provided"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)
        def labels = [
            "org.opencontainers.image.version": "1.0.0",
            "maintainer": "team@example.com",
            "custom.label": "value"
        ]
        def context = new BuildContext(
            tempDir,
            dockerfile,
            [:],  // no build args
            ["test:latest"],
            labels
        )
        def buildCmd = Mock(BuildImageCmd)
        def buildCallback = Mock(BuildImageResultCallback)

        mockDockerClient.buildImageCmd() >> buildCmd
        buildCmd.withDockerfile(_) >> buildCmd
        buildCmd.withBaseDirectory(_) >> buildCmd
        buildCmd.withTag(_) >> buildCmd
        buildCmd.withNoCache(_) >> buildCmd
        buildCmd.withLabels(_) >> buildCmd
        buildCmd.exec(_) >> buildCallback
        buildCallback.awaitImageId() >> "sha256:test123"

        when:
        def result = service.buildImage(context).get()

        then:
        result == "sha256:test123"
        1 * buildCmd.withLabels(labels)
    }

    def "buildImage skips labels when empty map provided"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)
        def context = new BuildContext(
            tempDir,
            dockerfile,
            [:],  // no build args
            ["test:latest"],
            [:]   // EMPTY LABELS
        )
        def buildCmd = Mock(BuildImageCmd)
        def buildCallback = Mock(BuildImageResultCallback)

        mockDockerClient.buildImageCmd() >> buildCmd
        buildCmd.withDockerfile(_) >> buildCmd
        buildCmd.withBaseDirectory(_) >> buildCmd
        buildCmd.withTag(_) >> buildCmd
        buildCmd.withNoCache(_) >> buildCmd
        buildCmd.exec(_) >> buildCallback
        buildCallback.awaitImageId() >> "sha256:test456"

        when:
        def result = service.buildImage(context).get()

        then:
        result == "sha256:test456"
        0 * buildCmd.withLabels(_)  // Should NOT be called for empty labels
    }

    def "tagImage tags all provided tags"() {
        given:
        def sourceImage = "source:latest"
        def tags = ["target1:tag1", "target2:tag2", "registry.io/target3:tag3"]
        def tagCmd = Mock(TagImageCmd)
        
        mockDockerClient.tagImageCmd(_, _, _) >> tagCmd
        
        when:
        service.tagImage(sourceImage, tags).get()
        
        then:
        tags.each { tag ->
            def parts = ImageRefParts.parse(tag)
            1 * mockDockerClient.tagImageCmd(sourceImage, parts.fullRepository, parts.tag) >> tagCmd
        }
        tags.size() * tagCmd.exec()
    }
    
    def "tagImage handles DockerException"() {
        given:
        def tagCmd = Mock(TagImageCmd)
        mockDockerClient.tagImageCmd(_, _, _) >> tagCmd
        tagCmd.exec() >> { throw new DockerException("Tag failed", 404) }
        
        when:
        service.tagImage("source:latest", ["target:tag"]).get()
        
        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof DockerServiceException
        ex.cause.errorType == DockerServiceException.ErrorType.TAG_FAILED
    }
    
    def "saveImage with no compression"() {
        given:
        def imageId = "sha256:12345"
        def outputPath = tempDir.resolve("image.tar")
        def saveCmd = Mock(SaveImageCmd)
        def inputStream = new ByteArrayInputStream("fake image data".bytes)
        
        mockDockerClient.saveImageCmd(imageId) >> saveCmd
        saveCmd.exec() >> inputStream
        
        when:
        service.saveImage(imageId, outputPath, SaveCompression.NONE).get()
        
        then:
        Files.exists(outputPath)
        Files.readAllBytes(outputPath) == "fake image data".bytes
    }
    
    def "saveImage with GZIP compression"() {
        given:
        def imageId = "sha256:12345"
        def outputPath = tempDir.resolve("image.tar.gz")
        def saveCmd = Mock(SaveImageCmd)
        def inputStream = new ByteArrayInputStream("fake image data".bytes)
        
        mockDockerClient.saveImageCmd(imageId) >> saveCmd
        saveCmd.exec() >> inputStream
        
        when:
        service.saveImage(imageId, outputPath, SaveCompression.GZIP).get()
        
        then:
        Files.exists(outputPath)
        // Verify it's a GZIP file by checking magic bytes
        def bytes = Files.readAllBytes(outputPath)
        bytes[0] == (byte) 0x1f && bytes[1] == (byte) 0x8b
    }
    
    def "saveImage with ZIP compression"() {
        given:
        def imageId = "sha256:12345"
        def outputPath = tempDir.resolve("image.zip")
        def saveCmd = Mock(SaveImageCmd)
        def inputStream = new ByteArrayInputStream("fake image data".bytes)
        
        mockDockerClient.saveImageCmd(imageId) >> saveCmd
        saveCmd.exec() >> inputStream
        
        when:
        service.saveImage(imageId, outputPath, SaveCompression.ZIP).get()
        
        then:
        Files.exists(outputPath)
        // Verify it's a ZIP file by checking magic bytes
        def bytes = Files.readAllBytes(outputPath)
        bytes[0] == 0x50 && bytes[1] == 0x4b  // PK magic bytes
    }
    
    def "saveImage with BZIP2 compression"() {
        given:
        def imageId = "sha256:12345"
        def outputPath = tempDir.resolve("image.tar.bz2")
        def saveCmd = Mock(SaveImageCmd)
        def inputStream = new ByteArrayInputStream("fake image data".bytes)
        
        mockDockerClient.saveImageCmd(imageId) >> saveCmd
        saveCmd.exec() >> inputStream
        
        when:
        service.saveImage(imageId, outputPath, SaveCompression.BZIP2).get()
        
        then:
        Files.exists(outputPath)
        // Verify it's a BZIP2 file by checking magic bytes
        def bytes = Files.readAllBytes(outputPath)
        bytes[0] == 0x42 && bytes[1] == 0x5a  // BZ magic bytes
    }
    
    def "saveImage with XZ compression"() {
        given:
        def imageId = "sha256:12345"
        def outputPath = tempDir.resolve("image.tar.xz")
        def saveCmd = Mock(SaveImageCmd)
        def inputStream = new ByteArrayInputStream("fake image data".bytes)
        
        mockDockerClient.saveImageCmd(imageId) >> saveCmd
        saveCmd.exec() >> inputStream
        
        when:
        service.saveImage(imageId, outputPath, SaveCompression.XZ).get()
        
        then:
        Files.exists(outputPath)
        // Verify it's an XZ file by checking magic bytes
        def bytes = Files.readAllBytes(outputPath)
        bytes[0] == (byte) 0xfd && bytes[1] == 0x37  // XZ magic bytes
    }
    
    def "saveImage handles DockerException"() {
        given:
        def saveCmd = Mock(SaveImageCmd)
        mockDockerClient.saveImageCmd(_) >> saveCmd
        saveCmd.exec() >> { throw new DockerException("Save failed", 500) }
        
        when:
        service.saveImage("image:tag", tempDir.resolve("output.tar"), SaveCompression.NONE).get()
        
        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof DockerServiceException
        ex.cause.errorType == DockerServiceException.ErrorType.SAVE_FAILED
    }
    
    def "pushImage with authentication"() {
        given:
        def imageRef = "registry.io/namespace/name:tag"
        def auth = DockerTestDataFactory.createAuthConfig()
        def pushCmd = Mock(PushImageCmd)
        def pushCallback = Mock(PushImageResultCallback)
        
        mockDockerClient.pushImageCmd("registry.io/namespace/name") >> pushCmd
        pushCmd.withTag("tag") >> pushCmd
        pushCmd.withAuthConfig(_) >> pushCmd
        pushCmd.exec(_) >> pushCallback
        pushCallback.awaitCompletion() >> pushCallback
        
        when:
        service.pushImage(imageRef, auth).get()
        
        then:
        1 * pushCmd.withAuthConfig(_)
        1 * pushCallback.awaitCompletion()
    }
    
    def "pushImage without authentication"() {
        given:
        def imageRef = "docker.io/library/alpine:latest"
        def pushCmd = Mock(PushImageCmd)
        def pushCallback = Mock(PushImageResultCallback)
        
        mockDockerClient.pushImageCmd("docker.io/library/alpine") >> pushCmd
        pushCmd.withTag("latest") >> pushCmd
        pushCmd.exec(_) >> pushCallback
        pushCallback.awaitCompletion() >> pushCallback
        
        when:
        service.pushImage(imageRef, null).get()
        
        then:
        0 * pushCmd.withAuthConfig(_)
        1 * pushCallback.awaitCompletion()
    }
    
    def "pushImage handles DockerException"() {
        given:
        def pushCmd = Mock(PushImageCmd)
        mockDockerClient.pushImageCmd(_) >> pushCmd
        pushCmd.withTag(_) >> pushCmd
        pushCmd.exec(_) >> { throw new DockerException("Push failed", 401) }
        
        when:
        service.pushImage("image:tag", null).get()
        
        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof DockerServiceException
        ex.cause.errorType == DockerServiceException.ErrorType.PUSH_FAILED
    }
    
    def "pullImage with authentication"() {
        given:
        def imageRef = "private.registry.io/namespace/name:tag"
        def auth = DockerTestDataFactory.createAuthConfig()
        def pullCmd = Mock(PullImageCmd)
        def pullCallback = Mock(PullImageResultCallback)
        
        mockDockerClient.pullImageCmd("private.registry.io/namespace/name") >> pullCmd
        pullCmd.withTag("tag") >> pullCmd
        pullCmd.withAuthConfig(_) >> pullCmd
        pullCmd.exec(_) >> pullCallback
        pullCallback.awaitCompletion() >> pullCallback
        
        when:
        service.pullImage(imageRef, auth).get()
        
        then:
        1 * pullCmd.withAuthConfig(_)
        1 * pullCallback.awaitCompletion()
    }
    
    def "pullImage without authentication"() {
        given:
        def imageRef = "docker.io/library/ubuntu:20.04"
        def pullCmd = Mock(PullImageCmd)
        def pullCallback = Mock(PullImageResultCallback)
        
        mockDockerClient.pullImageCmd("docker.io/library/ubuntu") >> pullCmd
        pullCmd.withTag("20.04") >> pullCmd
        pullCmd.exec(_) >> pullCallback
        pullCallback.awaitCompletion() >> pullCallback
        
        when:
        service.pullImage(imageRef, null).get()
        
        then:
        0 * pullCmd.withAuthConfig(_)
        1 * pullCallback.awaitCompletion()
    }
    
    def "pullImage handles DockerException"() {
        given:
        def pullCmd = Mock(PullImageCmd)
        mockDockerClient.pullImageCmd(_) >> pullCmd
        pullCmd.withTag(_) >> pullCmd
        pullCmd.exec(_) >> { throw new DockerException("Pull failed", 404) }
        
        when:
        service.pullImage("nonexistent:tag", null).get()
        
        then:
        def ex = thrown(ExecutionException)
        ex.cause instanceof DockerServiceException
        ex.cause.errorType == DockerServiceException.ErrorType.PULL_FAILED
    }
    
    def "imageExists returns true when image exists"() {
        given:
        def imageRef = "existing:image"
        def inspectCmd = Mock(InspectImageCmd)
        
        mockDockerClient.inspectImageCmd(imageRef) >> inspectCmd
        inspectCmd.exec() >> Mock(InspectImageResponse)
        
        when:
        def result = service.imageExists(imageRef).get()
        
        then:
        result == true
    }
    
    def "imageExists returns false when image not found"() {
        given:
        def imageRef = "nonexistent:image"
        def inspectCmd = Mock(InspectImageCmd)
        
        mockDockerClient.inspectImageCmd(imageRef) >> inspectCmd
        inspectCmd.exec() >> { throw new NotFoundException("Image not found") }
        
        when:
        def result = service.imageExists(imageRef).get()
        
        then:
        result == false
    }
    
    def "imageExists returns false on other exceptions"() {
        given:
        def imageRef = "error:image"
        def inspectCmd = Mock(InspectImageCmd)
        
        mockDockerClient.inspectImageCmd(imageRef) >> inspectCmd
        inspectCmd.exec() >> { throw new RuntimeException("Some other error") }
        
        when:
        def result = service.imageExists(imageRef).get()
        
        then:
        result == false
    }
    
    def "close shuts down executor and client"() {
        given:
        def mockExecutor = Mock(java.util.concurrent.ExecutorService)
        service = new DockerServiceTestBase.TestDockerServiceImpl(mockDockerClient, mockExecutor)
        
        when:
        service.close()
        
        then:
        1 * mockExecutor.isShutdown() >> false
        1 * mockExecutor.shutdown()
        1 * mockDockerClient.close()
    }
    
    def "close handles already shutdown executor"() {
        given:
        def mockExecutor = Mock(java.util.concurrent.ExecutorService)
        service = new DockerServiceTestBase.TestDockerServiceImpl(mockDockerClient, mockExecutor)
        
        when:
        service.close()
        
        then:
        1 * mockExecutor.isShutdown() >> true
        0 * mockExecutor.shutdown()
        1 * mockDockerClient.close()
    }
}