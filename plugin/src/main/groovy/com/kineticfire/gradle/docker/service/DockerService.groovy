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

import com.kineticfire.gradle.docker.model.*
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Service interface for Docker daemon operations
 */
interface DockerService {
    
    /**
     * Build a Docker image from the provided build context
     * @param context Build configuration including dockerfile, context path, build args, and tags
     * @return CompletableFuture with the built image ID
     * @throws DockerServiceException if build fails
     */
    CompletableFuture<String> buildImage(BuildContext context)
    
    /**
     * Tag an existing image with additional tags
     * @param sourceImage Source image ID or reference
     * @param tags List of new tags to apply
     * @return CompletableFuture that completes when tagging is done
     * @throws DockerServiceException if tagging fails
     */
    CompletableFuture<Void> tagImage(String sourceImage, List<String> tags)
    
    /**
     * Save an image to a tar file with optional compression
     * @param imageId Image ID to save
     * @param outputFile Output file path
     * @param compression Compression type (NONE, GZIP)
     * @return CompletableFuture that completes when save is done
     * @throws DockerServiceException if save fails
     */
    CompletableFuture<Void> saveImage(String imageId, Path outputFile, SaveCompression compression)
    
    /**
     * Push an image to a registry
     * @param imageRef Full image reference (repository:tag)
     * @param auth Authentication configuration (can be null)
     * @return CompletableFuture that completes when push is done
     * @throws DockerServiceException if push fails
     */
    CompletableFuture<Void> pushImage(String imageRef, AuthConfig auth)
    
    /**
     * Check if an image exists locally
     * @param imageRef Image reference to check
     * @return CompletableFuture with boolean indicating existence
     */
    CompletableFuture<Boolean> imageExists(String imageRef)
    
    /**
     * Pull an image from a registry
     * @param imageRef Image reference to pull
     * @param auth Authentication configuration (can be null)
     * @return CompletableFuture that completes when pull is done
     * @throws DockerServiceException if pull fails
     */
    CompletableFuture<Void> pullImage(String imageRef, AuthConfig auth)
    
    /**
     * Clean up resources and close connections
     */
    void close()
}