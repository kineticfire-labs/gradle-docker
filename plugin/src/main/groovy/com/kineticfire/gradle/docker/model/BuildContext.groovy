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
 * Represents the build context for a Docker image build operation
 */
class BuildContext {
    final Path contextPath
    final Path dockerfile
    final Map<String, String> buildArgs
    final List<String> tags
    
    BuildContext(Path contextPath, Path dockerfile, Map<String, String> buildArgs, List<String> tags) {
        this.contextPath = Objects.requireNonNull(contextPath, "Context path cannot be null")
        this.dockerfile = Objects.requireNonNull(dockerfile, "Dockerfile path cannot be null") 
        this.buildArgs = buildArgs ?: [:]
        this.tags = tags ?: []
        validate()
    }
    
    private void validate() {
        if (!Files.exists(contextPath)) {
            throw new IllegalArgumentException("Context path does not exist: ${contextPath}")
        }
        if (!Files.exists(dockerfile)) {
            throw new IllegalArgumentException("Dockerfile does not exist: ${dockerfile}")
        }
        if (tags.empty) {
            throw new IllegalArgumentException("At least one tag must be specified")
        }
    }
    
    @Override
    String toString() {
        return "BuildContext{contextPath=${contextPath}, dockerfile=${dockerfile}, tags=${tags}, buildArgs=${buildArgs.size()} args}"
    }
}