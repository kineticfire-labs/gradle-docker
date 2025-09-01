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

import java.nio.file.Files
import java.nio.file.Path

/**
 * Configuration for Docker Compose operations
 */
class ComposeConfig {
    final List<Path> composeFiles
    final List<Path> envFiles
    final String projectName
    final String stackName
    final Map<String, String> environment
    
    ComposeConfig(List<Path> composeFiles, String projectName, String stackName) {
        this.composeFiles = Objects.requireNonNull(composeFiles, "Compose files cannot be null")
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null")
        this.stackName = Objects.requireNonNull(stackName, "Stack name cannot be null")
        this.envFiles = []
        this.environment = [:]
        validate()
    }
    
    ComposeConfig(List<Path> composeFiles, List<Path> envFiles, String projectName, String stackName, Map<String, String> environment) {
        this.composeFiles = Objects.requireNonNull(composeFiles, "Compose files cannot be null")
        this.envFiles = envFiles ?: []
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null") 
        this.stackName = Objects.requireNonNull(stackName, "Stack name cannot be null")
        this.environment = environment ?: [:]
        validate()
    }
    
    private void validate() {
        if (composeFiles.empty) {
            throw new IllegalArgumentException("At least one compose file must be specified")
        }
        composeFiles.each { file ->
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("Compose file does not exist: ${file}")
            }
        }
    }
    
    @Override
    String toString() {
        return "ComposeConfig{projectName='${projectName}', stackName='${stackName}', composeFiles=${composeFiles.size()} files, envFiles=${envFiles.size()} files}"
    }
}