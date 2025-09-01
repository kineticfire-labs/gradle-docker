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
 * Represents the state of a Docker Compose service
 */
class ServiceState {
    String name
    String status
    String health
    List<PortMapping> ports = []
    List<String> networks = []
    
    // Default constructor for Jackson
    ServiceState() {}
    
    // Support for named parameter constructor used by tests
    ServiceState(Map<String, Object> args) {
        this.name = args.name
        this.status = args.status
        this.health = args.health
        this.ports = args.ports ?: []
        this.networks = args.networks ?: []
    }
    
    // Traditional constructor
    ServiceState(String name, String status, String health = null, List<PortMapping> ports = [], List<String> networks = []) {
        this.name = name
        this.status = status
        this.health = health
        this.ports = ports
        this.networks = networks
    }
    
    @com.fasterxml.jackson.annotation.JsonIgnore
    boolean isRunning() {
        return status?.toLowerCase() == 'running'
    }
    
    @com.fasterxml.jackson.annotation.JsonIgnore
    boolean isHealthy() {
        return health?.toLowerCase() == 'healthy'
    }
    
    @Override
    String toString() {
        return "ServiceState{name='${name}', status='${status}', health='${health}', ports=${ports.size()} ports, networks=${networks.size()} networks}"
    }
}