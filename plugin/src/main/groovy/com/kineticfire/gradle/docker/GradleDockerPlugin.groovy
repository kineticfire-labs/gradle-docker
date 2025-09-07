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
            
            // Build task
            project.tasks.register("dockerBuild${capitalizedName}", DockerBuildTask) { task ->
                configureDockerBuildTask(task, imageSpec, dockerService, project.layout)
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
            if (imageSpec.context.present) {
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
    }
    
    private void configureCleanupHooks(Project project, Provider<DockerService> dockerService, Provider<ComposeService> composeService) {
        // BuildService implementations handle their own lifecycle cleanup automatically
        // No explicit cleanup hooks needed for configuration cache compatibility
        project.logger.debug("Service cleanup configured via BuildService lifecycle")
    }
    
    // Task configuration methods
    private void configureDockerBuildTask(task, imageSpec, dockerService, projectLayout) {
        task.group = 'docker'
        task.description = "Build Docker image: ${imageSpec.name}"
        
        // Configure service dependency - ensure configuration cache compatibility
        task.dockerService.set(dockerService)
        task.usesService(dockerService)
        
        // Configure build context - support multiple context types
        configureContextPath(task, imageSpec, projectLayout)
        
        // Configure dockerfile
        if (imageSpec.dockerfile.present) {
            if (imageSpec.contextTask.present) {
                // When using contextTask, dockerfile should be relative to the context directory
                // The Copy task should have copied the dockerfile into the context
                task.dockerfile.set(projectLayout.buildDirectory.file("docker-context/${imageSpec.name}/Dockerfile"))
            } else {
                // Traditional approach - dockerfile path is absolute
                task.dockerfile.set(imageSpec.dockerfile)
            }
        } else {
            // Default Dockerfile location
            if (imageSpec.contextTask.present) {
                task.dockerfile.set(projectLayout.buildDirectory.file("docker-context/${imageSpec.name}/Dockerfile"))
            } else {
                task.dockerfile.set(projectLayout.projectDirectory.file("Dockerfile"))
            }
        }
        
        // Configure tags - combine image name with tag to create full image references
        // Convert camelCase to kebab-case for Docker image naming convention
        def dockerImageName = imageSpec.name.replaceAll(/([A-Z])/, '-$1').toLowerCase().replaceFirst(/^-/, '')
        task.tags.set(imageSpec.tags.map { tagList ->
            tagList.collect { tag -> "${dockerImageName}:${tag}" }
        })
        
        // Configure build arguments
        task.buildArgs.set(imageSpec.buildArgs)
        
        // Configure output image ID file
        def outputDir = projectLayout.buildDirectory.dir("docker/${imageSpec.name}")
        task.imageId.set(outputDir.map { it.file('image-id.txt') })
    }
    
    /**
     * Configure the context path for Docker build task based on context type
     */
    private void configureContextPath(DockerBuildTask task, ImageSpec imageSpec, ProjectLayout projectLayout) {
        if (imageSpec.contextTask.present) {
            // Use Copy task output as context - configuration cache compatible
            def copyTaskProvider = imageSpec.contextTask
            task.dependsOn(copyTaskProvider)
            // Directly set the expected destination directory without accessing the Task object
            // This avoids configuration cache issues with Task serialization
            task.contextPath.set(projectLayout.buildDirectory.dir("docker-context/${imageSpec.name}"))
        } else if (imageSpec.context.present) {
            // Use traditional context directory
            task.contextPath.set(imageSpec.context)
        } else {
            throw new GradleException(
                "Image '${imageSpec.name}' must specify either 'context' or 'contextTask'"
            )
        }
    }
    
    private void configureDockerSaveTask(task, imageSpec, dockerService) {
        task.group = 'docker'
        task.description = "Save Docker image to file: ${imageSpec.name}"
    }
    
    private void configureDockerTagTask(task, imageSpec, dockerService) {
        task.group = 'docker'
        task.description = "Tag Docker image: ${imageSpec.name}"
        
        // Configure service dependency
        task.dockerService.set(dockerService)
        
        // Configure source image (this needs to come from the build task's output)
        // For now, use first tag as source - this will be refined when build->tag dependency is established
        if (!imageSpec.tags.get().isEmpty()) {
            task.sourceImage.set(imageSpec.tags.get().first())
        }
        
        // Configure target tags
        task.tags.set(imageSpec.tags)
    }
    
    private void configureDockerPublishTask(task, imageSpec, dockerService) {
        task.group = 'docker'
        task.description = "Publish Docker image: ${imageSpec.name}"
        
        // Configure service dependency
        task.dockerService.set(dockerService)
        
        // Configure image name - prefer source reference over build task output
        if (imageSpec.sourceRef.present) {
            task.imageName.set(imageSpec.sourceRef)
        }
        // Note: Build task wiring and dependencies are handled in configureTaskDependencies method
        
        // Configure publish targets from DSL
        if (imageSpec.publish.present) {
            def publishSpec = imageSpec.publish.get()
            task.publishTargets.set(publishSpec.to)
        }
        
        // Note: Task dependencies are configured in configureTaskDependencies method
    }
    
    private void configureComposeUpTask(task, stackSpec, composeService, jsonService) {
        task.group = 'docker compose'
        task.description = "Start Docker Compose stack: ${stackSpec.name}"
        
        // Configure service dependency
        task.composeService.set(composeService)
        
        // Configure compose files
        task.composeFiles.setFrom(stackSpec.files)
        if (stackSpec.envFiles && !stackSpec.envFiles.empty) {
            task.envFiles.setFrom(stackSpec.envFiles)
        }
        
        // Configure project and stack names with property override support
        def project = task.project
        task.projectName.set(project.provider {
            project.findProperty("compose.project.name") ?: stackSpec.projectName.getOrElse(stackSpec.name)
        })
        task.stackName.set(stackSpec.name)
    }
    
    private void configureComposeDownTask(task, stackSpec, composeService) {
        task.group = 'docker compose'
        task.description = "Stop Docker Compose stack: ${stackSpec.name}"
        
        // Configure service dependency
        task.composeService.set(composeService)
        
        // Configure project and stack names with property override support
        def project = task.project
        task.projectName.set(project.provider {
            project.findProperty("compose.project.name") ?: stackSpec.projectName.getOrElse(stackSpec.name)
        })
        task.stackName.set(stackSpec.name)
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