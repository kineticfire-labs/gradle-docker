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

package com.kineticfire.gradle.docker.spock

import com.kineticfire.gradle.docker.junit.service.*
import com.kineticfire.gradle.docker.model.*
import com.kineticfire.gradle.docker.service.ComposeService
import groovy.json.JsonBuilder
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Interceptor for CLASS lifecycle Docker Compose orchestration.
 *
 * <p>Manages containers at the class level:</p>
 * <ul>
 *   <li>Starts containers before first test (setupSpec)</li>
 *   <li>All tests share the same containers</li>
 *   <li>Stops containers after last test (cleanupSpec)</li>
 * </ul>
 *
 * @see ComposeUp
 * @see LifecycleMode#CLASS
 * @since 1.0.0
 */
class ComposeClassInterceptor implements IMethodInterceptor {

    private static final String COMPOSE_STATE_FILE_PROPERTY = "COMPOSE_STATE_FILE"
    private static final String COMPOSE_PROJECT_PROPERTY = "COMPOSE_PROJECT_NAME"

    private final Map<String, Object> config
    private final ComposeService composeService
    private final ProcessExecutor processExecutor
    private final FileService fileService
    private final SystemPropertyService systemPropertyService
    private final TimeService timeService

    // Thread-local storage for project name and state
    private final ThreadLocal<String> uniqueProjectName = new ThreadLocal<>()
    private final ThreadLocal<ComposeState> composeState = new ThreadLocal<>()
    private boolean isSetup = false

