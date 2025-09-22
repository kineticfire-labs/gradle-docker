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

package com.kineticfire.gradle.docker

import com.kineticfire.gradle.docker.extension.DockerExtension
import com.kineticfire.gradle.docker.model.CompressionType
import com.kineticfire.gradle.docker.extension.DockerOrchExtension
import com.kineticfire.gradle.docker.extension.TestIntegrationExtension
import com.kineticfire.gradle.docker.service.*
import com.kineticfire.gradle.docker.task.*
import com.kineticfire.gradle.docker.spec.ImageSpec
import org.gradle.api.tasks.Copy
import org.gradle.api.file.ProjectLayout
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.util.GradleVersion
import org.gradle.api.JavaVersion

/**
 * Main plugin class for gradle-docker plugin
 */
class GradleDockerPlugin implements Plugin<Project> {
    
    @Override
    void apply(Project project) {
        // Validate minimum requirements
        validateRequirements(project)
        
        // Register shared services
        def dockerService = registerDockerService(project)
        def composeService = registerComposeService(project) 
        def jsonService = registerJsonService(project)
        
        // Create extensions
        def dockerExt = project.extensions.create('docker', DockerExtension, project.objects, project)
        def dockerOrchExt = project.extensions.create('dockerOrch', DockerOrchExtension, project.objects, project)
        
        // Register task creation rules
        registerTaskCreationRules(project, dockerExt, dockerOrchExt, dockerService, composeService, jsonService)
        
        // Configure validation and dependency resolution
        configureAfterEvaluation(project, dockerExt, dockerOrchExt)
        
        // Setup cleanup hooks
        configureCleanupHooks(project, dockerService, composeService)
        
        // Setup test integration extension methods
        setupTestIntegration(project)
    }
    
    private void validateRequirements(Project project) {
        // Validate Java version (warn only in test environments)
        def javaVersion = JavaVersion.current()
        if (javaVersion < JavaVersion.VERSION_21) {
            def message = "gradle-docker plugin requires Java 21 or higher. Current version: ${javaVersion}"
            if (isTestEnvironment()) {
                project.logger.warn(message)
            } else {
                throw new GradleException(
                    message + "\nSuggestion: Update your Java installation to version 21 or higher"
                )
            }
        }
        
        // Validate Gradle version (warn only in test environments)  
        def gradleVersion = project.gradle.gradleVersion
        if (GradleVersion.version(gradleVersion) < GradleVersion.version("9.0.0")) {
            def message = "gradle-docker plugin requires Gradle 9.0.0 or higher. Current version: ${gradleVersion}"
            if (isTestEnvironment()) {
                project.logger.warn(message)
            } else {
                throw new GradleException(
                    message + "\nSuggestion: Update your Gradle wrapper to version 9.0.0 or higher"
                )
            }
        }
        
        project.logger.info("gradle-docker plugin applied successfully (Java ${javaVersion}, Gradle ${gradleVersion})")
    }
    
    private boolean isTestEnvironment() {
        // Detect if running in test environment
        return System.getProperty("gradle.test.running") == "true" || 
               Thread.currentThread().stackTrace.any { 
                   it.className.contains("spock") || it.className.contains("Test") 
               }
    }
    
    private Provider<DockerService> registerDockerService(Project project) {
        return project.gradle.sharedServices.registerIfAbsent('dockerService', DockerServiceImpl) {
            // No parameters needed - service uses BuildServiceParameters.None
        }
    }
    
    private Provider<ComposeService> registerComposeService(Project project) {
        return project.gradle.sharedServices.registerIfAbsent('composeService', ExecLibraryComposeService) {
            // No parameters needed - service uses BuildServiceParameters.None
        }
    }
    
    private Provider<JsonService> registerJsonService(Project project) {
        return project.gradle.sharedServices.registerIfAbsent('jsonService', JsonServiceImpl) {
            // No parameters needed - service uses BuildServiceParameters.None
        }
    }
    
    private void registerTaskCreationRules(Project project, DockerExtension dockerExt, DockerOrchExtension dockerOrchExt,
                                         Provider<DockerService> dockerService, Provider<ComposeService> composeService, 
                                         Provider<JsonService> jsonService) {
        
        // Register aggregate tasks first
        registerAggregateTasks(project)
        
        // Register per-image tasks after evaluation
        project.afterEvaluate {
            registerDockerImageTasks(project, dockerExt, dockerService)
            registerComposeStackTasks(project, dockerOrchExt, composeService, jsonService)
            

        }
        

    }
    
