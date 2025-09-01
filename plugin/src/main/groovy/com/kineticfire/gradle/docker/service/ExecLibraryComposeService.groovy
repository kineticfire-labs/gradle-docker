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

import com.kineticfire.gradle.docker.exception.ComposeServiceException
import com.kineticfire.gradle.docker.model.*
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import javax.inject.Inject
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Real implementation of Docker Compose service using process execution
 */
abstract class ExecLibraryComposeService implements BuildService<BuildServiceParameters.None>, ComposeService {
    
    private static final Logger logger = Logging.getLogger(ExecLibraryComposeService)
    
    @Inject
    ExecLibraryComposeService() {
        validateDockerCompose()
        logger.info("ComposeService initialized with docker-compose CLI")
    }
    
    protected void validateDockerCompose() {
        try {
            def process = new ProcessBuilder("docker", "compose", "version").start()
            process.waitFor()
            if (process.exitValue() != 0) {
                // Try fallback to docker-compose
                process = new ProcessBuilder("docker-compose", "--version").start()
                process.waitFor()
                if (process.exitValue() != 0) {
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.COMPOSE_NOT_FOUND,
                        "Docker Compose is not available. Please install Docker Compose.",
                        "Install Docker Compose or Docker Desktop which includes Compose"
                    )
                }
                logger.info("Using legacy docker-compose command")
            } else {
                logger.info("Using docker compose plugin")
            }
        } catch (Exception e) {
            throw new ComposeServiceException(
                ComposeServiceException.ErrorType.COMPOSE_NOT_FOUND,
                "Failed to validate Docker Compose installation: ${e.message}",
                "Ensure Docker and Docker Compose are installed and accessible in PATH"
            )
        }
    }
    
    protected List<String> getComposeCommand() {
        // Try docker compose first (newer), fallback to docker-compose
        try {
            def process = new ProcessBuilder("docker", "compose", "version").start()
            process.waitFor()
            if (process.exitValue() == 0) {
                return ["docker", "compose"]
            }
        } catch (Exception ignored) {
            // Fall through to legacy command
        }
        return ["docker-compose"]
    }
    
    @Override
    CompletableFuture<ComposeState> upStack(ComposeConfig config) {
        return CompletableFuture.supplyAsync({
            try {
                logger.info("Starting Docker Compose stack: {}", config.stackName)
                
                def composeCommand = getComposeCommand()
                def command = composeCommand.clone()
                
                // Add compose files
                config.composeFiles.each { file ->
                    command.addAll(["-f", file.toString()])
                }
                
                // Add project name
                command.addAll(["-p", config.projectName])
                
                // Add env files
                config.envFiles.each { envFile ->
                    command.addAll(["--env-file", envFile.toString()])
                }
                
                // Add the up command
                command.addAll(["up", "-d"])
                
                logger.debug("Executing: {}", command.join(" "))
                
                def processBuilder = new ProcessBuilder(command)
                processBuilder.directory(config.composeFiles.first().parent.toFile())
                
                def process = processBuilder.start()
                def exitCode = process.waitFor()
                
                if (exitCode != 0) {
                    def errorOutput = process.errorStream.text
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                        "Docker Compose up failed with exit code ${exitCode}: ${errorOutput}",
                        "Check your compose file syntax and service configurations"
                    )
                }
                
                // Get current stack state
                def services = getStackServices(config.projectName)
                
                def composeState = new ComposeState(
                    config.stackName,
                    config.projectName,
                    services
                )
                
                logger.info("Docker Compose stack started: {}", config.stackName)
                return composeState
                
            } catch (ComposeServiceException e) {
                throw e
            } catch (Exception e) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                    "Failed to start compose stack: ${e.message}",
                    e
                )
            }
        })
    }
    
    protected Map<String, ServiceInfo> getStackServices(String projectName) {
        try {
            def composeCommand = getComposeCommand()
            def command = composeCommand + ["-p", projectName, "ps", "--format", "json"]
            
            def process = new ProcessBuilder(command).start()
            def output = process.inputStream.text
            def exitCode = process.waitFor()
            
            if (exitCode != 0) {
                logger.warn("Failed to get stack services for project {}: {}", projectName, process.errorStream.text)
                return [:]
            }
            
            def services = [:]
            if (output?.trim()) {
                // Parse JSON lines (docker compose ps outputs one JSON object per line)
                output.split('\n').each { line ->
                    if (line?.trim()) {
                        try {
                            def json = new groovy.json.JsonSlurper().parseText(line)
                            def serviceName = json.Service ?: json.Name?.split('_')?.getAt(1) // fallback parsing
                            def status = json.State ?: json.Status
                            def ports = json.Ports ? [json.Ports] : []
                            
                            if (serviceName) {
                                def serviceState = parseServiceState(status)
                                services[serviceName] = new ServiceInfo(
                                    serviceName,
                                    json.Image ?: 'unknown',
                                    serviceState,
                                    ports,
                                    json.Names ? [json.Names] : [json.Name ?: serviceName],
                                    json.ID ?: 'unknown'
                                )
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to parse service info line: {} - {}", line, e.message)
                        }
                    }
                }
            }
            
            return services
            
        } catch (Exception e) {
            logger.warn("Error getting stack services: {}", e.message)
            return [:]
        }
    }
    
    protected ServiceState parseServiceState(String status) {
        if (!status) return ServiceState.UNKNOWN
        
        def lowerStatus = status.toLowerCase()
        if (lowerStatus.contains('running') || lowerStatus.contains('up')) {
            if (lowerStatus.contains('healthy')) {
                return ServiceState.HEALTHY
            }
            return ServiceState.RUNNING
        } else if (lowerStatus.contains('exit') || lowerStatus.contains('stop')) {
            return ServiceState.STOPPED
        } else if (lowerStatus.contains('restart') || lowerStatus.contains('restarting')) {
            return ServiceState.RESTARTING
        } else {
            return ServiceState.UNKNOWN
        }
    }

    @Override
    CompletableFuture<Void> downStack(String projectName) {
        return CompletableFuture.runAsync({
            try {
                logger.info("Stopping Docker Compose stack: {}", projectName)
                
                def composeCommand = getComposeCommand()
                def command = composeCommand + ["-p", projectName, "down", "--remove-orphans"]
                
                logger.debug("Executing: {}", command.join(" "))
                
                def process = new ProcessBuilder(command).start()
                def exitCode = process.waitFor()
                
                if (exitCode != 0) {
                    def errorOutput = process.errorStream.text
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                        "Docker Compose down failed with exit code ${exitCode}: ${errorOutput}",
                        "Check if the project exists and is accessible"
                    )
                }
                
                logger.info("Docker Compose stack stopped: {}", projectName)
                
            } catch (ComposeServiceException e) {
                throw e
            } catch (Exception e) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                    "Failed to stop compose stack: ${e.message}",
                    e
                )
            }
        })
    }
    
    @Override
    CompletableFuture<ServiceState> waitForServices(WaitConfig config) {
        return CompletableFuture.supplyAsync({
            try {
                logger.info("Waiting for services: {}", config.services)
                
                def startTime = System.currentTimeMillis()
                def timeoutMillis = config.timeout.toMillis()
                
                while (System.currentTimeMillis() - startTime < timeoutMillis) {
                    // Check service states
                    def allReady = true
                    for (serviceName in config.services) {
                        def serviceReady = checkServiceReady(config.projectName, serviceName, config.targetState)
                        if (!serviceReady) {
                            allReady = false
                            break
                        }
                    }
                    
                    if (allReady) {
                        logger.info("All services are ready: {}", config.services)
                        return config.targetState
                    }
                    
                    Thread.sleep(config.pollInterval.toMillis())
                }
                
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_TIMEOUT,
                    "Timeout waiting for services to reach ${config.targetState}: ${config.services}",
                    "Increase timeout or check service health configuration"
                )
                
            } catch (ComposeServiceException e) {
                throw e
            } catch (Exception e) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.SERVICE_TIMEOUT,
                    "Error waiting for services: ${e.message}",
                    e
                )
            }
        })
    }
    
    protected boolean checkServiceReady(String projectName, String serviceName, ServiceState targetState) {
        try {
            def composeCommand = getComposeCommand()
            def command = composeCommand + ["-p", projectName, "ps", serviceName, "--format", "table"]
            
            def process = new ProcessBuilder(command).start()
            def output = process.inputStream.text
            def exitCode = process.waitFor()
            
            if (exitCode == 0 && output) {
                // Simple state checking - in real implementation would parse the output properly
                if (targetState == ServiceState.RUNNING) {
                    return output.toLowerCase().contains("up") || output.toLowerCase().contains("running")
                } else if (targetState == ServiceState.HEALTHY) {
                    return output.toLowerCase().contains("healthy")
                }
            }
            
            return false
            
        } catch (Exception e) {
            logger.debug("Error checking service ready state: {}", e.message)
            return false
        }
    }
    
    @Override
    CompletableFuture<String> captureLogs(String projectName, LogsConfig config) {
        return CompletableFuture.supplyAsync({
            try {
                logger.info("Capturing logs for project: {}", projectName)
                
                def composeCommand = getComposeCommand()
                def command = composeCommand + ["-p", projectName, "logs"]
                
                if (config.follow) {
                    command.add("--follow")
                }
                
                if (config.tail > 0) {
                    command.addAll(["--tail", config.tail.toString()])
                }
                
                if (config.services && !config.services.isEmpty()) {
                    command.addAll(config.services)
                }
                
                def process = new ProcessBuilder(command).start()
                def output = process.inputStream.text
                def exitCode = process.waitFor()
                
                if (exitCode != 0) {
                    def errorOutput = process.errorStream.text
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED,
                        "Failed to capture logs: ${errorOutput}",
                        "Check if the project and services exist"
                    )
                }
                
                return output
                
            } catch (ComposeServiceException e) {
                throw e
            } catch (Exception e) {
                throw new ComposeServiceException(
                    ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED,
                    "Failed to capture logs: ${e.message}",
                    e
                )
            }
        })
    }
}