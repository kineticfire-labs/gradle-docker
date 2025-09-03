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

import com.kineticfire.gradle.docker.model.AuthConfig
import com.kineticfire.gradle.docker.model.BuildContext
import com.kineticfire.gradle.docker.model.CompressionType
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Unit tests for DockerService interface
 */
class DockerServiceTest extends Specification {

    @TempDir
    Path tempDir

    TestDockerService service = new TestDockerService()

    def "DockerService interface can be implemented"() {
        expect:
        service instanceof DockerService
    }

    def "buildImage method signature is correct"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)
        def context = new BuildContext(tempDir, dockerfile, [:], ["test:latest"])

        when:
        def result = service.buildImage(context)

        then:
        result instanceof CompletableFuture<String>
        result.get() == "mock-image-id"
    }

    def "tagImage method signature is correct"() {
        given:
        def sourceImage = "source:latest"
        def targetTags = ["target1:latest", "target2:v1.0"]

        when:
        def result = service.tagImage(sourceImage, targetTags)

        then:
        result instanceof CompletableFuture<Void>
        result.get() == null
    }

    def "saveImage method signature is correct"() {
        given:
        def imageRef = "test:latest"
        def outputFile = tempDir.resolve("output.tar")
        def compression = CompressionType.NONE

        when:
        def result = service.saveImage(imageRef, outputFile, compression)

        then:
        result instanceof CompletableFuture<Void>
        result.get() == null
    }

    def "pushImage method signature is correct"() {
        given:
        def imageRef = "test:latest"
        def auth = new AuthConfig("user", "pass", null, "registry.example.com")

        when:
        def result = service.pushImage(imageRef, auth)

        then:
        result instanceof CompletableFuture<Void>
        result.get() == null
    }

    def "imageExists method signature is correct"() {
        given:
        def imageRef = "test:latest"

        when:
        def result = service.imageExists(imageRef)

        then:
        result instanceof CompletableFuture<Boolean>
        result.get() == true
    }

    def "pullImage method signature is correct"() {
        given:
        def imageRef = "test:latest"
        def auth = new AuthConfig("user", "pass", null, "registry.example.com")

        when:
        def result = service.pullImage(imageRef, auth)

        then:
        result instanceof CompletableFuture<Void>
        result.get() == null
    }

    def "close method exists"() {
        when:
        service.close()

        then:
        noExceptionThrown()
    }

    /**
     * Test implementation of DockerService for interface testing
     */
    static class TestDockerService implements DockerService {

        @Override
        CompletableFuture<String> buildImage(BuildContext context) {
            return CompletableFuture.completedFuture("mock-image-id")
        }

        @Override
        CompletableFuture<Void> tagImage(String sourceImage, List<String> tags) {
            return CompletableFuture.completedFuture(null)
        }

        @Override
        CompletableFuture<Void> saveImage(String imageId, Path outputFile, CompressionType compression) {
            return CompletableFuture.completedFuture(null)
        }

        @Override
        CompletableFuture<Void> pushImage(String imageRef, AuthConfig auth) {
            return CompletableFuture.completedFuture(null)
        }

        @Override
        CompletableFuture<Boolean> imageExists(String imageRef) {
            return CompletableFuture.completedFuture(true)
        }

        @Override
        CompletableFuture<Void> pullImage(String imageRef, AuthConfig auth) {
            return CompletableFuture.completedFuture(null)
        }

        @Override
        void close() {
            // No-op for testing
        }
    }
}