    private void registerAggregateTasks(Project project) {
        project.tasks.register('dockerBuild') {
            group = 'docker'
            description = 'Build all configured Docker images'
            // Dependencies will be configured after evaluation
        }
        
        project.tasks.register('dockerSave') {
            group = 'docker'
            description = 'Save all configured Docker images to files'
        }
        
        project.tasks.register('dockerTag') {
            group = 'docker'
            description = 'Tag all configured Docker images'
        }
        
        project.tasks.register('dockerPublish') {
            group = 'docker'
            description = 'Publish all configured Docker images to registries'
        }
        
        project.tasks.register('dockerImages') {
            group = 'docker'
            description = 'Run all configured Docker operations for all images'
        }
        
        project.tasks.register('composeUp') {
            group = 'docker compose'
            description = 'Start all configured Docker Compose stacks'
        }
        
        project.tasks.register('composeDown') {
            group = 'docker compose'
            description = 'Stop all configured Docker Compose stacks'
        }
    }
    
    private void registerDockerImageTasks(Project project, DockerExtension dockerExt, Provider<DockerService> dockerService) {
        dockerExt.images.all { imageSpec ->
            def imageName = imageSpec.name
            def capitalizedName = imageName.capitalize()
            
            // Validate that tags are specified for build tasks - avoid calling .get() which forces early evaluation
            if (!imageSpec.tags.present || imageSpec.tags.getOrElse([]).empty) {
                throw new GradleException(
                    "Image '${imageSpec.name}' must specify at least one tag\n" +
                    "Example: tags = ['latest', 'v1.0.0']"
                )
            }
            
            // Build task
            project.tasks.register("dockerBuild${capitalizedName}", DockerBuildTask) { task ->
                configureDockerBuildTask(task, imageSpec, dockerService, project)
            }
            
            // Save task
            if (imageSpec.save.present) {
                project.tasks.register("dockerSave${capitalizedName}", DockerSaveTask) { task ->
                    configureDockerSaveTask(task, imageSpec, dockerService)
                }
            }
            
            // Tag task
            project.tasks.register("dockerTag${capitalizedName}", DockerTagTask) { task ->
                configureDockerTagTask(task, imageSpec, dockerService)
            }
            
            // Publish task
            if (imageSpec.publish.present) {
                project.tasks.register("dockerPublish${capitalizedName}", DockerPublishTask) { task ->
                    configureDockerPublishTask(task, imageSpec, dockerService)
                }
            }
            
            // Per-image aggregate task - runs all configured operations for this image
            project.tasks.register("dockerImage${capitalizedName}") { task ->
                task.group = 'docker'
                task.description = "Run all configured Docker operations for image: ${imageSpec.name}"
                
                // Always depend on build and tag
                task.dependsOn("dockerBuild${capitalizedName}")
                task.dependsOn("dockerTag${capitalizedName}")
                
                // Conditionally depend on save and publish
                if (imageSpec.save.present) {
                    task.dependsOn("dockerSave${capitalizedName}")
                }
                if (imageSpec.publish.present) {
                    task.dependsOn("dockerPublish${capitalizedName}")
                }
            }
        }
    }
    
    private void registerComposeStackTasks(Project project, DockerOrchExtension dockerOrchExt, 
                                         Provider<ComposeService> composeService, Provider<JsonService> jsonService) {
        dockerOrchExt.composeStacks.all { stackSpec ->
            def stackName = stackSpec.name
            def capitalizedName = stackName.capitalize()
            
            // Up task
            project.tasks.register("composeUp${capitalizedName}", ComposeUpTask) { task ->
                configureComposeUpTask(task, stackSpec, composeService, jsonService)
            }
            
            // Down task  
            project.tasks.register("composeDown${capitalizedName}", ComposeDownTask) { task ->
                configureComposeDownTask(task, stackSpec, composeService)
            }
        }
    }
    
    private void configureAfterEvaluation(Project project, DockerExtension dockerExt, DockerOrchExtension dockerOrchExt) {
        project.afterEvaluate {
            try {
                // Validate configurations
                dockerExt.validate()
                dockerOrchExt.validate()
                
                // Configure task dependencies
                configureTaskDependencies(project, dockerExt, dockerOrchExt)
                
                project.logger.info("gradle-docker plugin configuration completed successfully")
                
            } catch (Exception e) {
                throw new GradleException("gradle-docker plugin configuration failed: ${e.message}", e)
            }
        }
    }
    
