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

import java.time.Duration

/**
 * Configuration for waiting for services to reach a desired state
 */
class WaitConfig {
    final String projectName
    final List<String> services
    final Duration timeout
    final Duration pollInterval
    final ServiceStatus targetState
    
    WaitConfig(String projectName, List<String> services, Duration timeout, ServiceStatus targetState) {
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null")
        this.services = Objects.requireNonNull(services, "Services list cannot be null")
        this.timeout = timeout ?: Duration.ofSeconds(60)
        this.pollInterval = Duration.ofSeconds(2)
        this.targetState = Objects.requireNonNull(targetState, "Target state cannot be null")
    }
    
    WaitConfig(String projectName, List<String> services, Duration timeout, Duration pollInterval, ServiceStatus targetState) {
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null")
        this.services = Objects.requireNonNull(services, "Services list cannot be null")
        this.timeout = timeout ?: Duration.ofSeconds(60)
        this.pollInterval = pollInterval ?: Duration.ofSeconds(2)
        this.targetState = Objects.requireNonNull(targetState, "Target state cannot be null")
    }
    
    /**
     * Calculate total wait attempts based on timeout and poll interval
     */
    int getTotalWaitAttempts() {
        return Math.max(1, (timeout.toSeconds() / pollInterval.toSeconds()).intValue())
    }
    
    @Override
    String toString() {
        return "WaitConfig{projectName='${projectName}', services=${services}, timeout=${timeout}, pollInterval=${pollInterval}, targetState=${targetState}}"
    }
}