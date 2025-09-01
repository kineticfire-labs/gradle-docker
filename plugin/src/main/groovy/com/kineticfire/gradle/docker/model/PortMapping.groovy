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
 * Represents a port mapping between container and host
 */
class PortMapping {
    int containerPort
    int hostPort
    String protocol
    
    // Default constructor for Jackson
    PortMapping() {}
    
    // Support for named parameter constructor used by tests
    PortMapping(Map<String, Object> args) {
        this.containerPort = args.containerPort
        this.hostPort = args.hostPort
        this.protocol = args.protocol ?: "tcp"
    }
    
    // Traditional constructors for compatibility
    PortMapping(int containerPort, int hostPort, String protocol = "tcp") {
        this.containerPort = containerPort
        this.hostPort = hostPort
        this.protocol = protocol ?: "tcp"
    }
    
    // Backward compatibility properties - marked as @JsonIgnore to avoid deserialization issues
    @com.fasterxml.jackson.annotation.JsonIgnore
    int getContainer() { return containerPort }
    
    @com.fasterxml.jackson.annotation.JsonIgnore
    int getHost() { return hostPort }
    
    /**
     * Get the mapping in Docker format (host:container/protocol)
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    String getDockerFormat() {
        return "${hostPort}:${containerPort}/${protocol}"
    }
    
    @Override
    String toString() {
        return "PortMapping{${hostPort}:${containerPort}/${protocol}}"
    }
    
    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) return true
        if (!(obj instanceof PortMapping)) return false
        
        PortMapping other = (PortMapping) obj
        return containerPort == other.containerPort && 
               hostPort == other.hostPort && 
               Objects.equals(protocol, other.protocol)
    }
    
    @Override
    int hashCode() {
        return Objects.hash(containerPort, hostPort, protocol)
    }
}