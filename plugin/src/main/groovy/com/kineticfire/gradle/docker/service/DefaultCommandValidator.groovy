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

import com.kineticfire.gradle.docker.exception.ComposeServiceException
import java.time.Duration

/**
 * Default implementation of CommandValidator
 * Uses ProcessExecutor to detect and validate Docker Compose
 */
class DefaultCommandValidator implements CommandValidator {
    
    private final ProcessExecutor processExecutor
    private List<String> cachedComposeCommand
    
    DefaultCommandValidator(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor
    }
    
    @Override
    void validateDockerCompose() {
        def command = detectComposeCommand()
        
        try {
            def versionCommand = command + ['version']
            def result = processExecutor.execute(versionCommand, null, Duration.ofSeconds(30))
            
            if (!result.isSuccess()) {
                throw new ComposeServiceException(
                ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE,
                "Docker Compose validation failed"
            )
            }
        } catch (Exception e) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE,
                "Docker Compose is not available or not working: ${e.message}",
                e
            )
        }
    }
    
    @Override
    List<String> detectComposeCommand() {
        if (cachedComposeCommand) {
            return cachedComposeCommand
        }
        
        // Try "docker compose" first (newer syntax)
        try {
            def result = processExecutor.execute(['docker', 'compose', 'version'], null, Duration.ofSeconds(10))
            if (result.isSuccess()) {
                cachedComposeCommand = ['docker', 'compose']
                return cachedComposeCommand
            }
        } catch (Exception ignored) {
            // Fall through to try docker-compose
        }
        
        // Try "docker-compose" (legacy syntax)
        try {
            def result = processExecutor.execute(['docker-compose', '--version'], null, Duration.ofSeconds(10))
            if (result.isSuccess()) {
                cachedComposeCommand = ['docker-compose']
                return cachedComposeCommand
            }
        } catch (Exception ignored) {
            // Fall through to error
        }
        
        throw new ComposeServiceException(
            ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE,
            "Neither 'docker compose' nor 'docker-compose' command is available"
        )
    }
}