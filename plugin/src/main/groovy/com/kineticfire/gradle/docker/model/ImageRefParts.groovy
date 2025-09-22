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

import java.util.Objects

/**
 * Simple Docker image reference parser
 */
class ImageRefParts {
    final String registry
    final String namespace
    final String repository
    final String tag

    ImageRefParts(String registry, String namespace, String repository, String tag) {
        // Handle both null and empty string cases for backward compatibility
        this.registry = registry  // Keep as-is for null/empty preservation
        this.namespace = namespace  // Keep as-is for null/empty preservation
        this.repository = repository ?: "unknown"
        this.tag = tag ?: "latest"
    }

    static ImageRefParts parse(String imageRef) {
        if (!imageRef) {
            // Return default for empty input instead of throwing
            return new ImageRefParts("", "", "unknown", "latest")
        }

        // Handle edge case where input is just ":"
        if (imageRef == ":") {
            return new ImageRefParts("", "", "unknown", "latest")
        }

        // Smart parsing to handle registry ports correctly
        // Find the last colon that's followed by a tag (not a port)
        def lastColonIndex = imageRef.lastIndexOf(':')
        def tag = "latest"
        def fullPath = imageRef

        if (lastColonIndex > 0) {
            def potentialTag = imageRef.substring(lastColonIndex + 1)
            // If what follows the colon doesn't contain '/', it's likely a tag
            if (!potentialTag.contains('/')) {
                tag = potentialTag
                fullPath = imageRef.substring(0, lastColonIndex)
            }
        }

        // Handle edge case where fullPath is empty (like ":tag")
        if (fullPath.isEmpty()) {
            return new ImageRefParts("", "", "unknown", tag)
        }

        def pathParts = fullPath.split('/')
        def parsedRegistry = ""
        def namespace = ""
        def repository = ""

        if (pathParts.length == 1) {
            // Just repository name: "nginx"
            repository = pathParts[0]
        } else if (pathParts.length == 2) {
            // Check if first part looks like a registry (contains . or :)
            if (pathParts[0].contains('.') || pathParts[0].contains(':')) {
                // registry/repository: "localhost:5000/myapp" or "docker.io/nginx"
                parsedRegistry = pathParts[0]
                repository = pathParts[1]
            } else {
                // namespace/repository: "library/nginx"
                namespace = pathParts[0]
                repository = pathParts[1]
            }
        } else if (pathParts.length >= 3) {
            // First check if first part looks like a registry
            if (pathParts[0].contains('.') || pathParts[0].contains(':')) {
                // registry/namespace.../repository: "docker.io/library/nginx" or "localhost:5000/very/deep/path"
                parsedRegistry = pathParts[0]
                if (pathParts.length == 3) {
                    namespace = pathParts[1]
                    repository = pathParts[2]
                } else {
                    // Handle multi-level namespaces like "very/deep" in "localhost:5000/very/deep/path"
                    namespace = pathParts[1..-2].join('/')
                    repository = pathParts[-1]
                }
            } else {
                // No registry, just multi-level namespace/repository like "company/project/service"
                namespace = pathParts[0..-2].join('/')
                repository = pathParts[-1]
            }
        }

        return new ImageRefParts(parsedRegistry, namespace, repository, tag)
    }

    /**
     * Get the full image reference
     */
    String getFullReference() {
        def ref = getFullRepository()
        return "${ref}:${tag}"
    }

    /**
     * Get the repository with namespace prefix if present (excludes registry)
     */
    String getFullRepository() {
        def parts = []
        if (namespace && !namespace.isEmpty()) {
            parts.add(namespace)
        }
        parts.add(repository)
        return parts.join('/')
    }

    /**
     * Check if this is a registry-qualified reference
     */
    boolean isRegistryQualified() {
        return registry != null && !registry.isEmpty()
    }

    /**
     * Check if this appears to be a Docker Hub reference
     */
    boolean isDockerHub() {
        return !isRegistryQualified() || registry?.startsWith("docker.io")
    }

    @Override
    String toString() {
        return "ImageRefParts{registry='${registry}', namespace='${namespace}', repository='${repository}', tag='${tag}'}"
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) return true
        if (!(obj instanceof ImageRefParts)) return false

        ImageRefParts other = (ImageRefParts) obj
        return Objects.equals(registry, other.registry) &&
               Objects.equals(namespace, other.namespace) &&
               Objects.equals(repository, other.repository) &&
               Objects.equals(tag, other.tag)
    }

    @Override
    int hashCode() {
        return Objects.hash(registry, namespace, repository, tag)
    }
}