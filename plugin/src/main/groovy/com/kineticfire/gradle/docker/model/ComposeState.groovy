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
 * Represents the current state of a Docker Compose stack
 */
class ComposeState {
    String configName
    String projectName
    Map<String, ServiceInfo> services = [:]
    List<String> networks = []
    
    // Default constructor for Jackson
    ComposeState() {}
    
    // Support for named parameter constructor used by tests
    ComposeState(Map<String, Object> args) {
        this.configName = args.configName
        this.projectName = args.projectName
        this.services = args.services ?: [:]
        this.networks = args.networks ?: []
    }
    
    // Traditional constructor
    ComposeState(String configName, String projectName, Map<String, ServiceInfo> services = [:], List<String> networks = []) {
        this.configName = configName
        this.projectName = projectName
        this.services = services
        this.networks = networks
    }
    
    @Override
    String toString() {
        return "ComposeState{configName='${configName}', projectName='${projectName}', services=${services.size()} services, networks=${networks.size()} networks}"
    }
}