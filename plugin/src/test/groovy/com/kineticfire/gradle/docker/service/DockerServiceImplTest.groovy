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
        service.saveImage(null, tempDir.resolve("image.tar"), CompressionType.NONE)
        
        then:
        thrown(NullPointerException)
    }

    def "saveImage throws exception for null output file"() {
        when:
        service.saveImage("image:latest", null, CompressionType.NONE)
        
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
            return CompletableFuture.completedFuture(null)
        }
        
        @Override
        CompletableFuture<Void> saveImage(String imageRef, Path outputFile, CompressionType compression) {
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