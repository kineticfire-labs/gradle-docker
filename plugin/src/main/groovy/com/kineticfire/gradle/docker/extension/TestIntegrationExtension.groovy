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

package com.kineticfire.gradle.docker.extension

import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.testing.Test

import javax.inject.Inject

/**
 * Extension methods for Test task integration with Docker Compose
 */
abstract class TestIntegrationExtension {

    private final ProjectLayout layout
    private final Provider<String> projectNameProvider
    private final Logger logger
    // Store reference to DockerOrchExtension for configuration-time access
    private DockerOrchExtension dockerOrchExtension

    @Inject
    TestIntegrationExtension(String projectName, ProjectLayout layout, ProviderFactory providers) {
        this.layout = layout
        // Store project name as a provider (configuration-cache safe)
        this.projectNameProvider = providers.provider { projectName }
        this.logger = Logging.getLogger(TestIntegrationExtension)
    }
    
    /**
     * Set the DockerOrchExtension reference (called by plugin during configuration)
     */
    void setDockerOrchExtension(DockerOrchExtension extension) {
        this.dockerOrchExtension = extension
    }
    
    /**
     * Configure a test task to use Docker Compose stack with specified lifecycle
     * @param test Test task to configure
     * @param stackName Name of the compose stack to use
     * @param lifecycle When to start/stop the stack: "suite", "class", or "method"
     */
    void usesCompose(Test test, String stackName, String lifecycle) {
        logger.info("Configuring test '{}' to use compose stack '{}' with lifecycle '{}'", 
            test.name, stackName, lifecycle)
            
        // Find the compose stack configuration
        if (!dockerOrchExtension) {
            throw new IllegalStateException("dockerOrch extension not found. Apply gradle-docker plugin first.")
        }
        def dockerOrchExt = dockerOrchExtension
        
        def stackSpec = dockerOrchExt.composeStacks.findByName(stackName)
        if (!stackSpec) {
            throw new IllegalArgumentException("Compose stack '${stackName}' not found in dockerOrch configuration")
        }
        
        // Configure test task based on lifecycle
        switch (lifecycle.toLowerCase()) {
            case 'suite':
                configureSuiteLifecycle(test, stackName, stackSpec)
                break
            case 'class':
                configureClassLifecycle(test, stackName, stackSpec)
                break
            case 'method':
                configureMethodLifecycle(test, stackName, stackSpec)
                break
            default:
                throw new IllegalArgumentException("Invalid lifecycle '${lifecycle}'. Must be 'suite', 'class', or 'method'")
        }
    }
    
    /**
     * Get the path to the compose state file for a given stack
     * @param stackName Name of the compose stack
     * @return Provider of path to the state file that will contain runtime information
     */
    Provider<String> composeStateFileFor(String stackName) {
        def buildDirProvider = layout.buildDirectory
        def stateDirProvider = buildDirProvider.dir("compose-state")
        return stateDirProvider.map { dir ->
            dir.asFile.toPath().resolve("${stackName}-state.json").toString()
        }
    }
    
    private void configureSuiteLifecycle(Test test, String stackName, stackSpec) {
        def upTaskName = "composeUp${stackName.capitalize()}"
        def downTaskName = "composeDown${stackName.capitalize()}"
        
        // Add dependency on compose tasks - this is sufficient for suite lifecycle
        // The compose tasks will handle the actual Docker Compose operations
        test.dependsOn upTaskName
        test.finalizedBy downTaskName
        
        // Note: Removed doFirst/doLast logging to avoid configuration cache issues
        // The compose tasks themselves handle logging of start/stop operations
    }
    
    private void configureClassLifecycle(Test test, String stackName, stackSpec) {
        // Class lifecycle uses JUnit extension to manage compose per test class
        // DO NOT add task dependencies - let JUnit extension handle lifecycle

        // Set system properties for JUnit extension to use
        test.systemProperty("docker.compose.stack", stackName)
        test.systemProperty("docker.compose.lifecycle", "class")
        test.systemProperty("docker.compose.project", projectNameProvider)

        logger.info("Test '{}' configured for per-class compose lifecycle using JUnit extension", test.name)
        logger.info("Test class must use @ExtendWith(DockerComposeClassExtension.class)")
    }
    
    private void configureMethodLifecycle(Test test, String stackName, stackSpec) {
        // Method lifecycle uses JUnit extension to manage compose per test method
        // DO NOT add task dependencies - let JUnit extension handle lifecycle

        // Set system properties for JUnit extension to use
        test.systemProperty("docker.compose.stack", stackName)
        test.systemProperty("docker.compose.lifecycle", "method")
        test.systemProperty("docker.compose.project", projectNameProvider)

        logger.info("Test '{}' configured for per-method compose lifecycle using JUnit extension", test.name)
        logger.info("Test class must use @ExtendWith(DockerComposeMethodExtension.class)")
    }
}