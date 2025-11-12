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

package com.kineticfire.gradle.docker.util

import com.kineticfire.gradle.docker.model.ServiceStatus
import com.kineticfire.gradle.docker.model.PortMapping
import com.kineticfire.gradle.docker.model.ServiceInfo
import groovy.json.JsonSlurper

/**
 * Pure utility for parsing docker-compose command outputs.
 * All methods are static and deterministic, making them 100% unit testable.
 *
 * This class is stateless and Gradle 9/10 configuration-cache compatible.
 */
class ComposeOutputParser {

    /**
     * Parse service state from docker-compose status string
     *
     * @param status Status string (e.g., "Up (healthy)", "running", "exited")
     * @return Corresponding ServiceStatus enum value
     */
    static ServiceStatus parseServiceState(String status) {
        if (!status) {
            return ServiceStatus.UNKNOWN
        }

        def lowerStatus = status.toLowerCase()
        if (lowerStatus.contains('running') || lowerStatus.contains('up')) {
            if (lowerStatus.contains('healthy')) {
                return ServiceStatus.HEALTHY
            }
            return ServiceStatus.RUNNING
        } else if (lowerStatus.contains('exit') || lowerStatus.contains('stop')) {
            return ServiceStatus.STOPPED
        } else if (lowerStatus.contains('restart') || lowerStatus.contains('restarting')) {
            return ServiceStatus.RESTARTING
        } else {
            return ServiceStatus.UNKNOWN
        }
    }

    /**
     * Parse port mappings from docker-compose ps output
     *
     * @param portsString Port string (e.g., "0.0.0.0:9091->8080/tcp, :::9091->8080/tcp")
     * @return List of PortMapping objects
     */
    static List<PortMapping> parsePortMappings(String portsString) {
        if (!portsString) {
            return []
        }

        def portMappings = []
        portsString.split(',').each { portEntry ->
            def trimmed = portEntry.trim()
            if (trimmed) {
                try {
                    // Parse format: "0.0.0.0:9091->8080/tcp" or "9091->8080/tcp"
                    def matcher = trimmed =~ /(?:[\d\.]+:)?(\d+)->(\d+)(?:\/(\w+))?/
                    if (matcher.find()) {
                        def hostPort = matcher.group(1) as Integer
                        def containerPort = matcher.group(2) as Integer
                        def protocol = matcher.group(3) ?: 'tcp'
                        portMappings << new PortMapping(containerPort, hostPort, protocol)
                    }
                } catch (Exception e) {
                    // Skip malformed port entries silently
                }
            }
        }
        return portMappings
    }

    /**
     * Parse services from docker-compose ps JSON output
     *
     * @param jsonOutput JSON output from 'docker compose ps --format json'
     * @return Map of service name to ServiceInfo
     */
    static Map<String, ServiceInfo> parseServicesJson(String jsonOutput) {
        def services = [:]

        if (!jsonOutput?.trim()) {
            return services
        }

        // Parse JSON lines (docker compose ps outputs one JSON object per line)
        jsonOutput.split('\n').each { line ->
            if (line?.trim()) {
                try {
                    def json = new JsonSlurper().parseText(line)
                    def serviceInfo = parseServiceInfoFromJson(json)
                    if (serviceInfo) {
                        services[serviceInfo.containerName] = serviceInfo
                    }
                } catch (Exception e) {
                    // Skip malformed JSON lines
                }
            }
        }

        return services
    }

    /**
     * Parse single ServiceInfo from JSON object
     *
     * @param json JSON object representing a service
     * @return ServiceInfo or null if service name cannot be determined
     */
    static ServiceInfo parseServiceInfoFromJson(Map json) {
        // Extract service name from Service field or parse from Name
        def serviceName = json.Service
        if (!serviceName && json.Name) {
            // Parse service name from container name format: {project}_{service}_{index}
            def parts = json.Name.split('_')
            if (parts.length >= 2) {
                serviceName = parts[1]
            }
        }

        if (!serviceName) {
            return null
        }

        def status = json.State ?: json.Status
        def serviceState = parseServiceState(status)
        def portMappings = parsePortMappings(json.Ports as String)

        return new ServiceInfo(
            json.ID ?: 'unknown',
            serviceName,
            serviceState.toString(),
            portMappings
        )
    }
}
