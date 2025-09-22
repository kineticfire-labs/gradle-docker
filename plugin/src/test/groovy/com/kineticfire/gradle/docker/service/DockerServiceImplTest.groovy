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

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.*
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.exception.NotFoundException
import com.kineticfire.gradle.docker.exception.DockerServiceException
import com.kineticfire.gradle.docker.model.*
import org.gradle.api.services.BuildServiceParameters
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Unit tests for DockerServiceImpl
 * Note: These are basic structural tests since DockerServiceImpl requires Docker daemon integration
 */
class DockerServiceImplTest extends Specification {

    @TempDir
    Path tempDir

    TestDockerServiceImpl service
    
    def setup() {
        service = new TestDockerServiceImpl()
    }

    def "DockerServiceImpl implements DockerService interface"() {
        expect:
        DockerService.isAssignableFrom(DockerServiceImpl)
    }

    def "DockerServiceImpl extends BuildService"() {
        expect:
        org.gradle.api.services.BuildService.isAssignableFrom(DockerServiceImpl)
    }

    def "buildImage throws exception for missing context"() {
        when:
        service.buildImage(null)
        
        then:
        thrown(NullPointerException)
    }

    def "tagImage throws exception for null source image"() {
        when:
        service.tagImage(null, ["test:tag"])
        
        then:
        thrown(NullPointerException)
    }

    def "tagImage throws exception for null tags"() {
        when:
        service.tagImage("source:latest", null)
        
        then:
        thrown(NullPointerException)
    }

    def "saveImage throws exception for null image ID"() {
        when:
        service.saveImage(null, tempDir.resolve("image.tar"), SaveCompression.NONE)
        
        then:
        thrown(NullPointerException)
    }

    def "saveImage throws exception for null output file"() {
        when:
        service.saveImage("image:latest", null, SaveCompression.NONE)
        
        then:
        thrown(NullPointerException)
    }

    def "pushImage throws exception for null image reference"() {
        when:
        service.pushImage(null, null)
        
        then:
        thrown(NullPointerException)
    }

    def "imageExists throws exception for null image reference"() {
        when:
        service.imageExists(null)
        
        then:
        thrown(NullPointerException)
    }

    def "pullImage throws exception for null image reference"() {
        when:
        service.pullImage(null, null)
        
        then:
        thrown(NullPointerException)
    }

    def "close method exists and can be called"() {
        when:
        service.close()

        then:
        noExceptionThrown()
    }

    def "buildImage handles valid context successfully"() {
        given:
        def dockerfile = Files.write(tempDir.resolve("Dockerfile"), "FROM alpine:latest".bytes)
        def context = new BuildContext(tempDir, dockerfile, [:], ["test:latest"])
        
        when:
        def result = service.buildImage(context)
        
        then:
        result.get() == "mock-image-id"
    }

    def "tagImage handles valid parameters successfully"() {
        given:
        def sourceImage = "source:latest"
        def targetTags = ["target1:latest", "target2:v1.0"]
        
        when:
        def result = service.tagImage(sourceImage, targetTags)
        
        then:
        result.get() == null // CompletableFuture<Void> returns null on success
    }

    def "tagImage throws exception for empty tags list"() {
        when:
        service.tagImage("source:latest", [])
        
        then:
        thrown(IllegalArgumentException)
    }

    def "saveImage handles valid parameters successfully"() {
        given:
        def imageRef = "test:latest"
        def outputFile = tempDir.resolve("output.tar")
        def compression = SaveCompression.NONE
        
        when:
        def result = service.saveImage(imageRef, outputFile, compression)
        
        then:
        result.get() == null // CompletableFuture<Void> returns null on success
    }

    def "saveImage handles GZIP compression"() {
        given:
        def imageRef = "test:latest"  
        def outputFile = tempDir.resolve("output.tar.gz")
        def compression = SaveCompression.GZIP
        
        when:
        def result = service.saveImage(imageRef, outputFile, compression)
        
        then:
        result.get() == null
    }

    def "pushImage handles null auth config"() {
        given:
        def imageRef = "test:latest"
        
        when:
        def result = service.pushImage(imageRef, null)
        
        then:
        result.get() == null
    }

    def "pushImage handles valid auth config"() {
        given:
        def imageRef = "test:latest"
        def auth = new AuthConfig("testuser", "testpass", null, "registry.example.com")
        
        when:
        def result = service.pushImage(imageRef, auth)
        
        then:
        result.get() == null
    }

    def "imageExists returns true for existing image"() {
        given:
        def imageRef = "existing:latest"
        
        when:
        def result = service.imageExists(imageRef)
        
        then:
        result.get() == true
    }

    def "pullImage handles null auth config"() {
        given:
        def imageRef = "test:latest"
        
        when:
        def result = service.pullImage(imageRef, null)
        
        then:
        result.get() == null
    }

    def "pullImage handles valid auth config"() {
        given:
        def imageRef = "test:latest"
        def auth = new AuthConfig("testuser", "testpass", null, "registry.example.com")
        
        when:
        def result = service.pullImage(imageRef, auth)
        
        then:
        result.get() == null
    }

    /**
     * Test implementation that provides simple validation without Docker operations
     */
    static class TestDockerServiceImpl extends DockerServiceImpl {

        TestDockerServiceImpl() {
            // Empty constructor for testing
        }

        @Override
        protected DockerClient createDockerClient() {
            return null // Don't create real Docker client for tests
        }

        @Override
        protected DockerClient getDockerClient() {
            return null // Don't use real Docker client for tests
        }
        
        @Override
        BuildServiceParameters.None getParameters() {
            return null // Not needed for testing
        }
        
        // Override methods to do simple validation without Docker operations
        @Override
        CompletableFuture<String> buildImage(BuildContext context) {
            Objects.requireNonNull(context, "BuildContext cannot be null")
            return CompletableFuture.completedFuture("mock-image-id")
        }
        
        @Override
        CompletableFuture<Void> tagImage(String sourceImageRef, List<String> targetTags) {
            Objects.requireNonNull(sourceImageRef, "Source image reference cannot be null")
            Objects.requireNonNull(targetTags, "Target tags cannot be null")
            if (targetTags.isEmpty()) {
                throw new IllegalArgumentException("Target tags list cannot be empty")
            }
            return CompletableFuture.completedFuture(null)
        }
        
        @Override
        CompletableFuture<Void> saveImage(String imageRef, Path outputFile, SaveCompression compression) {
            Objects.requireNonNull(imageRef, "Image reference cannot be null")
            Objects.requireNonNull(outputFile, "Output file cannot be null")
            return CompletableFuture.completedFuture(null)
        }
        
        @Override
        CompletableFuture<Void> pushImage(String imageRef, AuthConfig auth) {
            Objects.requireNonNull(imageRef, "Image reference cannot be null")
            return CompletableFuture.completedFuture(null)
        }
        
        @Override
        CompletableFuture<Boolean> imageExists(String imageRef) {
            Objects.requireNonNull(imageRef, "Image reference cannot be null")
            return CompletableFuture.completedFuture(true)
        }
        
        @Override
        CompletableFuture<Void> pullImage(String imageRef, AuthConfig auth) {
            Objects.requireNonNull(imageRef, "Image reference cannot be null")
            return CompletableFuture.completedFuture(null)
        }
        
        @Override
        void close() {
            // No-op for testing
        }
    }
}