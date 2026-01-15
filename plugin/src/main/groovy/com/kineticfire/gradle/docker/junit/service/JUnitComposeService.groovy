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

package com.kineticfire.gradle.docker.junit.service

import com.kineticfire.gradle.docker.model.*
import com.kineticfire.gradle.docker.service.ComposeService
import com.kineticfire.gradle.docker.util.LogPatternMatcher

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Standalone ComposeService implementation for JUnit extensions.
 * Does not depend on Gradle BuildService infrastructure.
 */
class JUnitComposeService implements ComposeService {

    private final ProcessExecutor processExecutor

    JUnitComposeService() {
        this(new DefaultProcessExecutor())
    }

    JUnitComposeService(ProcessExecutor processExecutor) {
        this.processExecutor = processExecutor
    }

    @Override
    CompletableFuture<ComposeState> upStack(ComposeConfig config) {
        if (config == null) {
            throw new NullPointerException("Compose config cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                println "Starting Docker Compose stack: ${config.stackName}"

                def command = ["docker", "compose"]

                // Add compose files
                config.composeFiles.each { file ->
                    command << "-f" << file.toString()
                }

                // Add project name
                command << "-p" << config.projectName

                // Add env files
                config.envFiles.each { envFile ->
                    command << "--env-file" << envFile.toString()
                }

                // Add the up command
                command << "up" << "-d" << "--remove-orphans"

                def workingDir = config.composeFiles.first().parent.toFile()
                def result = processExecutor.executeInDirectory(workingDir, command as String[])

                if (result.exitCode != 0) {
                    throw new RuntimeException(
                        "Docker Compose up failed with exit code ${result.exitCode}: ${result.output}"
                    )
                }

                // Get current stack state
                def services = getStackServices(config.projectName)

                def composeState = new ComposeState(
                    config.stackName,
                    config.projectName,
                    services
                )

                println "Docker Compose stack started: ${config.stackName}"
                return composeState

            } catch (Exception e) {
                throw new RuntimeException("Failed to start compose stack: ${e.message}", e)
            }
        })
    }

    @Override
    CompletableFuture<Void> downStack(String projectName) {
        if (projectName == null) {
            throw new NullPointerException("Project name cannot be null")
        }
        return CompletableFuture.runAsync({
            try {
                println "Stopping Docker Compose stack: ${projectName}"

                def result = processExecutor.execute(
                    "docker", "compose",
                    "-p", projectName,
                    "down", "--remove-orphans", "--volumes"
                )

                if (result.exitCode != 0) {
                    throw new RuntimeException(
                        "Docker Compose down failed with exit code ${result.exitCode}: ${result.output}"
                    )
                }

                println "Docker Compose stack stopped: ${projectName}"

            } catch (Exception e) {
                throw new RuntimeException("Failed to stop compose stack: ${e.message}", e)
            }
        })
    }

    @Override
    CompletableFuture<Void> downStack(ComposeConfig config) {
        if (config == null) {
            throw new NullPointerException("Compose config cannot be null")
        }
        return CompletableFuture.runAsync({
            try {
                println "Stopping Docker Compose stack: ${config.stackName} (project: ${config.projectName})"

                def command = ["docker", "compose"]

                // Add compose files
                config.composeFiles.each { file ->
                    command << "-f" << file.toString()
                }

                // Add project name
                command << "-p" << config.projectName

                // Add the down command
                command << "down" << "--remove-orphans" << "--volumes"

                def workingDir = config.composeFiles.first().parent.toFile()
                def result = processExecutor.executeInDirectory(workingDir, command as String[])

                if (result.exitCode != 0) {
                    throw new RuntimeException(
                        "Docker Compose down failed with exit code ${result.exitCode}: ${result.output}"
                    )
                }

                println "Docker Compose stack stopped: ${config.stackName}"

            } catch (Exception e) {
                throw new RuntimeException("Failed to stop compose stack: ${e.message}", e)
            }
        })
    }

    @Override
    CompletableFuture<ServiceStatus> waitForServices(WaitConfig config) {
        if (config == null) {
            throw new NullPointerException("Wait config cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                println "Waiting for services to reach ${config.targetState}: ${config.services}"

                long startTime = System.currentTimeMillis()
                long timeoutMillis = config.timeout.toMillis()

                while (System.currentTimeMillis() - startTime < timeoutMillis) {
                    boolean allReady = true
                    for (String serviceName : config.services) {
                        boolean serviceReady = checkServiceReady(
                            config.projectName,
                            serviceName,
                            config.targetState
                        )
                        if (!serviceReady) {
                            allReady = false
                            break
                        }
                    }

                    if (allReady) {
                        println "All services are ready: ${config.services}"
                        return config.targetState
                    }

                    Thread.sleep(config.pollInterval.toMillis())
                }

                throw new RuntimeException(
                    "Timeout waiting for services to reach ${config.targetState}: ${config.services}"
                )

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                throw new RuntimeException("Interrupted while waiting for services", e)
            } catch (Exception e) {
                throw new RuntimeException("Error waiting for services: ${e.message}", e)
            }
        })
    }

    @Override
    CompletableFuture<String> captureLogs(String projectName, LogsConfig config) {
        if (projectName == null) {
            throw new NullPointerException("Project name cannot be null")
        }
        if (config == null) {
            throw new NullPointerException("Logs config cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                println "Capturing logs for project: ${projectName}"

                def command = ["docker", "compose", "-p", projectName, "logs"]

                if (config.follow) {
                    command << "--follow"
                }

                if (config.hasLimitedTail()) {
                    command << "--tail" << config.tailLines.toString()
                }

                if (config.services && !config.services.isEmpty()) {
                    command.addAll(config.services)
                }

                def result = processExecutor.execute(command as String[])

                if (result.exitCode != 0) {
                    throw new RuntimeException("Failed to capture logs: ${result.output}")
                }

                return result.output

            } catch (Exception e) {
                throw new RuntimeException("Failed to capture logs: ${e.message}", e)
            }
        })
    }

    @Override
    CompletableFuture<Map<String, WaitForLogResult>> waitForLogPatterns(WaitForLogConfig config) {
        if (config == null) {
            throw new NullPointerException("WaitForLogConfig cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                println "Waiting for log patterns in services: ${config.services.keySet()}"

                long startTime = System.currentTimeMillis()
                long timeoutMillis = config.timeoutSeconds * 1000L
                long pollMillis = config.pollSeconds * 1000L

                // Track matched patterns per service
                Map<String, Set<String>> matchedPatterns = [:]
                config.services.keySet().each { service ->
                    matchedPatterns[service] = new HashSet<>()
                }

                while (System.currentTimeMillis() - startTime < timeoutMillis) {
                    boolean allMatched = true

                    for (Map.Entry<String, List<String>> entry : config.services.entrySet()) {
                        String serviceName = entry.key
                        List<String> patterns = entry.value

                        // Fetch logs for the service
                        String logs = fetchServiceLogs(config.projectName, serviceName)

                        // Check reject patterns first
                        if (config.rejectPatterns.containsKey(serviceName)) {
                            for (String rejectPattern : config.rejectPatterns[serviceName]) {
                                if (LogPatternMatcher.matches(logs, rejectPattern, config.caseInsensitive)) {
                                    throw new RuntimeException(
                                        "Reject pattern matched in service '${serviceName}': ${rejectPattern}"
                                    )
                                }
                            }
                        }

                        // Check for required patterns
                        for (String pattern : patterns) {
                            if (!matchedPatterns[serviceName].contains(pattern)) {
                                if (LogPatternMatcher.matches(logs, pattern, config.caseInsensitive)) {
                                    matchedPatterns[serviceName].add(pattern)
                                    if (config.verbose) {
                                        println "  Matched pattern in ${serviceName}: ${pattern}"
                                    }
                                }
                            }
                        }

                        // Check if all patterns matched for this service
                        if (matchedPatterns[serviceName].size() < patterns.size()) {
                            allMatched = false
                        }
                    }

                    if (allMatched) {
                        println "All log patterns matched"
                        return buildResults(config.services, matchedPatterns)
                    }

                    Thread.sleep(pollMillis)
                }

                // Timeout - report which patterns are still missing
                def missingPatterns = [:]
                config.services.each { serviceName, patterns ->
                    def missing = patterns.findAll { !matchedPatterns[serviceName].contains(it) }
                    if (missing) {
                        missingPatterns[serviceName] = missing
                    }
                }
                throw new RuntimeException(
                    "Timeout waiting for log patterns. Missing patterns: ${missingPatterns}"
                )

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                throw new RuntimeException("Interrupted while waiting for log patterns", e)
            } catch (Exception e) {
                if (e.message?.startsWith("Timeout") || e.message?.startsWith("Reject pattern")) {
                    throw e
                }
                throw new RuntimeException("Error waiting for log patterns: ${e.message}", e)
            }
        })
    }

    private String fetchServiceLogs(String projectName, String serviceName) {
        try {
            def result = processExecutor.execute(
                "docker", "compose",
                "-p", projectName,
                "logs", "--no-color", serviceName
            )
            return result.exitCode == 0 ? result.output : ""
        } catch (Exception e) {
            System.err.println("Error fetching logs for service ${serviceName}: ${e.message}")
            return ""
        }
    }

    private Map<String, WaitForLogResult> buildResults(
        Map<String, List<String>> services,
        Map<String, Set<String>> matchedPatterns
    ) {
        def results = [:]
        services.each { serviceName, patterns ->
            results[serviceName] = new WaitForLogResult(
                serviceName,
                patterns as List,
                matchedPatterns[serviceName].toList()
            )
        }
        return results
    }

    private Map<String, ServiceInfo> getStackServices(String projectName) {
        try {
            def result = processExecutor.execute(
                "docker", "compose",
                "-p", projectName,
                "ps", "--format", "json"
            )

            if (result.exitCode != 0) {
                System.err.println("Failed to get stack services: ${result.output}")
                return Collections.emptyMap()
            }

            def services = [:]
            if (result.output != null && !result.output.trim().isEmpty()) {
                // Docker compose ps outputs one JSON object per line
                String[] lines = result.output.split("\n")
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue

                    try {
                        def json = new groovy.json.JsonSlurper().parseText(line)
                        def serviceName = json.Service ?: json.Name?.split('_')?.getAt(1)
                        def status = json.State ?: json.Status
                        def portMappings = parsePortMappings(json.Ports)

                        if (serviceName) {
                            def serviceState = parseServiceState(status)
                            services[serviceName] = new ServiceInfo(
                                json.ID ?: 'unknown',
                                serviceName,
                                serviceState.toString(),
                                portMappings
                            )
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse service info line: ${line} - ${e.message}")
                    }
                }
            }

            return services

        } catch (Exception e) {
            System.err.println("Error getting stack services: ${e.message}")
            return Collections.emptyMap()
        }
    }

    private ServiceStatus parseServiceState(String status) {
        if (!status) return ServiceStatus.UNKNOWN

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

    private List<PortMapping> parsePortMappings(String portsString) {
        if (!portsString) return []

        def portMappings = []
        // Docker Compose ps --format json returns ports like "0.0.0.0:9091->8080/tcp, :::9091->8080/tcp"
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
                    System.err.println("Failed to parse port mapping: ${trimmed} - ${e.message}")
                }
            }
        }
        return portMappings
    }

    private boolean checkServiceReady(String projectName, String serviceName, ServiceStatus targetState) {
        try {
            def result = processExecutor.execute(
                "docker", "compose",
                "-p", projectName,
                "ps", serviceName, "--format", "table"
            )

            if (result.exitCode == 0 && result.output != null) {
                String output = result.output.toLowerCase()
                if (targetState == ServiceStatus.RUNNING) {
                    return output.contains("up") || output.contains("running")
                } else if (targetState == ServiceStatus.HEALTHY) {
                    return output.contains("healthy")
                }
            }

            return false

        } catch (Exception e) {
            System.err.println("Error checking service ready state: ${e.message}")
            return false
        }
    }
}
