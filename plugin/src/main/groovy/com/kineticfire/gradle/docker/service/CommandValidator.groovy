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

/**
 * Interface for validating and detecting Docker Compose commands
 * Allows for testable abstraction of command detection logic
 */
interface CommandValidator {
    
    /**
     * Validate that Docker Compose is available and working
     * @throws ComposeServiceException if Docker Compose is not available
     */
    void validateDockerCompose()
    
    /**
     * Detect the available Docker Compose command
     * @return List containing the command (e.g., ["docker", "compose"] or ["docker-compose"])
     */
    List<String> detectComposeCommand()
}