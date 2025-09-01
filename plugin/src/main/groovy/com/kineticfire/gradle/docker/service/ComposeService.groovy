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
import java.util.concurrent.CompletableFuture

/**
 * Service interface for Docker Compose operations
 */
interface ComposeService {
    
    /**
     * Start a Docker Compose stack
     * @param config Compose configuration with files, project name, etc.
     * @return CompletableFuture with the current state of services
     * @throws ComposeServiceException if startup fails
     */
    CompletableFuture<ComposeState> upStack(ComposeConfig config)
    
    /**
     * Stop a Docker Compose stack
     * @param projectName Compose project name
     * @return CompletableFuture that completes when stack is stopped
     * @throws ComposeServiceException if shutdown fails
     */
    CompletableFuture<Void> downStack(String projectName)
    
    /**
     * Wait for services to reach a desired state
     * @param config Wait configuration including services, timeout, target state
     * @return CompletableFuture with final service state
     * @throws ComposeServiceException if timeout or other error occurs
     */
    CompletableFuture<ServiceState> waitForServices(WaitConfig config)
    
    /**
     * Capture logs from compose services
     * @param projectName Compose project name
     * @param config Logs configuration
     * @return CompletableFuture with captured logs
     * @throws ComposeServiceException if log capture fails
     */
    CompletableFuture<String> captureLogs(String projectName, LogsConfig config)
}