    ComposeClassInterceptor(Map<String, Object> config,
                           ComposeService composeService,
                           ProcessExecutor processExecutor,
                           FileService fileService,
                           SystemPropertyService systemPropertyService,
                           TimeService timeService) {
        this.config = config
        this.composeService = composeService
        this.processExecutor = processExecutor
        this.fileService = fileService
        this.systemPropertyService = systemPropertyService
        this.timeService = timeService
    }

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        if (invocation.method.kind.name() == 'SETUP_SPEC') {
            handleSetupSpec(invocation)
        } else if (invocation.method.kind.name() == 'CLEANUP_SPEC') {
            handleCleanupSpec(invocation)
        } else {
            invocation.proceed()
        }
    }

    private void handleSetupSpec(IMethodInvocation invocation) {
        String stackName = config.stackName as String
        String className = config.className as String
        String projectNameBase = config.projectNameBase as String

        // Generate unique project name
        String projectName = DockerComposeSpockExtension.generateUniqueProjectName(
            projectNameBase,
            className
        )
        uniqueProjectName.set(projectName)

        println "=== Starting Docker Compose stack '${stackName}' for class: ${className} (project: ${projectName}) ==="

        try {
            // Clean up any leftover containers first
            cleanupExistingContainers(projectName)

            // Start compose stack
            startComposeStack(stackName, projectName)

            // Wait for services
            waitForServices(stackName, projectName)

            // Generate state file
            generateStateFile(stackName, projectName, className)

            isSetup = true

            // Proceed with setupSpec method
            invocation.proceed()

        } catch (Exception e) {
            System.err.println("Startup failed, attempting cleanup...")
            try {
                cleanupExistingContainers(projectName)
                stopComposeStack(stackName, projectName)
            } catch (Exception cleanupException) {
                System.err.println("Warning: Cleanup after startup failure also failed: ${cleanupException.message}")
            }
            throw e
        }
    }

    private void handleCleanupSpec(IMethodInvocation invocation) {
        String stackName = config.stackName as String
        String storedProjectName = uniqueProjectName.get()

        if (storedProjectName == null) {
            // Fallback if somehow the project name wasn't stored
            storedProjectName = DockerComposeSpockExtension.generateUniqueProjectName(
                config.projectNameBase as String,
                config.className as String
            )
        }

        println "=== Stopping Docker Compose stack '${stackName}' after class (project: ${storedProjectName}) ==="

        // Proceed with cleanupSpec method first
        try {
            invocation.proceed()
        } catch (Exception e) {
            System.err.println("Warning: cleanupSpec method failed: ${e.message}")
        }

        // Always attempt cleanup, regardless of previous failures
        Exception lastException = null

        try {
            stopComposeStack(stackName, storedProjectName)
        } catch (Exception e) {
            System.err.println("Warning: Compose down failed for ${stackName}: ${e.message}")
            lastException = e
        }

        try {
            cleanupExistingContainers(storedProjectName)
        } catch (Exception e) {
            System.err.println("Warning: Container cleanup failed for ${storedProjectName}: ${e.message}")
            if (lastException == null) lastException = e
        }

        try {
            forceRemoveContainersByName(storedProjectName)
        } catch (Exception e) {
            System.err.println("Warning: Force cleanup failed for ${storedProjectName}: ${e.message}")
            if (lastException == null) lastException = e
        }

        // Clear ThreadLocals
        uniqueProjectName.remove()
        composeState.remove()

        if (lastException != null) {
            System.err.println("Warning: Some cleanup operations failed but test execution will continue")
        }
    }

    private void cleanupExistingContainers(String uniqueProjectName) throws IOException, InterruptedException {
        println "Cleaning up existing containers for project: ${uniqueProjectName}"

        // Force stop and remove any containers with the project name
        try {
            def result = processExecutor.executeWithTimeout(15, TimeUnit.SECONDS,
                "bash", "-c",
                "docker ps -aq --filter name=${uniqueProjectName} | xargs -r docker rm -f")
        } catch (Exception e) {
            System.err.println("Warning: Force container cleanup failed: ${e.message}")
        }

        // Stop and remove containers by compose project label
        try {
            processExecutor.execute(
                "docker", "container", "prune", "-f",
                "--filter", "label=com.docker.compose.project=${uniqueProjectName}"
            )
        } catch (Exception e) {
            System.err.println("Warning: Container prune failed: ${e.message}")
        }
    }

    private void startComposeStack(String stackName, String uniqueProjectName) throws Exception {
        Path composeFile = fileService.resolve(config.composeFile as String).toAbsolutePath()
        if (!fileService.exists(composeFile)) {
            throw new IllegalStateException("Compose file not found: ${composeFile}")
        }

        // Create ComposeConfig
        def composeConfig = new ComposeConfig(
            Collections.singletonList(composeFile),
            uniqueProjectName,
            stackName
        )

        // Start the stack and store the state
        ComposeState state = composeService.upStack(composeConfig).get()
        composeState.set(state)

        println "Successfully started compose stack '${stackName}' with project '${uniqueProjectName}'"
    }

    private void stopComposeStack(String stackName, String uniqueProjectName) throws Exception {
        try {
            composeService.downStack(uniqueProjectName).get()
            println "Successfully stopped compose stack '${stackName}' with project '${uniqueProjectName}'"
        } catch (Exception e) {
            System.err.println("Warning: Failed to cleanly stop compose stack '${stackName}': ${e.message}")
        }
    }

    private void waitForServices(String stackName, String uniqueProjectName) throws Exception {
        List<String> healthyServices = config.waitForHealthy as List<String>
        List<String> runningServices = config.waitForRunning as List<String>
        int timeoutSeconds = config.timeoutSeconds as int
        int pollSeconds = config.pollSeconds as int

        // Wait for healthy services
        if (healthyServices && !healthyServices.isEmpty()) {
            println "Waiting for services to be HEALTHY: ${healthyServices}"

            def waitConfig = new WaitConfig(
                uniqueProjectName,
                healthyServices,
                Duration.ofSeconds(timeoutSeconds),
                Duration.ofSeconds(pollSeconds),
                ServiceStatus.HEALTHY
            )

            try {
                composeService.waitForServices(waitConfig).get()
                println "All services are HEALTHY for stack '${stackName}'"
            } catch (Exception e) {
                System.err.println("Warning: Service health check did not pass within timeout: ${e.message}")
            }
        }

        // Wait for running services
        if (runningServices && !runningServices.isEmpty()) {
            println "Waiting for services to be RUNNING: ${runningServices}"

            def waitConfig = new WaitConfig(
                uniqueProjectName,
                runningServices,
                Duration.ofSeconds(timeoutSeconds),
                Duration.ofSeconds(pollSeconds),
                ServiceStatus.RUNNING
            )

            try {
                composeService.waitForServices(waitConfig).get()
                println "All services are RUNNING for stack '${stackName}'"
            } catch (Exception e) {
                System.err.println("Warning: Service running check did not pass within timeout: ${e.message}")
            }
        }
    }

    private void generateStateFile(String stackName, String uniqueProjectName, String className) throws IOException {
        Path buildDir = fileService.resolve("build")
        Path stateDir = buildDir.resolve("compose-state")
        fileService.createDirectories(stateDir)

        Path stateFile = stateDir.resolve("${stackName}-${className}-state.json")

        // Get the stored state from upStack
        ComposeState state = composeState.get()
        if (state == null) {
            System.err.println("Warning: No ComposeState available for state file generation")
            return
        }

        // Build state data
        def stateData = [
            stackName: stackName,
            projectName: uniqueProjectName,
            lifecycle: "class",
            testClass: className,
            timestamp: timeService.now().toString(),
            services: [:]
        ]

        state.services.each { serviceName, serviceInfo ->
            stateData.services[serviceName] = [
                containerId: serviceInfo.containerId,
                containerName: serviceInfo.containerName,
                state: serviceInfo.state,
                publishedPorts: serviceInfo.publishedPorts.collect { port ->
                    [
                        container: port.containerPort,
                        host: port.hostPort,
                        protocol: port.protocol ?: "tcp"
                    ]
                }
            ]
        }

        // Write JSON
        def json = new JsonBuilder(stateData)
        fileService.writeString(stateFile, json.toPrettyString())

        // Set system property
        systemPropertyService.setProperty(COMPOSE_STATE_FILE_PROPERTY, stateFile.toString())
        systemPropertyService.setProperty(COMPOSE_PROJECT_PROPERTY, uniqueProjectName)

        println "State file generated: ${stateFile}"
    }

    private void forceRemoveContainersByName(String uniqueProjectName) throws IOException, InterruptedException {
        println "Force removing containers containing: ${uniqueProjectName}"

        // Remove by name pattern
        try {
            def listResult = processExecutor.execute(
                "docker", "ps", "-aq", "--filter", "name=${uniqueProjectName}"
            )

            String containerIds = listResult.output
            if (containerIds && !containerIds.trim().isEmpty()) {
                println "Found containers to remove: ${containerIds.trim().replace('\n', ', ')}"

                containerIds.trim().split("\n").each { containerId ->
                    if (containerId && !containerId.trim().isEmpty()) {
                        processExecutor.execute("docker", "rm", "-f", containerId.trim())
                        println "Successfully removed container: ${containerId.trim()}"
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in name-based cleanup: ${e.message}")
        }

        // Remove by compose project label
        try {
            def labelListResult = processExecutor.execute(
                "docker", "ps", "-aq", "--filter",
                "label=com.docker.compose.project=${uniqueProjectName}"
            )

            String labelContainerIds = labelListResult.output
            if (labelContainerIds && !labelContainerIds.trim().isEmpty()) {
                println "Found containers by compose label: ${labelContainerIds.trim().replace('\n', ', ')}"

                labelContainerIds.trim().split("\n").each { containerId ->
                    if (containerId && !containerId.trim().isEmpty()) {
                        processExecutor.execute("docker", "rm", "-f", containerId.trim())
                        println "Successfully removed container by label: ${containerId.trim()}"
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in label-based cleanup: ${e.message}")
        }
    }
}
