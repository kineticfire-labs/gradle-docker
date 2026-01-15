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

import java.nio.file.Path

/**
 * Configuration for capturing Docker Compose logs.
 *
 * <p><b>tailLines semantic values:</b></p>
 * <ul>
 *   <li>{@code tailLines > 0}: Fetch only the last N lines (adds {@code --tail N} flag)</li>
 *   <li>{@code tailLines <= 0}: Fetch all logs (no {@code --tail} flag added)</li>
 * </ul>
 * <p>Use {@link #hasLimitedTail()} to check if tailLines should be applied.</p>
 */
class LogsConfig {
    final List<String> services
    final int tailLines
    final boolean follow
    final Path outputFile

    LogsConfig(List<String> services, int tailLines = 100, boolean follow = false, Path outputFile = null) {
        this.services = services ?: []
        this.tailLines = tailLines
        this.follow = follow
        this.outputFile = outputFile
    }

    /**
     * Returns true if tailLines should be applied (positive value).
     * When false, the --tail flag should be omitted to fetch all logs.
     *
     * @return true if tailLines is positive, false otherwise
     */
    boolean hasLimitedTail() {
        return tailLines > 0
    }

    /**
     * Check if specific services are configured
     */
    boolean hasSpecificServices() {
        return !services.empty
    }

    /**
     * Check if output should be written to file
     */
    boolean hasOutputFile() {
        return outputFile != null
    }
    
    @Override
    String toString() {
        return "LogsConfig{services=${services}, tailLines=${tailLines}, follow=${follow}, outputFile=${outputFile}}"
    }
}