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

package com.kineticfire.gradle.docker.model

import java.util.regex.Pattern

/**
 * Parses and represents the components of a Docker image reference
 */
class ImageRefParts {
    final String repository
    final String tag
    final String registry
    
    // Docker image reference pattern: [registry/]repository[:tag]
    private static final Pattern IMAGE_REF_PATTERN = ~/^(?:((?:[^\/]+\.[^\/]+(?::\d+)?|[^\/]+:\d+))\/)?([^:]+)(?::(.+))?$/
    
    ImageRefParts(String repository, String tag, String registry = null) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null")
        this.tag = tag ?: "latest"
        this.registry = registry
    }
    
    /**
     * Parse an image reference string into its components
     */
    static ImageRefParts parse(String imageRef) {
        if (!imageRef) {
            throw new IllegalArgumentException("Image reference cannot be null or empty")
        }
        
        def matcher = IMAGE_REF_PATTERN.matcher(imageRef)
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid image reference format: ${imageRef}")
        }
        
        def registry = matcher.group(1)
        def repository = matcher.group(2)
        def tag = matcher.group(3) ?: "latest"
        
        return new ImageRefParts(repository, tag, registry)
    }
    
    /**
     * Get the full image reference
     */
    String getFullReference() {
        def ref = registry ? "${registry}/${repository}" : repository
        return "${ref}:${tag}"
    }
    
    /**
     * Get the repository with registry prefix if present
     */
    String getFullRepository() {
        return registry ? "${registry}/${repository}" : repository
    }
    
    /**
     * Check if this is a registry-qualified reference
     */
    boolean isRegistryQualified() {
        return registry != null
    }
    
    /**
     * Check if this appears to be a Docker Hub reference
     */
    boolean isDockerHub() {
        return !isRegistryQualified() || registry?.startsWith("docker.io")
    }
    
    @Override
    String toString() {
        return getFullReference()
    }
    
    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) return true
        if (!(obj instanceof ImageRefParts)) return false
        
        ImageRefParts other = (ImageRefParts) obj
        return Objects.equals(repository, other.repository) &&
               Objects.equals(tag, other.tag) &&
               Objects.equals(registry, other.registry)
    }
    
    @Override
    int hashCode() {
        return Objects.hash(repository, tag, registry)
    }
}