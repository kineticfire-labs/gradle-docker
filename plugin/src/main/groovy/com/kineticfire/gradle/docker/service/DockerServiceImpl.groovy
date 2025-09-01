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
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
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

/**
 * Docker Java Client implementation of Docker service
 */
abstract class DockerServiceImpl implements BuildService<BuildServiceParameters.None>, DockerService {
    
    private static final Logger logger = Logging.getLogger(DockerServiceImpl)
    
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
        logger.info("DockerService initialized successfully")
    }
    
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
            logger.debug("Docker client connected to: {}", dockerClientConfig.getDockerHost())
            
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
                logger.lifecycle("Building Docker image with context: {}", context.contextPath)
                
                def buildImageResultCallback = new BuildImageResultCallback() {
                    @Override
                    void onNext(BuildResponseItem item) {
                        // Handle stream output (build progress)
                        if (item.stream) {
                            def message = item.stream.trim()
                            if (message) {
                                logger.lifecycle("Docker build: {}", message)
                            }
                        }
                        
                        // Handle errors
                        if (item.errorDetail) {
                            logger.error("Docker build error: {}", item.errorDetail.message)
                        }
                        if (item.error) {
                            logger.error("Docker build error: {}", item.error)
                        }
                        
                        // Pass to parent to handle image ID capture
                        super.onNext(item)
                    }
                    
                    @Override
                    void onError(Throwable throwable) {
                        logger.error("Docker build callback error: {}", throwable.message, throwable)
                        super.onError(throwable)
                    }
                    
                    @Override
                    void onComplete() {
                        logger.info("Docker build callback completed")
                        super.onComplete()
                    }
                }
                
                def buildCmd = dockerClient.buildImageCmd()
                    .withDockerfile(context.dockerfile.toFile())
                    .withBaseDirectory(context.contextPath.toFile())
                    .withTags(new HashSet<>(context.tags))
                    .withNoCache(false)
                    
                // Add build arguments individually
                context.buildArgs.each { key, value ->
                    buildCmd.withBuildArg(key, value)
                }
                
                def result = buildCmd.exec(buildImageResultCallback)
                logger.info("Build command executed, awaiting image ID...")
                
                def imageId = result.awaitImageId()
                logger.info("Received image ID: {}", imageId)
                    
                logger.lifecycle("Successfully built Docker image: {} (ID: {})", context.tags.first(), imageId)
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
                logger.info("Tagging image {} with {} tags", sourceImage, tags.size())
                
                tags.each { tag ->
                    def parts = ImageRefParts.parse(tag)
                    dockerClient.tagImageCmd(sourceImage, parts.fullRepository, parts.tag).exec()
                    logger.debug("Tagged {} as {}", sourceImage, tag)
                }
                
                logger.info("Successfully tagged image {} with all tags", sourceImage)
                
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
    CompletableFuture<Void> saveImage(String imageId, Path outputFile, CompressionType compression) {
        return CompletableFuture.runAsync({
            try {
                logger.info("Saving image {} to {} with compression: {}", imageId, outputFile, compression)
                
                // Create parent directories
                Files.createDirectories(outputFile.parent)
                
                def inputStream = dockerClient.saveImageCmd(imageId).exec()
                
                if (compression == CompressionType.GZIP) {
                    outputFile.withOutputStream { fileOut ->
                        new GZIPOutputStream(fileOut).withStream { gzipOut ->
                            gzipOut << inputStream
                        }
                    }
                } else {
                    outputFile.withOutputStream { fileOut ->
                        fileOut << inputStream
                    }
                }
                
                inputStream.close()
                
                logger.info("Successfully saved image {} to {}", imageId, outputFile)
                
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
                logger.info("Pushing image {} to registry", imageRef)
                
                def pushCallback = new ResultCallback.Adapter<PushResponseItem>() {
                    @Override
                    void onNext(PushResponseItem item) {
                        if (item.status) {
                            logger.debug("Push status: {}", item.status)
                        }
                    }
                }
                def pushCmd = dockerClient.pushImageCmd(parts.fullRepository)
                    .withTag(parts.tag)
                    
                if (auth?.hasCredentials()) {
                    pushCmd.withAuthConfig(auth.toDockerJavaAuthConfig())
                }
                
                pushCmd.exec(pushCallback).awaitCompletion()
                
                logger.info("Successfully pushed image {}", imageRef)
                
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
                logger.debug("Error checking if image exists: {}", e.message)
                return false
            }
        }, executorService)
    }
    
    @Override
    CompletableFuture<Void> pullImage(String imageRef, AuthConfig auth) {
        return CompletableFuture.runAsync({
            try {
                def parts = ImageRefParts.parse(imageRef)
                logger.info("Pulling image {}", imageRef)
                
                def pullCallback = new ResultCallback.Adapter<PullResponseItem>() {
                    @Override
                    void onNext(PullResponseItem item) {
                        if (item.status) {
                            logger.debug("Pull status: {}", item.status)
                        }
                    }
                }
                def pullCmd = dockerClient.pullImageCmd(parts.fullRepository)
                    .withTag(parts.tag)
                    
                if (auth?.hasCredentials()) {
                    pullCmd.withAuthConfig(auth.toDockerJavaAuthConfig())
                }
                
                pullCmd.exec(pullCallback).awaitCompletion()
                
                logger.info("Successfully pulled image {}", imageRef)
                
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
                logger.debug("Docker service executor shutdown")
            }
            
            if (dockerClient) {
                dockerClient.close()
                logger.debug("Docker client closed")
            }
        } catch (Exception e) {
            logger.warn("Error closing Docker service: {}", e.message)
        }
    }
}