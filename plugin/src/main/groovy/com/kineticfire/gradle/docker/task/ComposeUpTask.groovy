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
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
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
                    serviceInfo.state, serviceInfo.ports.collect { "${it.hostPort}:${it.containerPort}" }.join(', '))
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
        def dockerOrchExt = project.extensions.findByName('dockerOrch')
        if (!dockerOrchExt) {
            return
        }

        def stackSpec = dockerOrchExt.composeStacks.findByName(stackName)
        if (!stackSpec) {
            return
        }

        // Wait for healthy services
        if (stackSpec.waitForHealthy.present) {
            def waitSpec = stackSpec.waitForHealthy.get()
            if (waitSpec.services.present && !waitSpec.services.get().isEmpty()) {
                logger.lifecycle("Waiting for services to be HEALTHY: {}", waitSpec.services.get())

                def waitConfig = new WaitConfig(
                    projectName,
                    waitSpec.services.get(),
                    Duration.ofSeconds(waitSpec.timeoutSeconds.getOrElse(60)),
                    Duration.ofSeconds(waitSpec.pollSeconds.getOrElse(2)),
                    ServiceStatus.HEALTHY
                )

                def waitFuture = composeService.get().waitForServices(waitConfig)
                waitFuture.get()

                logger.lifecycle("All services are HEALTHY")
            }
        }

        // Wait for running services
        if (stackSpec.waitForRunning.present) {
            def waitSpec = stackSpec.waitForRunning.get()
            if (waitSpec.services.present && !waitSpec.services.get().isEmpty()) {
                logger.lifecycle("Waiting for services to be RUNNING: {}", waitSpec.services.get())

                def waitConfig = new WaitConfig(
                    projectName,
                    waitSpec.services.get(),
                    Duration.ofSeconds(waitSpec.timeoutSeconds.getOrElse(60)),
                    Duration.ofSeconds(waitSpec.pollSeconds.getOrElse(2)),
                    ServiceStatus.RUNNING
                )

                def waitFuture = composeService.get().waitForServices(waitConfig)
                waitFuture.get()

                logger.lifecycle("All services are RUNNING")
            }
        }
    }

    /**
     * Generate state file for test consumption
     */
    private void generateStateFile(String stackName, String projectName, ComposeState composeState) {
        def buildDir = project.layout.buildDirectory.get().asFile
        def stateDir = new File(buildDir, "compose-state")
        stateDir.mkdirs()

        def stateFile = new File(stateDir, "${stackName}-state.json")

        // Build state JSON
        def stateData = [
            stackName: stackName,
            projectName: projectName,
            lifecycle: "suite",
            timestamp: Instant.now().toString(),
            services: composeState.services.collectEntries { serviceName, serviceInfo ->
                [
                    (serviceName): [
                        containerId: serviceInfo.containerId,
                        containerName: serviceInfo.containerName ?: "${serviceName}-${projectName}-1",
                        state: serviceInfo.state,
                        publishedPorts: serviceInfo.ports.collect { port ->
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