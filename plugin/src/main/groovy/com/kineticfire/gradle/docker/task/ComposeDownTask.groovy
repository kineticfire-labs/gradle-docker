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
}