    private void configureTaskDependencies(Project project, DockerExtension dockerExt, DockerOrchExtension dockerOrchExt) {
        // Configure per-image task dependencies
        dockerExt.images.all { imageSpec ->
            def imageName = imageSpec.name
            def capitalizedName = imageName.capitalize()
            def buildTaskName = "dockerBuild${capitalizedName}"
            
            // If image has build context, save/publish depend on build
            def hasBuildContext = imageSpec.context.present || imageSpec.contextTask != null
            if (hasBuildContext) {
                project.tasks.findByName("dockerSave${capitalizedName}")?.dependsOn(buildTaskName)
                project.tasks.findByName("dockerPublish${capitalizedName}")?.dependsOn(buildTaskName)
            }
        }
        
        // Configure aggregate task dependencies
        project.tasks.named('dockerBuild') {
            dependsOn dockerExt.images.names.collect { "dockerBuild${it.capitalize()}" }
        }
        
        project.tasks.named('dockerSave') {
            def saveTaskNames = dockerExt.images.findAll { it.save.present }.collect { "dockerSave${it.name.capitalize()}" }
            if (saveTaskNames) {
                dependsOn saveTaskNames
            }
        }
        
        project.tasks.named('dockerPublish') {
            def publishTaskNames = dockerExt.images.findAll { it.publish.present }.collect { "dockerPublish${it.name.capitalize()}" }
            if (publishTaskNames) {
                dependsOn publishTaskNames
            }
        }
        
        // Configure compose aggregate dependencies
        project.tasks.named('composeUp') {
            dependsOn dockerOrchExt.composeStacks.names.collect { "composeUp${it.capitalize()}" }
        }
        
        project.tasks.named('composeDown') {
            dependsOn dockerOrchExt.composeStacks.names.collect { "composeDown${it.capitalize()}" }
        }
        
        // Configure global dockerImages aggregate task dependencies
        project.tasks.named('dockerImages') {
            def imageTaskNames = dockerExt.images.names.collect { "dockerImage${it.capitalize()}" }
            if (imageTaskNames) {
                dependsOn imageTaskNames
            }
        }
    }
    
    private void configureCleanupHooks(Project project, Provider<DockerService> dockerService, Provider<ComposeService> composeService) {
        // BuildService implementations handle their own lifecycle cleanup automatically
        // No explicit cleanup hooks needed for configuration cache compatibility
        project.logger.debug("Service cleanup configured via BuildService lifecycle")
    }
    
    // Task configuration methods
    private void configureDockerBuildTask(task, imageSpec, dockerService, project) {
        task.group = 'docker'
        task.description = "Build Docker image: ${imageSpec.name}"
        
        // Configure service dependency - ensure configuration cache compatibility
        task.dockerService.set(dockerService)
        task.usesService(dockerService)
        
        // Configure Docker nomenclature properties
        task.registry.set(imageSpec.registry)
        task.namespace.set(imageSpec.namespace)
        task.imageName.set(imageSpec.imageName)
        task.repository.set(imageSpec.repository)
        task.version.set(imageSpec.version)
        task.tags.set(imageSpec.tags)
        task.labels.set(imageSpec.labels)
        
        // Configure build context - support multiple context types
        configureContextPath(task, imageSpec, project)
        
        // Configure dockerfile - support dockerfile, dockerfileName, or default
        configureDockerfile(task, imageSpec, project)
        
        // Configure build arguments
        task.buildArgs.set(imageSpec.buildArgs)
        
        // Configure output image ID file
        def outputDir = project.layout.buildDirectory.dir("docker/${imageSpec.name}")
        task.imageId.set(outputDir.map { it.file('image-id.txt') })
    }
    
    /**
     * Configure the context path for Docker build task based on context type
     */
    private void configureContextPath(DockerBuildTask task, ImageSpec imageSpec, project) {
        // Capture project layout to avoid configuration cache issues
        def buildDirectory = project.layout.buildDirectory
        
        if (imageSpec.contextTask != null) {
            // Use Copy task output as context - configuration cache compatible
            // Use the TaskProvider directly for dependsOn to avoid Task serialization
            task.dependsOn(imageSpec.contextTask)
            // Directly set the expected destination directory without accessing the Task object
            // This avoids configuration cache issues with Task serialization
            task.contextPath.set(buildDirectory.dir("docker-context/${imageSpec.name}"))
        } else if (imageSpec.context.present) {
            // Use traditional context directory
            task.contextPath.set(imageSpec.context)
        } else {
            throw new GradleException(
                "Image '${imageSpec.name}' must specify either 'context' or 'contextTask'"
            )
        }
    }
    
