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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/**
 * Plugin that provides Docker registry container management for integration testing.
 * 
 * This plugin adds tasks and utilities for managing test Docker registries:
 * - Starting local registries for integration tests
 * - Stopping and cleaning up registries after tests
 * - Emergency cleanup for orphaned containers
 * - Registry configuration with authentication support
 */
class RegistryManagementPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // Make plugin idempotent - check if already applied
        if (project.extensions.findByName('registryManagement') != null) {
            // Plugin already applied, skip initialization
            return
        }
        
        // Create the shared registry fixture
        def registryFixture = new RegistryTestFixture()
        
        // Create extension for registry configuration
        def extension = project.extensions.create('registryManagement', RegistryManagementExtension, project)
        
        // Register start task
        def startTask = project.tasks.register('startTestRegistries', DockerRegistryStartTask) {
            it.registryFixture.set(registryFixture)
            it.registryConfigs.set(extension.registryConfigs)
            it.group = 'docker registry'
            it.description = 'Start Docker registries for integration testing'
        }
        
        // Register stop task
        def stopTask = project.tasks.register('stopTestRegistries', DockerRegistryStopTask) {
            it.registryFixture.set(registryFixture)
            it.group = 'docker registry'
            it.description = 'Stop Docker registries after integration testing'
        }
        
        // Register cleanup task
        def cleanupTask = project.tasks.register('cleanupTestRegistries', DockerRegistryCleanupTask) {
            it.registryFixture.set(registryFixture)
            it.group = 'docker registry'
            it.description = 'Emergency cleanup of orphaned Docker registries'
        }
        
        // Configure task dependencies using lazy wiring (Gradle 9/10 best practice)
        // Wire stopTestRegistries to run after test tasks without using afterEvaluate
        project.tasks.withType(Test).configureEach { testTask ->
            // Only add mustRunAfter for standard test task names to avoid over-constraining the build
            if (testTask.name in ['test', 'functionalTest', 'integrationTest']) {
                // Get the realized task and add ordering constraint
                // stopTask should run AFTER testTask
                stopTask.get().mustRunAfter(testTask)
            }
        }
        
        // Add convenience methods to project
        project.ext.createRegistryConfig = { String name, int port ->
            return new RegistryTestFixture.RegistryConfig(name, port)
        }
        
        project.ext.addRegistryConfig = { RegistryTestFixture.RegistryConfig config ->
            extension.registryConfigs.add(config)
        }
        
        project.ext.withTestRegistry = { String name, int port, Closure closure ->
            def config = new RegistryTestFixture.RegistryConfig(name, port)
            extension.registryConfigs.add(config)
            
            // Execute the closure with the registry config for further customization
            closure.delegate = config
            closure.call(config)
        }
        
        project.ext.withAuthenticatedRegistry = { String name, int port, String username, String password, Closure closure = null ->
            def config = new RegistryTestFixture.RegistryConfig(name, port)
                .withAuth(username, password)
            extension.registryConfigs.add(config)
            
            if (closure) {
                closure.delegate = config
                closure.call(config)
            }
        }
    }
}