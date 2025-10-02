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
import com.github.dockerjava.api.command.BuildImageResultCallback
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.BuildResponseItem
import com.github.dockerjava.api.model.PushResponseItem
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.kineticfire.gradle.docker.exception.DockerServiceException
import com.kineticfire.gradle.docker.model.*

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

/**
 * Docker Java Client implementation of Docker service
 */
abstract class DockerServiceImpl implements BuildService<BuildServiceParameters.None>, DockerService {
    
    // private static final Logger logger = Logging.getLogger(DockerServiceImpl)
    // Removed logger to fix Gradle 9 configuration cache compatibility issues
    
    protected final DockerClient dockerClient
    protected final ExecutorService executorService
    
    @Inject
    DockerServiceImpl() {
        this.dockerClient = createDockerClient()
        this.executorService = Executors.newCachedThreadPool { runnable ->
            Thread thread = new Thread(runnable, "docker-service-${System.currentTimeMillis()}")
            thread.daemon = true
            return thread
        }
        println "DockerService initialized successfully"
    }
    
    @org.gradle.api.tasks.Internal
    protected DockerClient getDockerClient() {
        return dockerClient
    }
    
    protected DockerClient createDockerClient() {
        try {
            def dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .build()
                
            def httpTransport = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerClientConfig.getDockerHost())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build()
                
            def client = DockerClientImpl.getInstance(dockerClientConfig, httpTransport)
                
            // Test connection
            client.pingCmd().exec()
            println "Docker client connected to: ${dockerClientConfig.getDockerHost()}"
            
            return client
        } catch (Exception e) {
            throw new DockerServiceException(
                DockerServiceException.ErrorType.DAEMON_UNAVAILABLE,
                "Failed to connect to Docker daemon: ${e.message}",
                e
            )
        }
    }
    
    @Override
    CompletableFuture<String> buildImage(BuildContext context) {
        return CompletableFuture.supplyAsync({
            try {
                println "Building Docker image with context: ${context.contextPath}"
                println "Requested tags: ${context.tags}"
                
                def buildImageResultCallback = new BuildImageResultCallback() {
                    @Override
                    void onNext(BuildResponseItem item) {
                        // Handle stream output (build progress)
                        if (item.stream) {
                            def message = item.stream.trim()
                            if (message) {
                                println "Docker build: ${message}"
                            }
                        }
                        
                        // Handle errors
                        if (item.errorDetail) {
                            System.err.println("Docker build error: ${item.errorDetail.message}")
                        }
                        if (item.error) {
                            System.err.println("Docker build error: ${item.error}")
                        }
                        
                        // Pass to parent to handle image ID capture
                        super.onNext(item)
                    }
                    
                    @Override
                    void onError(Throwable throwable) {
                        System.err.println("Docker build callback error: ${throwable.message}")
                        super.onError(throwable)
                    }
                    
                    @Override
                    void onComplete() {
                        println "Docker build callback completed"
                        super.onComplete()
                    }
                }
                
                // Build with the primary tag first (prefer non-latest tags, fallback to first tag)
                def primaryTag = context.tags.find { !it.endsWith(':latest') } ?: context.tags.first()
                
                // Docker Java client has issues with subdirectory dockerfiles
                // When dockerfile is in a subdirectory, create a temporary copy at the root
                def dockerfileFile = context.dockerfile.toFile()
                def contextFile = context.contextPath.toFile()

                // Check if dockerfile is within the context directory
                if (!dockerfileFile.absolutePath.startsWith(contextFile.absolutePath)) {
                    throw new IllegalArgumentException(
                        "Dockerfile must be within the build context directory. " +
                        "Dockerfile: ${dockerfileFile.absolutePath}, Context: ${contextFile.absolutePath}"
                    )
                }

                // Calculate relative path for Docker API
                def relativePath = contextFile.toPath().relativize(dockerfileFile.toPath()).toString()

                def actualDockerfile = dockerfileFile

                // If dockerfile is in a subdirectory, create a temporary copy at the context root
                if (relativePath.contains("/") || relativePath.contains("\\")) {
                    def tempDockerfileName = "Dockerfile.tmp.${System.currentTimeMillis()}"
                    def tempDockerfile = new File(contextFile, tempDockerfileName)
                    tempDockerfile.text = dockerfileFile.text
                    actualDockerfile = tempDockerfile

                    // Temporary workaround for Docker Java client subdirectory dockerfile issue

                    // Schedule cleanup
                    Runtime.runtime.addShutdownHook {
                        if (tempDockerfile.exists()) {
                            tempDockerfile.delete()
                        }
                    }
                }

                def buildCmd = dockerClient.buildImageCmd()
                    .withDockerfile(actualDockerfile)
                    .withBaseDirectory(contextFile)
                    .withTag(primaryTag)  // Use single primary tag for build
                    .withNoCache(false)
                    
                // Add build arguments individually
                context.buildArgs.each { key, value ->
                    buildCmd.withBuildArg(key, value)
                }
                
                def result = buildCmd.exec(buildImageResultCallback)
                println "Build command executed, awaiting image ID..."
                
                def imageId = result.awaitImageId()
                println "Received image ID: ${imageId}"
                
                // Apply additional tags if there are more than one
                if (context.tags.size() > 1) {
                    def additionalTags = context.tags.findAll { it != primaryTag }
                    println "Applying additional tags: ${additionalTags}"
                    additionalTags.each { tag ->
                        println "Tagging ${imageId} as ${tag}"
                        dockerClient.tagImageCmd(imageId, tag.split(':')[0], tag.split(':')[1]).exec()
                    }
                }
                
                println "Successfully built Docker image with all tags: ${context.tags} (ID: ${imageId})"
                return imageId
                
            } catch (DockerException e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.BUILD_FAILED,
                    "Build failed: ${e.message}",
                    "Check your Dockerfile syntax and build context",
                    e
                )
            } catch (Exception e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.BUILD_FAILED,
                    "Build failed: ${e.message}",
                    e
                )
            }
        }, executorService)
    }
    
    @Override
    CompletableFuture<Void> tagImage(String sourceImage, List<String> tags) {
        return CompletableFuture.runAsync({
            try {
                println "Tagging image ${sourceImage} with ${tags.size()} tags"
                
                tags.each { tag ->
                    def parts = ImageRefParts.parse(tag)

                    // Build full repository path including registry for Docker Java client
                    def fullRepoPath = parts.isRegistryQualified() ?
                        "${parts.registry}/${parts.fullRepository}" :
                        parts.fullRepository

                    dockerClient.tagImageCmd(sourceImage, fullRepoPath, parts.tag).exec()
                    // Debug: Tagged ${sourceImage} as ${tag}
                }
                
                println "Successfully tagged image ${sourceImage} with all tags"
                
            } catch (DockerException e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.TAG_FAILED,
                    "Tag operation failed: ${e.message}",
                    "Verify the source image exists and tag format is correct",
                    e
                )
            } catch (Exception e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.TAG_FAILED,
                    "Tag operation failed: ${e.message}",
                    e
                )
            }
        }, executorService)
    }
    
    @Override
    CompletableFuture<Void> saveImage(String imageId, Path outputFile, SaveCompression compression) {
        return CompletableFuture.runAsync({
            try {
                println "Saving image ${imageId} to ${outputFile} with compression: ${compression}"
                
                // Create parent directories
                Files.createDirectories(outputFile.parent)
                
                def inputStream = dockerClient.saveImageCmd(imageId).exec()
                
                outputFile.withOutputStream { fileOut ->
                    switch (compression) {
                        case SaveCompression.GZIP:
                            new GZIPOutputStream(fileOut).withStream { gzipOut ->
                                gzipOut << inputStream
                            }
                            break
                        case SaveCompression.BZIP2:
                            new BZip2CompressorOutputStream(fileOut).withStream { bz2Out ->
                                bz2Out << inputStream
                            }
                            break
                        case SaveCompression.XZ:
                            new XZCompressorOutputStream(fileOut).withStream { xzOut ->
                                xzOut << inputStream
                            }
                            break
                        case SaveCompression.ZIP:
                            new ZipOutputStream(fileOut).withStream { zipOut ->
                                zipOut.putNextEntry(new ZipEntry("image.tar"))
                                zipOut << inputStream
                                zipOut.closeEntry()
                            }
                            break
                        case SaveCompression.NONE:
                        default:
                            fileOut << inputStream
                            break
                    }
                }
                
                inputStream.close()
                
                println "Successfully saved image ${imageId} to ${outputFile}"
                
            } catch (DockerException e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.SAVE_FAILED,
                    "Save failed: ${e.message}",
                    "Check output directory permissions and disk space",
                    e
                )
            } catch (Exception e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.SAVE_FAILED,
                    "Save failed: ${e.message}",
                    e
                )
            }
        }, executorService)
    }
    
    @Override
    CompletableFuture<Void> pushImage(String imageRef, AuthConfig auth) {
        return CompletableFuture.runAsync({
            try {
                def parts = ImageRefParts.parse(imageRef)
                println "Pushing image ${imageRef} to registry"
                
                def pushCallback = new ResultCallback.Adapter<PushResponseItem>() {
                    @Override
                    void onNext(PushResponseItem item) {
                        // Only log errors, not all status messages to avoid spam
                        if (item.errorDetail) {
                            throw new RuntimeException("Push failed: ${item.errorDetail.message}")
                        }
                    }
                }
                def repositoryName = parts.isRegistryQualified() ? 
                    "${parts.registry}/${parts.fullRepository}" : 
                    parts.fullRepository
                def pushCmd = dockerClient.pushImageCmd(repositoryName)
                    .withTag(parts.tag)
                    
                if (auth?.hasCredentials()) {
                    pushCmd.withAuthConfig(auth.toDockerJavaAuthConfig())
                }
                
                pushCmd.exec(pushCallback).awaitCompletion()
                
                println "Successfully pushed image ${imageRef}"
                
            } catch (DockerException e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.PUSH_FAILED,
                    "Push failed: ${e.message}",
                    "Check registry accessibility and credentials",
                    e
                )
            } catch (Exception e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.PUSH_FAILED,
                    "Push failed: ${e.message}",
                    e
                )
            }
        }, executorService)
    }
    
    @Override
    CompletableFuture<Boolean> imageExists(String imageRef) {
        return CompletableFuture.supplyAsync({
            try {
                dockerClient.inspectImageCmd(imageRef).exec()
                return true
            } catch (NotFoundException e) {
                return false
            } catch (Exception e) {
                // Debug: Error checking if image exists: ${e.message}
                return false
            }
        }, executorService)
    }
    
    @Override
    CompletableFuture<Void> pullImage(String imageRef, AuthConfig auth) {
        return CompletableFuture.runAsync({
            try {
                def parts = ImageRefParts.parse(imageRef)
                println "Pulling image ${imageRef}"
                
                def pullCallback = new ResultCallback.Adapter<PullResponseItem>() {
                    @Override
                    void onNext(PullResponseItem item) {
                        if (item.status) {
                            // Debug: Pull status: ${item.status}
                        }
                    }
                }
                def pullCmd = dockerClient.pullImageCmd(parts.fullRepository)
                    .withTag(parts.tag)
                    
                if (auth?.hasCredentials()) {
                    pullCmd.withAuthConfig(auth.toDockerJavaAuthConfig())
                }
                
                pullCmd.exec(pullCallback).awaitCompletion()
                
                println "Successfully pulled image ${imageRef}"
                
            } catch (DockerException e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.PULL_FAILED,
                    "Pull failed: ${e.message}",
                    "Check image reference and registry accessibility",
                    e
                )
            } catch (Exception e) {
                throw new DockerServiceException(
                    DockerServiceException.ErrorType.PULL_FAILED,
                    "Pull failed: ${e.message}",
                    e
                )
            }
        }, executorService)
    }
    
    @Override
    void close() {
        try {
            if (executorService && !executorService.isShutdown()) {
                executorService.shutdown()
                // Debug: Docker service executor shutdown
            }
            
            if (dockerClient) {
                dockerClient.close()
                // Debug: Docker client closed
            }
        } catch (Exception e) {
            System.err.println("Error closing Docker service: ${e.message}")
        }
    }
}