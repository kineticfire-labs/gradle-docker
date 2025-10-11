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
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import javax.inject.Inject
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Real implementation of Docker Compose service using process execution
 */
abstract class ExecLibraryComposeService implements BuildService<BuildServiceParameters.None>, ComposeService {

    protected ProcessExecutor processExecutor
    protected CommandValidator commandValidator
    protected ServiceLogger serviceLogger
    
    @Inject
    ExecLibraryComposeService() {
        this.processExecutor = new DefaultProcessExecutor()
        this.commandValidator = new DefaultCommandValidator(new DefaultProcessExecutor())
        this.serviceLogger = new DefaultServiceLogger(ExecLibraryComposeService.class)

        commandValidator.validateDockerCompose()
        serviceLogger.info("ComposeService initialized with docker-compose CLI")
    }
    
    @VisibleForTesting
    ExecLibraryComposeService(ProcessExecutor processExecutor, 
                              CommandValidator commandValidator, 
                              ServiceLogger serviceLogger) {
        this.processExecutor = processExecutor
        this.commandValidator = commandValidator
        this.serviceLogger = serviceLogger
        
        commandValidator.validateDockerCompose()
        serviceLogger.info("ComposeService initialized with docker-compose CLI")
    }
    
    protected List<String> getComposeCommand() {
        return commandValidator.detectComposeCommand()
    }
    
    @Override
    CompletableFuture<ComposeState> upStack(ComposeConfig config) {
        if (config == null) {
            throw new NullPointerException("Compose config cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                serviceLogger.info("Starting Docker Compose stack: ${config.stackName}")
                
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
                
                serviceLogger.debug("Executing: ${command.join(' ')}")
                
                def workingDir = config.composeFiles.first().parent.toFile()
                def result = processExecutor.execute(command, workingDir)
                
                if (!result.isSuccess()) {
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                        "Docker Compose up failed with exit code ${result.exitCode}: ${result.stderr}",
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
                
                serviceLogger.info("Docker Compose stack started: ${config.stackName}")
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
            
            def result = processExecutor.execute(command)
            
            if (!result.isSuccess()) {
                serviceLogger.warn("Failed to get stack services for project ${projectName}: ${result.stderr}")
                return [:]
            }
            
            def services = [:]
            if (result.stdout?.trim()) {
                // Parse JSON lines (docker compose ps outputs one JSON object per line)
                result.stdout.split('\n').each { line ->
                    if (line?.trim()) {
                        try {
                            def json = new groovy.json.JsonSlurper().parseText(line)
                            def serviceName = json.Service ?: json.Name?.split('_')?.getAt(1) // fallback parsing
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
                            serviceLogger.debug("Failed to parse service info line: ${line} - ${e.message}")
                        }
                    }
                }
            }
            
            return services
            
        } catch (Exception e) {
            serviceLogger.warn("Error getting stack services: ${e.message}")
            return [:]
        }
    }
    
    protected ServiceStatus parseServiceState(String status) {
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

    protected List<PortMapping> parsePortMappings(String portsString) {
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
                    serviceLogger.debug("Failed to parse port mapping: ${trimmed} - ${e.message}")
                }
            }
        }
        return portMappings
    }

    @Override
    CompletableFuture<Void> downStack(String projectName) {
        if (projectName == null) {
            throw new NullPointerException("Project name cannot be null")
        }
        return CompletableFuture.runAsync({
            try {
                serviceLogger.info("Stopping Docker Compose stack: ${projectName}")
                
                def composeCommand = getComposeCommand()
                def command = composeCommand + ["-p", projectName, "down", "--remove-orphans"]
                
                serviceLogger.debug("Executing: ${command.join(' ')}")
                
                def result = processExecutor.execute(command)
                
                if (!result.isSuccess()) {
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                        "Docker Compose down failed with exit code ${result.exitCode}: ${result.stderr}",
                        "Check if the project exists and is accessible"
                    )
                }
                
                serviceLogger.info("Docker Compose stack stopped: ${projectName}")
                
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
    CompletableFuture<Void> downStack(ComposeConfig config) {
        if (config == null) {
            throw new NullPointerException("Compose config cannot be null")
        }
        return CompletableFuture.runAsync({
            try {
                serviceLogger.info("Stopping Docker Compose stack: ${config.stackName} (project: ${config.projectName})")
                
                def composeCommand = getComposeCommand()
                def command = composeCommand.clone()
                
                // Add compose files for proper teardown
                config.composeFiles.each { file ->
                    command.addAll(["-f", file.toString()])
                }
                
                // Add project name
                command.addAll(["-p", config.projectName])
                
                // Add env files if present
                config.envFiles.each { envFile ->
                    command.addAll(["--env-file", envFile.toString()])
                }
                
                // Add the down command
                command.addAll(["down", "--remove-orphans"])
                
                serviceLogger.debug("Executing: ${command.join(' ')}")
                
                def workingDir = config.composeFiles.first().parent.toFile()
                def result = processExecutor.execute(command, workingDir)
                
                if (!result.isSuccess()) {
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                        "Docker Compose down failed with exit code ${result.exitCode}: ${result.stderr}",
                        "Check your compose file syntax and project configuration"
                    )
                }
                
                serviceLogger.info("Docker Compose stack stopped: ${config.stackName} (project: ${config.projectName})")
                
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
    CompletableFuture<ServiceStatus> waitForServices(WaitConfig config) {
        if (config == null) {
            throw new NullPointerException("Wait config cannot be null")
        }
        return CompletableFuture.supplyAsync({
            try {
                serviceLogger.info("Waiting for services: ${config.services}")
                
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
                        serviceLogger.info("All services are ready: ${config.services}")
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
    
    protected boolean checkServiceReady(String projectName, String serviceName, ServiceStatus targetState) {
        try {
            def composeCommand = getComposeCommand()
            def command = composeCommand + ["-p", projectName, "ps", serviceName, "--format", "table"]
            
            def result = processExecutor.execute(command)
            
            if (result.isSuccess() && result.stdout) {
                // Simple state checking - in real implementation would parse the output properly
                if (targetState == ServiceStatus.RUNNING) {
                    return result.stdout.toLowerCase().contains("up") || result.stdout.toLowerCase().contains("running")
                } else if (targetState == ServiceStatus.HEALTHY) {
                    return result.stdout.toLowerCase().contains("healthy")
                }
            }
            
            return false
            
        } catch (Exception e) {
            serviceLogger.debug("Error checking service ready state: ${e.message}")
            return false
        }
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
                serviceLogger.info("Capturing logs for project: ${projectName}")
                
                def composeCommand = getComposeCommand()
                def command = composeCommand + ["-p", projectName, "logs"]
                
                if (config.follow) {
                    command.add("--follow")
                }
                
                if (config.tailLines > 0) {
                    command.addAll(["--tail", config.tailLines.toString()])
                }
                
                if (config.services && !config.services.isEmpty()) {
                    command.addAll(config.services)
                }
                
                def result = processExecutor.execute(command)
                
                if (!result.isSuccess()) {
                    throw new ComposeServiceException(
                        ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED,
                        "Failed to capture logs: ${result.stderr}",
                        "Check if the project and services exist"
                    )
                }
                
                return result.stdout
                
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