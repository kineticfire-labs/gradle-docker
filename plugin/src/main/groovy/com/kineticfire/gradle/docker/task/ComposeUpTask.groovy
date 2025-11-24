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

package com.kineticfire.gradle.docker.task

import com.kineticfire.gradle.docker.model.ComposeConfig
import com.kineticfire.gradle.docker.model.ComposeState
import com.kineticfire.gradle.docker.model.ServiceStatus
import com.kineticfire.gradle.docker.model.WaitConfig
import com.kineticfire.gradle.docker.service.ComposeService
import groovy.json.JsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.time.Duration
import java.time.Instant

/**
 * Task for starting Docker Compose stacks
 */
abstract class ComposeUpTask extends DefaultTask {
    
    ComposeUpTask() {
        group = 'docker compose'
        description = 'Start Docker Compose stack'
    }
    
    @Internal
    abstract Property<ComposeService> getComposeService()
    
    @InputFiles
    abstract ConfigurableFileCollection getComposeFiles()
    
    @InputFiles
    @Optional
    abstract ConfigurableFileCollection getEnvFiles()
    
    @Input
    abstract Property<String> getProjectName()
    
    @Input
    abstract Property<String> getStackName()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @Input
    @Optional
    abstract ListProperty<String> getWaitForHealthyServices()

    @Input
    @Optional
    abstract Property<Integer> getWaitForHealthyTimeoutSeconds()

    @Input
    @Optional
    abstract Property<Integer> getWaitForHealthyPollSeconds()

    @Input
    @Optional
    abstract ListProperty<String> getWaitForRunningServices()

    @Input
    @Optional
    abstract Property<Integer> getWaitForRunningTimeoutSeconds()

    @Input
    @Optional
    abstract Property<Integer> getWaitForRunningPollSeconds()

    @TaskAction
    void composeUp() {
        def projectName = this.projectName.get()
        def stackName = this.stackName.get()

        logger.lifecycle("Starting Docker Compose stack: {} (project: {})", stackName, projectName)

        // Convert FileCollection to List<Path>
        def composeFilePaths = composeFiles.files.collect { it.toPath() }
        def envFilePaths = envFiles?.files?.collect { it.toPath() } ?: []

        // Create compose configuration
        def config = new ComposeConfig(composeFilePaths, envFilePaths, projectName, stackName, [:])

        try {
            // Start the compose stack
            def future = composeService.get().upStack(config)
            def composeState = future.get()

            logger.lifecycle("Successfully started compose stack '{}' with {} services",
                stackName, composeState.services.size())

            composeState.services.each { serviceName, serviceInfo ->
                logger.info("  Service '{}': {} ({})", serviceName,
                    serviceInfo.state, serviceInfo.publishedPorts.collect { "${it.hostPort}:${it.containerPort}" }.join(', '))
            }

            // Wait for services to be ready
            performWaitIfConfigured(stackName, projectName)

            // Generate state file for test consumption
            generateStateFile(stackName, projectName, composeState)

        } catch (Exception e) {
            throw new RuntimeException("Failed to start compose stack '${stackName}': ${e.message}", e)
        }
    }

    /**
     * Wait for services to reach desired state if configured
     */
    private void performWaitIfConfigured(String stackName, String projectName) {
        // Wait for healthy services (configured during configuration phase)
        if (waitForHealthyServices.isPresent() && !waitForHealthyServices.get().isEmpty()) {
            def services = waitForHealthyServices.get()
            def timeoutSeconds = waitForHealthyTimeoutSeconds.getOrElse(60)
            def pollSeconds = waitForHealthyPollSeconds.getOrElse(2)

            logger.lifecycle("Waiting for services to be HEALTHY: {}", services)

            def waitConfig = new WaitConfig(
                projectName,
                services,
                Duration.ofSeconds(timeoutSeconds),
                Duration.ofSeconds(pollSeconds),
                ServiceStatus.HEALTHY
            )

            def waitFuture = composeService.get().waitForServices(waitConfig)
            waitFuture.get()

            logger.lifecycle("All services are HEALTHY")
        }

        // Wait for running services (configured during configuration phase)
        if (waitForRunningServices.isPresent() && !waitForRunningServices.get().isEmpty()) {
            def services = waitForRunningServices.get()
            def timeoutSeconds = waitForRunningTimeoutSeconds.getOrElse(60)
            def pollSeconds = waitForRunningPollSeconds.getOrElse(2)

            logger.lifecycle("Waiting for services to be RUNNING: {}", services)

            def waitConfig = new WaitConfig(
                projectName,
                services,
                Duration.ofSeconds(timeoutSeconds),
                Duration.ofSeconds(pollSeconds),
                ServiceStatus.RUNNING
            )

            def waitFuture = composeService.get().waitForServices(waitConfig)
            waitFuture.get()

            logger.lifecycle("All services are RUNNING")
        }
    }

    /**
     * Generate state file for test consumption
     */
    private void generateStateFile(String stackName, String projectName, ComposeState composeState) {
        def stateDir = outputDirectory.get().asFile
        stateDir.mkdirs()

        def stateFile = new File(stateDir, "${stackName}-state.json")

        // Build state JSON
        def stateData = [
            stackName: stackName,
            projectName: projectName,
            lifecycle: "class",
            timestamp: Instant.now().toString(),
            services: composeState.services.collectEntries { serviceName, serviceInfo ->
                [
                    (serviceName): [
                        containerId: serviceInfo.containerId,
                        containerName: serviceInfo.containerName ?: "${serviceName}-${projectName}-1",
                        state: serviceInfo.state,
                        publishedPorts: serviceInfo.publishedPorts.collect { port ->
                            [
                                container: port.containerPort,
                                host: port.hostPort,
                                protocol: port.protocol ?: "tcp"
                            ]
                        }
                    ]
                ]
            }
        ]
        def stateJson = new JsonBuilder(stateData)

        stateFile.text = stateJson.toPrettyString()

        logger.lifecycle("State file generated: {}", stateFile.absolutePath)
    }
}