    /**
     * Configure dockerfile path for Docker build task
     */
    private void configureDockerfile(DockerBuildTask task, ImageSpec imageSpec, project) {
        // Validate mutually exclusive dockerfile configuration
        if (imageSpec.dockerfile.present && imageSpec.dockerfileName.present) {
            throw new GradleException(
                "Image '${imageSpec.name}' cannot specify both 'dockerfile' and 'dockerfileName'. " +
                "Use 'dockerfile' for custom paths or 'dockerfileName' for custom names in default locations."
            )
        }
        
        // Capture project providers to avoid configuration cache issues
        def providers = project.providers
        def buildDirectory = project.layout.buildDirectory
        
        if (imageSpec.dockerfile.present) {
            // When dockerfile is explicitly set, always use it regardless of contextTask
            task.dockerfile.set(imageSpec.dockerfile)
        } else {
            // Handle dockerfileName or default dockerfile resolution using providers
            if (imageSpec.contextTask != null) {
                // Use contextTask directory with custom or default filename
                def dockerfileNameProvider = imageSpec.dockerfileName.present 
                    ? imageSpec.dockerfileName
                    : providers.provider { "Dockerfile" }
                task.dockerfile.set(buildDirectory.dir("docker-context/${imageSpec.name}")
                    .map { contextDir -> contextDir.file(dockerfileNameProvider.get()) })
            } else if (imageSpec.context.present) {
                // Use traditional context directory with custom or default filename
                def dockerfileNameProvider = imageSpec.dockerfileName.present 
                    ? imageSpec.dockerfileName
                    : providers.provider { "Dockerfile" }
                task.dockerfile.set(imageSpec.context.file(dockerfileNameProvider))
            } else {
                throw new GradleException(
                    "Image '${imageSpec.name}' must specify either 'context' or 'contextTask'"
                )
            }
        }
    }
    
    private void configureDockerSaveTask(task, imageSpec, dockerService) {
        task.group = 'docker'
        task.description = "Save Docker image to file: ${imageSpec.name}"
        
        // Configure service dependency
        task.dockerService.set(dockerService)
        task.usesService(dockerService)
        
        // Configure Docker nomenclature properties
        task.registry.set(imageSpec.registry)
        task.namespace.set(imageSpec.namespace)
        task.imageName.set(imageSpec.imageName)
        task.repository.set(imageSpec.repository)
        task.version.set(imageSpec.version)
        task.tags.set(imageSpec.tags)
        
        // Configure save specification if present
        if (imageSpec.save.present) {
            def saveSpec = imageSpec.save.get()
            task.outputFile.set(saveSpec.outputFile)
            task.compression.set(saveSpec.compression)  // Now using SaveCompression enum directly
        }
    }
    
    private void configureDockerTagTask(task, imageSpec, dockerService) {
        task.group = 'docker'
        task.description = "Tag Docker image: ${imageSpec.name}"
        
        // Configure service dependency
        task.dockerService.set(dockerService)
        task.usesService(dockerService)
        
        // Configure Docker nomenclature properties
        task.registry.set(imageSpec.registry)
        task.namespace.set(imageSpec.namespace)
        task.imageName.set(imageSpec.imageName)
        task.repository.set(imageSpec.repository)
        task.version.set(imageSpec.version)
        task.tags.set(imageSpec.tags)
    }
    
    private void configureDockerPublishTask(task, imageSpec, dockerService) {
        task.group = 'docker'
        task.description = "Publish Docker image: ${imageSpec.name}"
        
        // Configure service dependency
        task.dockerService.set(dockerService)
        task.usesService(dockerService)
        
        // Configure Docker nomenclature properties
        task.registry.set(imageSpec.registry)
        task.namespace.set(imageSpec.namespace)
        task.imageName.set(imageSpec.imageName)
        task.repository.set(imageSpec.repository)
        task.version.set(imageSpec.version)
        task.tags.set(imageSpec.tags)
        
        // Configure publish targets from DSL
        if (imageSpec.publish.present) {
            def publishSpec = imageSpec.publish.get()
            task.publishTargets.set(publishSpec.to)
        }
    }
    
