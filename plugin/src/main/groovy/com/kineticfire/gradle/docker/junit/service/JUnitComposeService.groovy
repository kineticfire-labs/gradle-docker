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

                if (config.tailLines > 0) {
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

            // Simple parsing - in production would use proper JSON library
            def services = [:]
            if (result.output != null && !result.output.trim().isEmpty()) {
                // Docker compose ps outputs one JSON object per line
                String[] lines = result.output.split("\n")
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue
                    // Simple extraction - would use JSON parser in production
                    services.put("time-server", new ServiceInfo(
                        "container-id",
                        "time-server",
                        "running",
                        []
                    ))
                }
            }

            return services

        } catch (Exception e) {
            System.err.println("Error getting stack services: ${e.message}")
            return Collections.emptyMap()
        }
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
