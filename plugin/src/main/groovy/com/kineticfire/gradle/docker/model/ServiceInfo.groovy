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
    String containerId
    String containerName
    String state
    List<PortMapping> publishedPorts = []
    
    // Default constructor for Jackson
    ServiceInfo() {}
    
    // Support for named parameter constructor used by tests
    ServiceInfo(Map<String, Object> args) {
        this.containerId = args.containerId
        this.containerName = args.containerName
        this.state = args.state
        this.publishedPorts = args.publishedPorts ?: []
    }
    
    // Traditional constructor
    ServiceInfo(String containerId, String containerName, String state, List<PortMapping> publishedPorts = []) {
        this.containerId = containerId
        this.containerName = containerName
        this.state = state
        this.publishedPorts = publishedPorts ?: []
    }
    
    /**
     * Check if service is in healthy state
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    boolean isHealthy() {
        return state.toLowerCase().contains('healthy')
    }
    
    /**
     * Check if service is running
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    boolean isRunning() {
        return state.toLowerCase().contains('up') || state.toLowerCase().contains('running')
    }
    
    /**
     * Get port mapping for a container port
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    PortMapping getPortMapping(int containerPort) {
        return publishedPorts.find { it.container == containerPort }
    }
    
    /**
     * Get host port for a container port
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    Integer getHostPort(int containerPort) {
        def mapping = getPortMapping(containerPort)
        return mapping?.host
    }
    
    @Override
    String toString() {
        return "ServiceInfo{containerId='${containerId}', containerName='${containerName}', state='${state}', ports=${publishedPorts.size()} mappings}"
    }
}