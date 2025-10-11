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
import com.kineticfire.gradle.docker.model.LogsConfig
import com.kineticfire.gradle.docker.service.ComposeService
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task for stopping Docker Compose stacks
 */
abstract class ComposeDownTask extends DefaultTask {
    
    ComposeDownTask() {
        group = 'docker compose'
        description = 'Stop Docker Compose stack'
    }
    
    @Internal
    abstract Property<ComposeService> getComposeService()
    
    @InputFiles
    @Optional
    abstract ConfigurableFileCollection getComposeFiles()
    
    @InputFiles
    @Optional
    abstract ConfigurableFileCollection getEnvFiles()
    
    @Input
    abstract Property<String> getProjectName()
    
    @Input
    abstract Property<String> getStackName()
    
    @TaskAction
    void composeDown() {
        def projectName = this.projectName.get()
        def stackName = this.stackName.get()

        logger.lifecycle("Stopping Docker Compose stack: {} (project: {})", stackName, projectName)

        try {
            // Capture logs before tearing down
            captureLogsIfConfigured(stackName, projectName)

            // Use compose files if provided, otherwise fall back to project name only
            if (composeFiles?.files?.size() > 0) {
                // Convert FileCollection to List<Path>
                def composeFilePaths = composeFiles.files.collect { it.toPath() }
                def envFilePaths = envFiles?.files?.collect { it.toPath() } ?: []

                // Create compose configuration
                def config = new ComposeConfig(composeFilePaths, envFilePaths, projectName, stackName, [:])

                // Stop the compose stack with specific files
                def future = composeService.get().downStack(config)
                future.get()
            } else {
                // Stop the compose stack by project name only (legacy behavior)
                def future = composeService.get().downStack(projectName)
                future.get()
            }

            logger.lifecycle("Successfully stopped compose stack '{}'", stackName)

        } catch (Exception e) {
            throw new RuntimeException("Failed to stop compose stack '${stackName}': ${e.message}", e)
        }
    }

    /**
     * Capture logs if configured
     */
    private void captureLogsIfConfigured(String stackName, String projectName) {
        def dockerOrchExt = project.extensions.findByName('dockerOrch')
        if (!dockerOrchExt) {
            return
        }

        def stackSpec = dockerOrchExt.composeStacks.findByName(stackName)
        if (!stackSpec || !stackSpec.logs.present) {
            return
        }

        def logsSpec = stackSpec.logs.get()

        logger.lifecycle("Capturing logs for stack '{}'", stackName)

        def logsConfig = new LogsConfig(
            logsSpec.services.present ? logsSpec.services.get() : [],
            logsSpec.tailLines.getOrElse(100),
            logsSpec.follow.getOrElse(false),
            null  // outputFile not used here - we handle file writing ourselves
        )

        try {
            def logsFuture = composeService.get().captureLogs(projectName, logsConfig)
            def logs = logsFuture.get()

            // Write logs to configured location
            if (logsSpec.writeTo.present) {
                def logFile = logsSpec.writeTo.get().asFile
                logFile.parentFile.mkdirs()
                logFile.text = logs
                logger.lifecycle("Logs written to: {}", logFile.absolutePath)
            } else {
                logger.info("Logs:\n{}", logs)
            }
        } catch (Exception e) {
            logger.warn("Failed to capture logs for stack '{}': {}", stackName, e.message)
            // Don't fail the task if log capture fails - just warn
        }
    }
}