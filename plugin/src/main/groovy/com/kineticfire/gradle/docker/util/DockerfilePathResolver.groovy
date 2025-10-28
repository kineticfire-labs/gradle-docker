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

package com.kineticfire.gradle.docker.util

import java.nio.file.Path

/**
 * Pure utility for Dockerfile path resolution and validation.
 * All methods are static and deterministic, making them 100% unit testable.
 *
 * This class is stateless and Gradle 9/10 configuration-cache compatible.
 */
class DockerfilePathResolver {

    /**
     * Validate that Dockerfile is within the build context directory
     *
     * @param contextPath Build context directory
     * @param dockerfilePath Dockerfile path
     * @throws IllegalArgumentException if Dockerfile is outside context
     */
    static void validateDockerfileLocation(Path contextPath, Path dockerfilePath) {
        if (!dockerfilePath.toAbsolutePath().startsWith(contextPath.toAbsolutePath())) {
            throw new IllegalArgumentException(
                "Dockerfile must be within the build context directory. " +
                "Dockerfile: ${dockerfilePath.toAbsolutePath()}, " +
                "Context: ${contextPath.toAbsolutePath()}"
            )
        }
    }

    /**
     * Calculate relative path from context to Dockerfile
     *
     * @param contextPath Build context directory
     * @param dockerfilePath Dockerfile path
     * @return Relative path string
     */
    static String calculateRelativePath(Path contextPath, Path dockerfilePath) {
        return contextPath.relativize(dockerfilePath).toString()
    }

    /**
     * Determine if Dockerfile is in a subdirectory (needs temp copy workaround)
     * This is required due to Docker Java Client issues with subdirectory Dockerfiles
     *
     * @param relativePath Relative path from context to Dockerfile
     * @return true if Dockerfile needs temporary copy at context root
     */
    static boolean needsTemporaryDockerfile(String relativePath) {
        return relativePath.contains("/") || relativePath.contains("\\")
    }

    /**
     * Generate temporary Dockerfile name with timestamp to avoid collisions
     *
     * @return Unique temporary filename
     */
    static String generateTempDockerfileName() {
        return "Dockerfile.tmp.${System.currentTimeMillis()}"
    }
}
