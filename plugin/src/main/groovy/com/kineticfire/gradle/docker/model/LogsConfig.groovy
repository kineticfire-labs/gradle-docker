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
 * Configuration for capturing Docker Compose logs
 */
class LogsConfig {
    final List<String> services
    final int tailLines
    final boolean follow
    final Path outputFile
    
    LogsConfig(List<String> services, int tailLines = 100, boolean follow = false, Path outputFile = null) {
        this.services = services ?: []
        this.tailLines = Math.max(1, tailLines)
        this.follow = follow
        this.outputFile = outputFile
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