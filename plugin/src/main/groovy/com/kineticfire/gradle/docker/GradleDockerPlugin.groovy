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
import com.kineticfire.gradle.docker.extension.DockerProjectExtension
import com.kineticfire.gradle.docker.model.CompressionType
import com.kineticfire.gradle.docker.extension.DockerTestExtension
import com.kineticfire.gradle.docker.extension.DockerWorkflowsExtension
import com.kineticfire.gradle.docker.extension.TestIntegrationExtension
import com.kineticfire.gradle.docker.service.*
import com.kineticfire.gradle.docker.task.*
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.workflow.validation.PipelineValidator
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.file.ProjectLayout
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceSet
import org.gradle.api.file.DuplicatesStrategy
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
        def taskExecutionService = registerTaskExecutionService(project)

        // Create extensions
        def dockerExt = project.extensions.create('docker', DockerExtension, project.objects, project.providers, project.layout)
        def dockerTestExt = project.extensions.create('dockerTest', DockerTestExtension, project.objects)
        dockerTestExt.configureProjectNames(project.name)
        def dockerWorkflowsExt = project.extensions.create('dockerWorkflows', DockerWorkflowsExtension, project.objects)

        // Create dockerProject extension (simplified facade)
        // Gradle injects ObjectFactory automatically via @Inject
        def dockerProjectExt = project.extensions.create(
            'dockerProject',
            DockerProjectExtension,
            project.objects
        )

        // Register task creation rules
        registerTaskCreationRules(project, dockerExt, dockerTestExt, dockerWorkflowsExt, dockerProjectExt,
                                  dockerService, composeService, jsonService)

        // Register workflow pipeline tasks
        registerWorkflowTasks(project, dockerWorkflowsExt, dockerExt, dockerTestExt,
                              dockerService, composeService, taskExecutionService)

        // Configure validation and dependency resolution
        configureAfterEvaluation(project, dockerExt, dockerTestExt, dockerWorkflowsExt)
        
        // Setup cleanup hooks
        configureCleanupHooks(project, dockerService, composeService)

        // Setup test integration extension methods
        setupTestIntegration(project)

        // Setup integration test source set conventions
        // Applied automatically when dockerTest.composeStacks is configured
        setupIntegrationTestSourceSet(project, dockerTestExt)
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

    private static boolean isTestEnvironment() {
        // Check for common test environment indicators
        return System.getProperty('gradle.test.worker') != null ||
               System.getProperty('org.gradle.test.worker') != null ||
               Thread.currentThread().getContextClassLoader().toString().contains('test') ||
               System.getProperty("gradle.test.running") == "true" ||
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

    /**
     * Register the TaskExecutionService for pipeline task execution.
     *
     * This service provides configuration-cache-compatible task lookup and execution
     * for PipelineRunTask. The service holds the TaskContainer internally.
     *
     * IMPORTANT: Each project gets its own TaskExecutionService instance by including
     * the project path in the service name. This is necessary because TaskContainer
     * is project-specific and cannot be shared across projects in a multi-project build.
     */
    private Provider<TaskExecutionService> registerTaskExecutionService(Project project) {
        // Use project path to create a unique service name per project
        // This ensures each project has its own TaskContainer reference
        def serviceName = "taskExecutionService_${project.path.replace(':', '_')}"

        def provider = project.gradle.sharedServices.registerIfAbsent(
            serviceName,
            TaskExecutionService
        ) {
            // No parameters needed - TaskContainer is set separately
        }

        // Set the TaskContainer on the service after registration
        // This is done during plugin configuration, not during service creation
        provider.get().setTaskContainer(project.tasks)

        return provider
    }
    
    private void registerTaskCreationRules(Project project, DockerExtension dockerExt, DockerTestExtension dockerTestExt,
                                           DockerWorkflowsExtension dockerWorkflowsExt,
                                           DockerProjectExtension dockerProjectExt,
                                           Provider<DockerService> dockerService, Provider<ComposeService> composeService,
                                           Provider<JsonService> jsonService) {

        // Register aggregate tasks first
        registerAggregateTasks(project)

        // Register per-image tasks after evaluation
        //
        // GRADLE 9/10 COMPATIBILITY NOTE: afterEvaluate usage
        //
        // This plugin uses afterEvaluate for dynamic task registration based on user DSL configuration.
        // This pattern is configuration cache safe because:
        //
        // 1. Tasks are created based on dockerExt.images and dockerTestExt.composeStacks containers
        //    populated by the user in their build.gradle
        // 2. Cannot create tasks before user finishes configuring the DSL (images, stacks, etc.)
        // 3. No Project references are captured in this closure - all values passed as parameters
        // 4. No cross-task mutation occurs during execution - only configuration-time task creation
        // 5. Standard pattern for container-based dynamic task registration in Gradle plugins
        //
        // Alternative approach would be dockerExt.images.all { } callback, but afterEvaluate
        // is simpler and equally correct for this use case (both are configuration-time only).
        //
        // Verified compatible with Gradle 9.x configuration cache:
        // - Integration tests pass with org.gradle.configuration-cache=true
        // - Zero configuration cache violations reported
        // - All 12+ test scenarios working correctly
        //
        // See: docs/design-docs/gradle-9-and-10-compatibility.md for details
        // See: docs/design-docs/gradle-incompatibility/2025-10-18-config-cache-incompat-issues.md
        //      for verification report
        project.afterEvaluate {
            // STEP 1: Translate dockerProject FIRST (before iterating images)
            // This adds images/stacks/pipelines to the existing containers before task registration
            if (dockerProjectExt.spec.isConfigured()) {
                def translator = new DockerProjectTranslator()
                translator.translate(project, dockerProjectExt.spec, dockerExt, dockerTestExt, dockerWorkflowsExt)
            }

            // STEP 2: Now register tasks for ALL images (including translator-created ones)
            // The .all { } callback will fire for both pre-existing AND newly-added items
            registerDockerImageTasks(project, dockerExt, dockerService)
            registerComposeStackTasks(project, dockerTestExt, composeService, jsonService)
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
            
            // Defer tags validation to task execution time for TestKit compatibility
            // Tags will be validated when tasks actually execute
            
            // Build task - always register, will be skipped at execution time in sourceRef mode
            project.tasks.register("dockerBuild${capitalizedName}", DockerBuildTask) { task ->
                configureDockerBuildTask(task, imageSpec, dockerService, project)
            }
            
            // Save task
            if (imageSpec.save.isPresent()) {
                project.tasks.register("dockerSave${capitalizedName}", DockerSaveTask) { task ->
                    configureDockerSaveTask(task, imageSpec, dockerService, project)
                }
            }
            
            // Tag task
            project.tasks.register("dockerTag${capitalizedName}", DockerTagTask) { task ->
                configureDockerTagTask(task, imageSpec, dockerService, project)
            }
            
            // Publish task
            if (imageSpec.publish.isPresent()) {
                project.tasks.register("dockerPublish${capitalizedName}", DockerPublishTask) { task ->
                    configureDockerPublishTask(task, imageSpec, dockerService, project)
                }
            }
            
            // Per-image aggregate task - runs all configured operations for this image
            project.tasks.register("dockerImage${capitalizedName}") { task ->
                task.group = 'docker'
                task.description = "Run all configured Docker operations for image: ${imageSpec.name}"
                
                // Always depend on build (will be skipped at execution time in sourceRef mode)
                task.dependsOn("dockerBuild${capitalizedName}")
                // Always depend on tag
                task.dependsOn("dockerTag${capitalizedName}")
                
                // Conditionally depend on save and publish
                if (imageSpec.save.isPresent()) {
                    task.dependsOn("dockerSave${capitalizedName}")
                }
                if (imageSpec.publish.isPresent()) {
                    task.dependsOn("dockerPublish${capitalizedName}")
                }
            }
        }
    }
    
    private void registerComposeStackTasks(Project project, DockerTestExtension dockerTestExt, 
                                         Provider<ComposeService> composeService, Provider<JsonService> jsonService) {
        dockerTestExt.composeStacks.all { stackSpec ->
            def stackName = stackSpec.name
            def capitalizedName = stackName.capitalize()
            
            // Up task
            project.tasks.register("composeUp${capitalizedName}", ComposeUpTask) { task ->
                configureComposeUpTask(task, stackSpec, composeService, jsonService, project)
            }

            // Down task
            project.tasks.register("composeDown${capitalizedName}", ComposeDownTask) { task ->
                configureComposeDownTask(task, stackSpec, composeService, project)
            }
        }
    }

    /**
     * Register workflow pipeline tasks.
     *
     * Creates a PipelineRunTask for each defined pipeline in dockerWorkflows.pipelines.
     * Tasks are named 'run{PipelineName}' (e.g., 'runCiPipeline').
     */
    private void registerWorkflowTasks(Project project, DockerWorkflowsExtension dockerWorkflowsExt,
                                       DockerExtension dockerExt, DockerTestExtension dockerTestExt,
                                       Provider<DockerService> dockerService, Provider<ComposeService> composeService,
                                       Provider<TaskExecutionService> taskExecutionService) {
        // Register an aggregate task for running all pipelines
        project.tasks.register('runPipelines') {
            group = 'docker workflows'
            description = 'Run all configured pipelines'
        }

        // Register tasks for each pipeline after evaluation (when pipelines are fully configured)
        project.afterEvaluate {
            dockerWorkflowsExt.pipelines.all { pipelineSpec ->
                def pipelineName = pipelineSpec.name
                def capitalizedName = pipelineName.capitalize()
                def taskName = "run${capitalizedName}"

                project.tasks.register(taskName, PipelineRunTask) { task ->
                    configurePipelineRunTask(task, pipelineSpec, dockerService, taskExecutionService)
                }

                // Add to aggregate task
                project.tasks.named('runPipelines').configure { aggregateTask ->
                    aggregateTask.dependsOn(taskName)
                }

                project.logger.info("Registered pipeline task: {} for pipeline: {}", taskName, pipelineName)
            }
        }
    }

    /**
     * Configure a PipelineRunTask with the pipeline spec and services.
     *
     * Uses TaskExecutionService (a Gradle BuildService) for configuration-cache-compatible
     * task lookup and execution.
     */
    private void configurePipelineRunTask(PipelineRunTask task, pipelineSpec,
                                          Provider<DockerService> dockerService,
                                          Provider<TaskExecutionService> taskExecutionService) {
        task.description = "Run pipeline: ${pipelineSpec.name}"

        // Configure pipeline spec and name
        task.pipelineSpec.set(pipelineSpec)
        task.pipelineName.set(pipelineSpec.name)

        // Set the TaskExecutionService provider for configuration cache compatibility
        // The Provider is serializable, allowing proper configuration cache handling
        task.setTaskExecutionServiceProvider(taskExecutionService)

        // Set the DockerService provider for tagging operations in onTestSuccess
        task.setDockerServiceProvider(dockerService)
    }

    private void configureAfterEvaluation(Project project, DockerExtension dockerExt, DockerTestExtension dockerTestExt,
                                           DockerWorkflowsExtension dockerWorkflowsExt) {
        // GRADLE 9/10 COMPATIBILITY NOTE: afterEvaluate usage
        //
        // This plugin uses afterEvaluate for validation and task dependency configuration.
        // This pattern is configuration cache safe because:
        //
        // 1. Validation must occur after user completes DSL configuration
        // 2. Task dependencies are based on user-configured properties (hasBuildContext, etc.)
        // 3. Cannot wire dependencies until full task graph is known
        // 4. No Project references captured - all values passed as parameters
        // 5. Uses task providers (tasks.named()) for configuration cache compatibility
        //
        // This is configuration-time work only, not execution-time mutation.
        // Alternative approaches (like providers and lazy wiring) would be significantly more
        // complex for this use case and provide no additional benefit.
        //
        // Verified compatible with Gradle 9.x configuration cache:
        // - Zero configuration cache violations
        // - All integration tests pass with configuration cache enabled
        //
        // See: docs/design-docs/gradle-9-and-10-compatibility.md for details
        // See: docs/design-docs/gradle-incompatibility/2025-10-18-config-cache-incompat-issues.md
        //      for verification report
        project.afterEvaluate {
            try {
                // Validate configurations
                dockerExt.validate()
                dockerTestExt.validate()

                // Validate pipeline configurations (cross-DSL references)
                validatePipelines(project, dockerWorkflowsExt, dockerExt, dockerTestExt)

                // Configure task dependencies
                configureTaskDependencies(project, dockerExt, dockerTestExt)

                project.logger.info("gradle-docker plugin configuration completed successfully")

            } catch (Exception e) {
                throw new GradleException("gradle-docker plugin configuration failed: ${e.message}", e)
            }
        }
    }

    /**
     * Validate all pipeline specifications
     */
    private void validatePipelines(Project project, DockerWorkflowsExtension dockerWorkflowsExt,
                                   DockerExtension dockerExt, DockerTestExtension dockerTestExt) {
        if (dockerWorkflowsExt.pipelines.isEmpty()) {
            project.logger.debug("No pipelines configured - skipping workflow validation")
            return
        }

        def validator = new PipelineValidator(project, dockerExt, dockerTestExt)
        validator.validateAll(dockerWorkflowsExt.pipelines)
        project.logger.info("Pipeline validation completed: {} pipeline(s) validated",
            dockerWorkflowsExt.pipelines.size())
    }
    
    private void configureTaskDependencies(Project project, DockerExtension dockerExt, DockerTestExtension dockerTestExt) {
        // Configure per-image task dependencies using configuration cache compatible approach
        dockerExt.images.all { imageSpec ->
            def imageName = imageSpec.name
            def capitalizedName = imageName.capitalize()
            def buildTaskName = "dockerBuild${capitalizedName}"

            // If image has build context, save/publish should depend on build
            // Build task will handle sourceRef mode detection at execution time
            def hasBuildContext = imageSpec.context.isPresent() ||
                imageSpec.contextTask != null ||
                (imageSpec.contextTaskName.isPresent() && !imageSpec.contextTaskName.get().isEmpty())
            if (hasBuildContext) {
                // Use configuration cache compatible approach with task providers
                if (imageSpec.save.isPresent()) {
                    project.tasks.named("dockerSave${capitalizedName}").configure { task ->
                        task.dependsOn(buildTaskName)
                    }
                }
                if (imageSpec.publish.isPresent()) {
                    project.tasks.named("dockerPublish${capitalizedName}").configure { task ->
                        task.dependsOn(buildTaskName)
                    }
                }
            }
        }

        // Configure aggregate task dependencies using configuration cache compatible approach
        project.tasks.named('dockerBuild').configure { aggregateTask ->
            // Use provider-based dependency configuration
            // All images now have build tasks, let execution decide whether to skip
            aggregateTask.dependsOn(project.provider {
                dockerExt.images.collect { "dockerBuild${it.name.capitalize()}" }
            })
        }

        project.tasks.named('dockerTag').configure { aggregateTask ->
            aggregateTask.dependsOn(project.provider {
                dockerExt.images.collect { "dockerTag${it.name.capitalize()}" }
            })
        }

        project.tasks.named('dockerSave').configure { aggregateTask ->
            aggregateTask.dependsOn(project.provider {
                dockerExt.images.findAll { it.save.isPresent() }.collect { "dockerSave${it.name.capitalize()}" }
            })
        }

        project.tasks.named('dockerPublish').configure { aggregateTask ->
            aggregateTask.dependsOn(project.provider {
                dockerExt.images.findAll { it.publish.isPresent() }.collect { "dockerPublish${it.name.capitalize()}" }
            })
        }

        // Configure compose aggregate dependencies
        project.tasks.named('composeUp').configure { aggregateTask ->
            aggregateTask.dependsOn(project.provider {
                dockerTestExt.composeStacks.names.collect { "composeUp${it.capitalize()}" }
            })
        }

        project.tasks.named('composeDown').configure { aggregateTask ->
            aggregateTask.dependsOn(project.provider {
                dockerTestExt.composeStacks.names.collect { "composeDown${it.capitalize()}" }
            })
        }

        // Configure global dockerImages aggregate task dependencies
        project.tasks.named('dockerImages').configure { aggregateTask ->
            aggregateTask.dependsOn(project.provider {
                dockerExt.images.names.collect { "dockerImage${it.capitalize()}" }
            })
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

        // Set sourceRef mode - skip build task execution in sourceRef mode
        task.sourceRefMode.set(isSourceRefMode(imageSpec))

        // Configure Docker nomenclature properties
        task.registry.set(imageSpec.registry)
        task.namespace.set(imageSpec.namespace)
        task.imageName.set(imageSpec.imageName)
        task.repository.set(imageSpec.repository)
        task.version.set(imageSpec.version)
        task.tags.set(imageSpec.tags)
        task.labels.set(imageSpec.labels)

        // Configure build context only if not in sourceRef mode
        if (!isSourceRefMode(imageSpec)) {
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
    }
    
    /**
     * Configure the context path for Docker build task based on context type
     */
    private void configureContextPath(DockerBuildTask task, ImageSpec imageSpec, project) {
        // Capture project layout to avoid configuration cache issues
        def buildDirectory = project.layout.buildDirectory
        
        // Check for contextTask (either via contextTask field or contextTaskName property)
        def hasContextTask = (imageSpec.contextTask != null) ||
                            (imageSpec.contextTaskName.isPresent() && !imageSpec.contextTaskName.get().isEmpty())
        
        if (hasContextTask) {
            // Use Copy task output as context - configuration cache compatible
            if (imageSpec.contextTask != null) {
                // contextTask is set directly - use it
                task.dependsOn(imageSpec.contextTask)
            } else if (imageSpec.contextTaskName.isPresent() && !imageSpec.contextTaskName.get().isEmpty()) {
                // Use task name for dependsOn to avoid Task serialization
                def contextTaskName = imageSpec.contextTaskName.get()
                task.dependsOn(contextTaskName)
            }
            // Directly set the expected destination directory without accessing the Task object
            // This avoids configuration cache issues with Task serialization
            task.contextPath.set(buildDirectory.dir("docker-context/${imageSpec.name}"))
        } else if (imageSpec.context.isPresent()) {
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
        if (imageSpec.dockerfile.isPresent() && imageSpec.dockerfileName.isPresent()) {
            throw new GradleException(
                "Image '${imageSpec.name}' cannot specify both 'dockerfile' and 'dockerfileName'. " +
                "Use 'dockerfile' for custom paths or 'dockerfileName' for custom names in default locations."
            )
        }
        
        // Capture project providers to avoid configuration cache issues
        def providers = project.providers
        def buildDirectory = project.layout.buildDirectory
        
        if (imageSpec.dockerfile.isPresent()) {
            // When dockerfile is explicitly set, always use it regardless of contextTask
            task.dockerfile.set(imageSpec.dockerfile)
        } else {
            // Handle dockerfileName or default dockerfile resolution using providers
            // Check for contextTask (either via contextTask field or contextTaskName property)
            def hasContextTask = (imageSpec.contextTask != null) ||
                                (imageSpec.contextTaskName.isPresent() && !imageSpec.contextTaskName.get().isEmpty())
            
            if (hasContextTask) {
                // Use contextTask directory with custom or default filename
                def dockerfileNameProvider = imageSpec.dockerfileName.isPresent()
                    ? imageSpec.dockerfileName
                    : project.providers.provider { "Dockerfile" }
                task.dockerfile.set(buildDirectory.dir("docker-context/${imageSpec.name}")
                    .map { contextDir -> contextDir.file(dockerfileNameProvider.get()) })
            } else if (imageSpec.context.isPresent()) {
                // Use traditional context directory with custom or default filename
                def dockerfileNameProvider = imageSpec.dockerfileName.isPresent() 
                    ? imageSpec.dockerfileName
                    : project.providers.provider { "Dockerfile" }
                task.dockerfile.set(imageSpec.context.file(dockerfileNameProvider))
            } else {
                throw new GradleException(
                    "Image '${imageSpec.name}' must specify either 'context' or 'contextTask'"
                )
            }
        }
    }


    private void configureDockerSaveTask(task, imageSpec, dockerService, project) {
        task.group = 'docker'
        task.description = "Save Docker image to file: ${imageSpec.name}"

        // Configure service dependency
        task.dockerService.set(dockerService)
        task.usesService(dockerService)

        // Configure individual properties for configuration cache compatibility
        task.registry.set(imageSpec.registry)
        task.namespace.set(imageSpec.namespace)
        task.imageName.set(imageSpec.imageName)
        task.repository.set(imageSpec.repository)
        task.version.set(imageSpec.version)
        task.tags.set(imageSpec.tags)
        task.sourceRef.set(imageSpec.sourceRef)
        task.pullIfMissing.set(imageSpec.pullIfMissing)
        // Evaluate effectiveSourceRef eagerly at configuration time (configuration cache compatible)
        try {
            task.effectiveSourceRef.set(imageSpec.getEffectiveSourceRef())
        } catch (Exception e) {
            // Return empty string if effective source ref cannot be determined (build mode)
            task.effectiveSourceRef.set("")
        }
        task.contextTaskName.set(imageSpec.contextTaskName)
        task.contextTaskPath.set(imageSpec.contextTaskPath)

        // Configure SourceRef component properties
        task.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
        task.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
        task.sourceRefImageName.set(imageSpec.sourceRefImageName)
        task.sourceRefRepository.set(imageSpec.sourceRefRepository)
        task.sourceRefTag.set(imageSpec.sourceRefTag)

        // Configure save specification if present
        if (imageSpec.save.present) {
            def saveSpec = imageSpec.save.get()
            task.outputFile.set(saveSpec.outputFile)
            task.compression.set(saveSpec.compression)  // Now using SaveCompression enum directly
        }
    }
    
    private void configureDockerTagTask(task, imageSpec, dockerService, project) {
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

        // Configure SourceRef Mode properties
        task.sourceRef.set(imageSpec.sourceRef)
        task.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
        task.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
        task.sourceRefImageName.set(imageSpec.sourceRefImageName)
        task.sourceRefRepository.set(imageSpec.sourceRefRepository)
        task.sourceRefTag.set(imageSpec.sourceRefTag)

        // Configure pullIfMissing properties
        task.pullIfMissing.set(imageSpec.pullIfMissing)
        // Evaluate effectiveSourceRef eagerly at configuration time (configuration cache compatible)
        try {
            task.effectiveSourceRef.set(imageSpec.getEffectiveSourceRef())
        } catch (Exception e) {
            // Return empty string if effective source ref cannot be determined (build mode)
            task.effectiveSourceRef.set("")
        }

        // Configure pullAuth if present
        if (imageSpec.pullAuth != null) {
            task.pullAuth.set(imageSpec.pullAuth)
        }
    }

    private void configureDockerPublishTask(task, imageSpec, dockerService, project) {
        task.group = 'docker'
        task.description = "Publish Docker image: ${imageSpec.name}"

        // Configure service dependency
        task.dockerService.set(dockerService)
        task.usesService(dockerService)

        // Configure individual properties for configuration cache compatibility
        task.registry.set(imageSpec.registry)
        task.namespace.set(imageSpec.namespace)
        task.imageName.set(imageSpec.imageName)
        task.repository.set(imageSpec.repository)
        task.version.set(imageSpec.version)
        task.tags.set(imageSpec.tags)
        task.sourceRef.set(imageSpec.sourceRef)
        task.pullIfMissing.set(imageSpec.pullIfMissing)
        // Evaluate effectiveSourceRef eagerly at configuration time (configuration cache compatible)
        try {
            task.effectiveSourceRef.set(imageSpec.getEffectiveSourceRef())
        } catch (Exception e) {
            // Return empty string if effective source ref cannot be determined (build mode)
            task.effectiveSourceRef.set("")
        }
        task.contextTaskName.set(imageSpec.contextTaskName)
        task.contextTaskPath.set(imageSpec.contextTaskPath)

        // Configure SourceRef component properties
        task.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
        task.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
        task.sourceRefImageName.set(imageSpec.sourceRefImageName)
        task.sourceRefRepository.set(imageSpec.sourceRefRepository)
        task.sourceRefTag.set(imageSpec.sourceRefTag)

        // Configure pullAuth if present
        if (imageSpec.pullAuth != null) {
            task.pullAuth.set(imageSpec.pullAuth)
        }

        // Configure publish targets from DSL
        if (imageSpec.publish.present) {
            def publishSpec = imageSpec.publish.get()
            task.publishTargets.set(publishSpec.to)
        }
    }
    
    private void configureComposeUpTask(task, stackSpec, composeService, jsonService, project) {
        task.group = 'docker compose'
        task.description = "Start Docker Compose stack: ${stackSpec.name}"

        // Configure service dependency
        task.composeService.set(composeService)

        // Configure compose files with multi-file support and backward compatibility
        // Resolve files at configuration time to avoid project access at execution time (Gradle 10 compatibility)
        task.composeFiles.setFrom(createComposeFilesList(stackSpec, project))

        if (stackSpec.envFiles && !stackSpec.envFiles.empty) {
            task.envFiles.setFrom(stackSpec.envFiles)
        }

        // Configure project and stack names with property override support
        // Capture project name property during configuration to avoid project access during execution
        def projectNameProperty = project.findProperty("compose.project.name")
        task.projectName.set(project.provider {
            projectNameProperty ?: stackSpec.projectName.getOrElse(stackSpec.name)
        })
        task.stackName.set(stackSpec.name)

        // Configure output directory for state files (Gradle 10 compatibility)
        task.outputDirectory.set(project.layout.buildDirectory.dir('compose-state'))

        // Configure wait-for-healthy settings (Gradle 10 compatibility)
        if (stackSpec.waitForHealthy.present) {
            def waitSpec = stackSpec.waitForHealthy.get()
            if (waitSpec.waitForServices.present) {
                task.waitForHealthyServices.set(waitSpec.waitForServices)
            }
            task.waitForHealthyTimeoutSeconds.set(waitSpec.timeoutSeconds.getOrElse(60))
            task.waitForHealthyPollSeconds.set(waitSpec.pollSeconds.getOrElse(2))
        }

        // Configure wait-for-running settings (Gradle 10 compatibility)
        if (stackSpec.waitForRunning.present) {
            def waitSpec = stackSpec.waitForRunning.get()
            if (waitSpec.waitForServices.present) {
                task.waitForRunningServices.set(waitSpec.waitForServices)
            }
            task.waitForRunningTimeoutSeconds.set(waitSpec.timeoutSeconds.getOrElse(60))
            task.waitForRunningPollSeconds.set(waitSpec.pollSeconds.getOrElse(2))
        }
    }
    
    private void configureComposeDownTask(task, stackSpec, composeService, project) {
        task.group = 'docker compose'
        task.description = "Stop Docker Compose stack: ${stackSpec.name}"
        
        // Configure service dependency
        task.composeService.set(composeService)
        
        // Configure compose files - automatically use same files as ComposeUp for proper teardown
        // Resolve files at configuration time to avoid project access at execution time (Gradle 10 compatibility)
        task.composeFiles.setFrom(createComposeFilesList(stackSpec, project))
        
        if (stackSpec.envFiles && !stackSpec.envFiles.empty) {
            task.envFiles.setFrom(stackSpec.envFiles)
        }
        
        // Configure project and stack names with property override support
        // Capture project name property during configuration to avoid project access during execution
        def projectNameProperty = project.findProperty("compose.project.name")
        task.projectName.set(project.provider {
            projectNameProperty ?: stackSpec.projectName.getOrElse(stackSpec.name)
        })
        task.stackName.set(stackSpec.name)

        // Configure logs capture properties at configuration time (Gradle 10 compatibility)
        if (stackSpec.logs.present) {
            def logsSpec = stackSpec.logs.get()
            task.logsEnabled.set(true)
            if (logsSpec.services.present) {
                task.logsServices.set(logsSpec.services)
            }
            task.logsTailLines.set(logsSpec.tailLines)
            task.logsFollow.set(logsSpec.follow)
            if (logsSpec.writeTo.present) {
                task.logsWriteTo.set(logsSpec.writeTo)
            }
        } else {
            task.logsEnabled.set(false)
        }
    }
    
    /**
     * Create a list of compose files with multi-file support and backward compatibility.
     * Resolves files at configuration time to avoid project access at execution time (Gradle 10 compatibility).
     */
    private List<File> createComposeFilesList(stackSpec, project) {
        def files = []

        // Priority 1: Use new multi-file configuration
        if (stackSpec.composeFiles.present && !stackSpec.composeFiles.getOrElse([]).empty) {
            // Add files from composeFiles property (List<String>)
            // Resolve at configuration time to avoid project.file() at execution time
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
    
    private void setupTestIntegration(Project project) {
        def testIntegration = project.objects.newInstance(TestIntegrationExtension, project.name)

        // Register the extension so tests can retrieve it
        project.extensions.add(TestIntegrationExtension, 'testIntegration', testIntegration)

        // Wire the dockerTestExtension reference
        def dockerTestExt = project.extensions.findByType(DockerTestExtension)
        testIntegration.setDockerTestExtension(dockerTestExt)
        
        // Add extension methods to all Test tasks
        project.tasks.withType(Test) { test ->
            // Add usesCompose method
            test.ext.usesCompose = { Map args ->
                def stackName = args.stack
                def lifecycle = args.lifecycle ?: 'class'
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

    /**
     * Setup integration test source set convention.
     * The convention automatically creates the integrationTest source set, configurations,
     * and task when the java plugin is applied.
     *
     * Rationale: Create the integrationTest source set immediately (like the java plugin creates 'test')
     * so that configurations are available for the dependencies block. The source set is always available
     * whether the user needs it or not - similar to how 'test' source set works.
     *
     * @param project The Gradle project
     * @param dockerTestExt The DockerTestExtension instance (unused, kept for signature compatibility)
     */
    private void setupIntegrationTestSourceSet(Project project, DockerTestExtension dockerTestExt) {
        // Create source set immediately when java plugin is present
        // This ensures configurations are available for the dependencies block
        project.plugins.withId('java') {
            project.logger.info("Java plugin detected, applying integration test source set convention")
            createIntegrationTestSourceSetIfNeeded(project)
        }
    }

    /**
     * Create integrationTest source set if it doesn't already exist.
     * Supports both Java and Groovy test files transparently.
     *
     * @param project The Gradle project
     */
    private void createIntegrationTestSourceSetIfNeeded(Project project) {
        def sourceSets = project.extensions.findByType(SourceSetContainer)
        if (!sourceSets) {
            project.logger.debug("SourceSetContainer not found, skipping integration test convention")
            return
        }

        // Check if user already created integrationTest source set (user override protection)
        def existingSourceSet = sourceSets.findByName('integrationTest')
        if (existingSourceSet) {
            project.logger.info("Integration test source set already exists, skipping convention")
            return
        }

        project.logger.info("Creating integrationTest source set via convention")

        // Get main source set to configure classpaths
        def mainSourceSet = sourceSets.getByName('main')

        // Create source set (classpaths will be configured after configurations exist)
        def integrationTestSourceSet = sourceSets.create('integrationTest') { sourceSet ->
            // ALWAYS configure Java source directory
            sourceSet.java.srcDir('src/integrationTest/java')

            // Configure Groovy source directory if groovy plugin is applied
            // (groovy plugin automatically applies java plugin)
            project.plugins.withId('groovy') {
                sourceSet.groovy.srcDir('src/integrationTest/groovy')
            }

            // Always configure resources
            sourceSet.resources.srcDir('src/integrationTest/resources')
        }

        // Configure configurations
        configureIntegrationTestConfigurations(project)

        // Configure classpaths AFTER configurations exist
        // Explicitly set classpaths to include main output, like Java plugin does for test source set
        integrationTestSourceSet.compileClasspath = project.files(
            mainSourceSet.output,
            project.configurations.getByName('integrationTestCompileClasspath')
        )
        integrationTestSourceSet.runtimeClasspath = project.files(
            integrationTestSourceSet.output,
            mainSourceSet.output,
            project.configurations.getByName('integrationTestRuntimeClasspath')
        )

        // Configure resource processing
        configureIntegrationTestResourceProcessing(project)

        // Register integration test task
        registerIntegrationTestTask(project, sourceSets)

        project.logger.lifecycle("Integration test convention applied: source set, configurations, and task created")
    }

    /**
     * Configure integrationTest configurations to extend from test configurations.
     * This allows integration tests to automatically inherit test dependencies.
     *
     * @param project The Gradle project
     */
    private void configureIntegrationTestConfigurations(Project project) {
        project.configurations {
            // Extend from test configurations - integration tests inherit test dependencies
            integrationTestImplementation.extendsFrom testImplementation
            integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
        }

        project.logger.debug("Integration test configurations configured")
    }

    /**
     * Configure integration test resource processing task.
     * Sets duplicatesStrategy to INCLUDE to allow resource overlays.
     *
     * @param project The Gradle project
     */
    private void configureIntegrationTestResourceProcessing(Project project) {
        // Task is created automatically by java plugin when source set is created
        project.tasks.named('processIntegrationTestResources') { task ->
            task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }

        project.logger.debug("Integration test resource processing configured")
    }

    /**
     * Register the integrationTest task.
     * Configured to run integration tests against Docker Compose environments.
     *
     * @param project The Gradle project
     * @param sourceSets The source set container
     */
    private void registerIntegrationTestTask(Project project, SourceSetContainer sourceSets) {
        def integrationTestSourceSet = sourceSets.getByName('integrationTest')

        // Check if task already exists (user may have created it)
        if (project.tasks.findByName('integrationTest')) {
            project.logger.info("Integration test task already exists, skipping convention")
            return
        }

        project.logger.info("Registering integrationTest task via convention")

        project.tasks.register('integrationTest', Test) { task ->
            task.description = 'Runs integration tests'
            task.group = 'verification'

            // Configure test task
            task.testClassesDirs = integrationTestSourceSet.output.classesDirs
            task.classpath = integrationTestSourceSet.runtimeClasspath

            // Support both JUnit and Spock
            task.useJUnitPlatform()

            // Docker integration tests are NOT cacheable (interact with external Docker daemon)
            task.outputs.cacheIf { false }

            // Prevent parallel execution with regular tests (avoid port conflicts, resource contention)
            task.mustRunAfter(project.tasks.named('test'))
        }

        project.logger.debug("Integration test task registered")
    }

    /**
     * Determines if an ImageSpec is configured for sourceRef mode (working with existing images)
     * rather than build mode (building new images from source)
     */
    private static boolean isSourceRefMode(ImageSpec imageSpec) {
        // Check direct sourceRef
        if (imageSpec.sourceRef.isPresent() && !imageSpec.sourceRef.get().isEmpty()) {
            return true
        }

        // Check sourceRef components
        def hasRepository = imageSpec.sourceRefRepository.isPresent() && !imageSpec.sourceRefRepository.get().isEmpty()
        def hasImageName = imageSpec.sourceRefImageName.isPresent() && !imageSpec.sourceRefImageName.get().isEmpty()
        def hasNamespace = imageSpec.sourceRefNamespace.isPresent() && !imageSpec.sourceRefNamespace.get().isEmpty()
        def hasRegistry = imageSpec.sourceRefRegistry.isPresent() && !imageSpec.sourceRefRegistry.get().isEmpty()

        // If any sourceRef component is specified, it's sourceRef mode
        return hasRepository || hasImageName || hasNamespace || hasRegistry
    }

}