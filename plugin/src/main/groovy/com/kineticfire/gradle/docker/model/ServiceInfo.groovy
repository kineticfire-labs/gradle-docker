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

/**
 * Information about a Docker service/container
 */
class ServiceInfo {
    final String containerId
    final String containerName
    final String state
    final List<PortMapping> publishedPorts
    
    ServiceInfo(String containerId, String containerName, String state, List<PortMapping> publishedPorts) {
        this.containerId = Objects.requireNonNull(containerId, "Container ID cannot be null")
        this.containerName = Objects.requireNonNull(containerName, "Container name cannot be null")
        this.state = Objects.requireNonNull(state, "State cannot be null")
        this.publishedPorts = publishedPorts ?: []
    }
    
    /**
     * Check if service is in healthy state
     */
    boolean isHealthy() {
        return state.toLowerCase().contains('healthy')
    }
    
    /**
     * Check if service is running
     */
    boolean isRunning() {
        return state.toLowerCase().contains('up') || state.toLowerCase().contains('running')
    }
    
    /**
     * Get port mapping for a container port
     */
    PortMapping getPortMapping(int containerPort) {
        return publishedPorts.find { it.container == containerPort }
    }
    
    /**
     * Get host port for a container port
     */
    Integer getHostPort(int containerPort) {
        def mapping = getPortMapping(containerPort)
        return mapping?.host
    }
    
    @Override
    String toString() {
        return "ServiceInfo{containerId='${containerId}', containerName='${containerName}', state='${state}', ports=${publishedPorts.size()} mappings}"
    }
}