    private void configureComposeUpTask(task, stackSpec, composeService, jsonService) {
        task.group = 'docker compose'
        task.description = "Start Docker Compose stack: ${stackSpec.name}"
        
        // Configure service dependency
        task.composeService.set(composeService)
        
        // Configure compose files with multi-file support and backward compatibility
        // Use provider transformation to avoid configuration cache violations
        task.composeFiles.setFrom(createComposeFilesProvider(stackSpec, task.project))
        
        if (stackSpec.envFiles && !stackSpec.envFiles.empty) {
            task.envFiles.setFrom(stackSpec.envFiles)
        }
        
        // Configure project and stack names with property override support
        // Capture project name property during configuration to avoid project access during execution
        def projectNameProperty = task.project.findProperty("compose.project.name")
        task.projectName.set(task.project.provider {
            projectNameProperty ?: stackSpec.projectName.getOrElse(stackSpec.name)
        })
        task.stackName.set(stackSpec.name)
    }
    
    private void configureComposeDownTask(task, stackSpec, composeService) {
        task.group = 'docker compose'
        task.description = "Stop Docker Compose stack: ${stackSpec.name}"
        
        // Configure service dependency
        task.composeService.set(composeService)
        
        // Configure compose files - automatically use same files as ComposeUp for proper teardown
        // Use provider transformation to avoid configuration cache violations
        task.composeFiles.setFrom(createComposeFilesProvider(stackSpec, task.project))
        
        if (stackSpec.envFiles && !stackSpec.envFiles.empty) {
            task.envFiles.setFrom(stackSpec.envFiles)
        }
        
        // Configure project and stack names with property override support
        // Capture project name property during configuration to avoid project access during execution
        def projectNameProperty = task.project.findProperty("compose.project.name")
        task.projectName.set(task.project.provider {
            projectNameProperty ?: stackSpec.projectName.getOrElse(stackSpec.name)
        })
        task.stackName.set(stackSpec.name)
    }
    
    /**
     * Create a provider for compose files with multi-file support and backward compatibility.
     * Uses provider transformations to maintain configuration cache compatibility.
     */
    private Provider<List<File>> createComposeFilesProvider(stackSpec, project) {
        // Create provider that combines all possible compose file sources
        return project.provider {
            def files = []
            
            // Priority 1: Use new multi-file configuration
            if (stackSpec.composeFiles.present && !stackSpec.composeFiles.getOrElse([]).empty) {
                // Add files from composeFiles property (List<String>)
                stackSpec.composeFiles.getOrElse([]).each { path ->
                    files.add(project.file(path))
                }
            }
            
            if (!stackSpec.composeFileCollection.empty) {
                // Add files from composeFileCollection (ConfigurableFileCollection)
                files.addAll(stackSpec.composeFileCollection.files)
            }
            
            // Priority 2: Use legacy single-file configuration (backward compatibility)
            if (files.empty && stackSpec.composeFile.present) {
                files.add(stackSpec.composeFile.get().asFile)
            }
            
            // Priority 3: Use original files property (existing behavior)
            if (files.empty && !stackSpec.files.empty) {
                files.addAll(stackSpec.files.files)
            }
            
            // Validation: ensure at least one compose file is specified
            if (files.empty) {
                throw new GradleException("No compose files specified for stack '${stackSpec.name}'. " +
                    "Use 'composeFile', 'composeFiles', 'composeFileCollection', or 'files' to specify compose files.\\n" +
                    "Suggestion: Add at least one compose file to the stack configuration")
            }
            
            // Validate that specified files exist
            files.each { file ->
                if (!file.exists()) {
                    throw new GradleException("Compose file does not exist: ${file.absolutePath} for stack '${stackSpec.name}'\\n" +
                        "Suggestion: Create the compose file or update the path")
                }
            }
            
            return files
        }
    }
    
    private void setupTestIntegration(Project project) {
        def testIntegration = new TestIntegrationExtension(project)
        
        // Add extension methods to all Test tasks
        project.tasks.withType(Test) { test ->
            // Add usesCompose method
            test.ext.usesCompose = { Map args ->
                def stackName = args.stack
                def lifecycle = args.lifecycle ?: 'suite'
                testIntegration.usesCompose(test, stackName, lifecycle)
            }
            
            // Add composeStateFileFor method to project for use in build scripts
            if (!project.ext.has('composeStateFileFor')) {
                project.ext.composeStateFileFor = { String stackName ->
                    return testIntegration.composeStateFileFor(stackName)
                }
            }
        }
        
        project.logger.debug("Test integration extension methods configured")
    }


}