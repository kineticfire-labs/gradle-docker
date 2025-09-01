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
import com.kineticfire.gradle.docker.model.WaitConfig
import com.kineticfire.gradle.docker.service.ComposeService
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

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
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to start compose stack '${stackName}': ${e.message}", e)
        }
    }
}