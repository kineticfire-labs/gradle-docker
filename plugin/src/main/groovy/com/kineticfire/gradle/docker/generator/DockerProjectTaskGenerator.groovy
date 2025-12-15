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

package com.kineticfire.gradle.docker.generator

import com.kineticfire.gradle.docker.Lifecycle
import com.kineticfire.gradle.docker.extension.DockerProjectExtension
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.project.ProjectImageSpec
import com.kineticfire.gradle.docker.spec.project.ProjectSuccessSpec
import com.kineticfire.gradle.docker.spec.project.ProjectTestSpec
import com.kineticfire.gradle.docker.spec.project.PublishTargetSpec
import com.kineticfire.gradle.docker.task.CleanupTask
import com.kineticfire.gradle.docker.task.DockerBuildTask
import com.kineticfire.gradle.docker.task.DockerPublishTask
import com.kineticfire.gradle.docker.task.DockerSaveTask
import com.kineticfire.gradle.docker.task.TagOnSuccessTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Task generator for the dockerProject DSL.
 *
 * This generator creates a configuration-cache-compatible task graph for the
 * dockerProject workflow. All tasks are registered at configuration time with
 * proper dependency wiring, and file-based state communication is used for
 * conditional execution.
 *
 * Generated Task Graph:
 * <pre>
 * prepare{ImageName}Context (if jarFrom is set)
 *         ↓
 * dockerBuild{ImageName}
 *         ↓
 * composeUp{StackName} (if test.compose is set)
 *         ↓
 * integrationTest (test task)
 *         ↓
 * composeDown{StackName}
 *         ↓
 * dockerProjectTagOnSuccess (conditional on test success)
 *         ↓
 * dockerProjectSave (optional, conditional on test success)
 *         ↓
 * dockerProjectPublish{Target} (optional, conditional on test success)
 *         ↓
 * runDockerProject (lifecycle task)
 * </pre>
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: This generator runs during configuration time
 * only. All task relationships are established using lazy providers and proper
 * task dependency wiring. No Project reference is captured for execution time.
 */
class DockerProjectTaskGenerator extends TaskGraphGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerProjectTaskGenerator)

    private static final String TASK_GROUP = 'docker project'
    private static final String STATE_DIR = 'docker-project'

    /**
     * Main entry point for generating the dockerProject task graph.
     *
     * @param project The Gradle project
     * @param extension The dockerProject extension containing configuration
     * @param dockerServiceProvider Provider for DockerService (injected at config time)
     */
    void generate(Project project, DockerProjectExtension extension, Provider<DockerService> dockerServiceProvider) {
        if (!extension.spec.isConfigured()) {
            LOGGER.debug("dockerProject is not configured, skipping task generation")
            return
        }

        def spec = extension.spec
        def testSpec = spec.test.get()
        def successSpec = spec.onSuccess.get()

        // Get all images
        def allImages = spec.images.toList()
        if (allImages.isEmpty()) {
            LOGGER.debug("No images configured in dockerProject, skipping task generation")
            return
        }

        // Validate and get primary image
        def primaryImage = spec.primaryImage
        if (primaryImage == null && allImages.size() > 1) {
            throw new org.gradle.api.GradleException(
                "Multiple images defined in dockerProject but none marked as primary. " +
                "Set primary.set(true) on exactly one image."
            )
        }
        // If only one image and no explicit primary, use it
        if (primaryImage == null) {
            primaryImage = allImages.first()
        }

        // Build tasks for ALL images
        def buildTaskProviders = [:]
        allImages.each { imageSpec ->
            def imageName = deriveImageName(imageSpec, project)
            def sanitizedImageName = TaskNamingUtils.normalizeName(imageName)
            sanitizedImageName = TaskNamingUtils.capitalize(sanitizedImageName)

            LOGGER.lifecycle("dockerProject: Generating build task for image '{}'", imageName)

            def buildTaskProvider = registerBuildTask(project, imageSpec, sanitizedImageName, dockerServiceProvider)
            buildTaskProviders[imageName] = buildTaskProvider
        }

        // Use primary image for task naming and success operations
        def primaryImageName = deriveImageName(primaryImage, project)
        def primarySanitizedName = TaskNamingUtils.normalizeName(primaryImageName)
        primarySanitizedName = TaskNamingUtils.capitalize(primarySanitizedName)

        LOGGER.lifecycle("dockerProject: Primary image is '{}', generating pipeline tasks", primaryImageName)

        // 2. Register compose tasks if test is configured
        def composeUpTaskName = null
        def composeDownTaskName = null
        if (testSpec.compose.isPresent() && !testSpec.compose.get().isEmpty()) {
            def stackName = "${primarySanitizedName}Test"
            composeUpTaskName = TaskNamingUtils.composeUpTaskName(stackName)
            composeDownTaskName = TaskNamingUtils.composeDownTaskName(stackName)

            // Note: composeUp/composeDown tasks are registered by DockerTestExtension
            // We just wire dependencies here
        }

        // 3. Register tag on success task (for primary image only)
        def tagOnSuccessTaskProvider = registerTagOnSuccessTask(
            project, primaryImage, successSpec, primarySanitizedName, dockerServiceProvider
        )

        // 4. Register save task if configured (for primary image only)
        def saveTaskProvider = null
        if (successSpec.saveFile.isPresent() && !successSpec.saveFile.get().isEmpty()) {
            saveTaskProvider = registerSaveTask(project, primaryImage, successSpec, primarySanitizedName, dockerServiceProvider)
        }

        // 5. Register publish tasks (for primary image only)
        def publishTaskProviders = registerPublishTasks(project, primaryImage, successSpec, primarySanitizedName, dockerServiceProvider)

        // 6. Wire task dependencies - all build tasks feed into the pipeline
        wireTaskDependencies(
            project, testSpec, primarySanitizedName,
            buildTaskProviders, composeUpTaskName, composeDownTaskName,
            tagOnSuccessTaskProvider, saveTaskProvider, publishTaskProviders
        )

        // 7. Create lifecycle task
        def lifecycleTaskProvider = createLifecycleTask(
            project, tagOnSuccessTaskProvider, saveTaskProvider, publishTaskProviders
        )

        LOGGER.lifecycle("dockerProject: Task graph generation complete for {} image(s)", allImages.size())
    }

    /**
     * Derive the image name from the spec or project name.
     * Priority: imageName > name (deprecated) > repository > blockName > sourceRefImageName > project.name
     */
    private String deriveImageName(ProjectImageSpec imageSpec, Project project) {
        // Explicit imageName takes highest priority
        if (imageSpec.imageName.isPresent() && !imageSpec.imageName.get().isEmpty()) {
            return imageSpec.imageName.get()
        }
        // Deprecated 'legacyName' property (renamed from 'name' for Named interface)
        if (imageSpec.legacyName.isPresent() && !imageSpec.legacyName.get().isEmpty()) {
            return imageSpec.legacyName.get()
        }
        // Repository mode - extract image name from repository (e.g., "myorg/myapp" -> "myapp")
        if (imageSpec.repository.isPresent() && !imageSpec.repository.get().isEmpty()) {
            def repo = imageSpec.repository.get()
            def lastSlash = repo.lastIndexOf('/')
            return lastSlash >= 0 ? repo.substring(lastSlash + 1) : repo
        }
        // Block name from DSL (e.g., "myApp" from images { myApp { } })
        if (imageSpec.blockName.isPresent() && !imageSpec.blockName.get().isEmpty()) {
            return imageSpec.blockName.get()
        }
        // Source reference image name
        if (imageSpec.sourceRefImageName.isPresent() && !imageSpec.sourceRefImageName.get().isEmpty()) {
            return imageSpec.sourceRefImageName.get()
        }
        // Fallback to project name
        return project.name
    }

    /**
     * Register the Docker build task.
     */
    private TaskProvider<DockerBuildTask> registerBuildTask(
            Project project,
            ProjectImageSpec imageSpec,
            String sanitizedImageName,
            Provider<DockerService> dockerServiceProvider) {

        def buildTaskName = TaskNamingUtils.buildTaskName(sanitizedImageName)

        // If using jarFrom, create context preparation task first
        TaskProvider<Copy> contextTaskProvider = null
        if (imageSpec.jarFrom.isPresent() && !imageSpec.jarFrom.get().isEmpty()) {
            contextTaskProvider = registerContextTask(project, imageSpec, sanitizedImageName)
        }

        // Check if build task already exists (registered by docker DSL)
        if (taskExists(project, buildTaskName)) {
            LOGGER.debug("Build task {} already exists, skipping registration", buildTaskName)
            return project.tasks.named(buildTaskName, DockerBuildTask)
        }

        LOGGER.debug("Registering build task: {}", buildTaskName)

        def taskProvider = project.tasks.register(buildTaskName, DockerBuildTask) { task ->
            task.group = TASK_GROUP
            task.description = "Build Docker image ${sanitizedImageName}"

            task.dockerService.set(dockerServiceProvider)

            // Configure image naming
            if (imageSpec.imageName.isPresent()) {
                task.imageName.set(imageSpec.imageName)
            } else if (imageSpec.legacyName.isPresent()) {
                task.imageName.set(imageSpec.legacyName)
            } else {
                task.imageName.set(sanitizedImageName)
            }

            // Configure tags
            if (imageSpec.tags.isPresent()) {
                task.tags.set(imageSpec.tags)
            }

            // Configure registry and namespace
            if (imageSpec.registry.isPresent()) {
                task.registry.set(imageSpec.registry)
            }
            if (imageSpec.namespace.isPresent()) {
                task.namespace.set(imageSpec.namespace)
            }

            // Configure build args and labels
            task.buildArgs.putAll(imageSpec.buildArgs)
            task.labels.putAll(imageSpec.labels)

            // Configure context and Dockerfile
            if (contextTaskProvider != null) {
                def contextDir = project.layout.buildDirectory.dir("docker-context/${sanitizedImageName}")
                task.contextPath.set(contextDir)
                task.dependsOn(contextTaskProvider)

                // When using context task, Dockerfile is copied into the context directory
                def dockerfilePath = imageSpec.dockerfile.getOrElse('src/main/docker/Dockerfile')
                def dockerfileName = new File(dockerfilePath).name
                task.dockerfile.set(contextDir.map { it.file(dockerfileName) })
            } else if (imageSpec.contextDir.isPresent() && !imageSpec.contextDir.get().isEmpty()) {
                task.contextPath.set(project.layout.projectDirectory.dir(imageSpec.contextDir.get()))

                // Configure Dockerfile for non-context-task builds
                if (imageSpec.dockerfile.isPresent() && !imageSpec.dockerfile.get().isEmpty()) {
                    task.dockerfile.set(project.layout.projectDirectory.file(imageSpec.dockerfile.get()))
                }
            } else {
                // No context - configure Dockerfile if specified
                if (imageSpec.dockerfile.isPresent() && !imageSpec.dockerfile.get().isEmpty()) {
                    task.dockerfile.set(project.layout.projectDirectory.file(imageSpec.dockerfile.get()))
                }
            }
        }

        return taskProvider
    }

    /**
     * Register context preparation task for jarFrom builds.
     */
    private TaskProvider<Copy> registerContextTask(
            Project project,
            ProjectImageSpec imageSpec,
            String sanitizedImageName) {

        def taskName = "prepare${TaskNamingUtils.capitalize(sanitizedImageName)}Context"

        if (taskExists(project, taskName)) {
            LOGGER.debug("Context task {} already exists, skipping registration", taskName)
            return project.tasks.named(taskName, Copy)
        }

        def jarTaskPath = imageSpec.jarFrom.get()

        LOGGER.debug("Registering context preparation task: {}", taskName)

        return project.tasks.register(taskName, Copy) { task ->
            task.group = TASK_GROUP
            task.description = "Prepare Docker build context for ${sanitizedImageName}"
            task.into(project.layout.buildDirectory.dir("docker-context/${sanitizedImageName}"))

            // Copy Dockerfile
            def dockerfilePath = imageSpec.dockerfile.getOrElse('src/main/docker/Dockerfile')
            def dockerfileFile = project.file(dockerfilePath)
            if (dockerfileFile.parentFile?.exists()) {
                task.from(dockerfileFile.parentFile) { spec ->
                    spec.include(dockerfileFile.name)
                }
            }

            // Copy JAR
            def jarName = imageSpec.jarName.getOrElse('app.jar')
            def jarTask = resolveJarTask(project, jarTaskPath)
            if (jarTask != null) {
                task.from(jarTask.flatMap { it.archiveFile }) { spec ->
                    spec.rename { jarName }
                }
                task.dependsOn(jarTaskPath)
            }
        }
    }

    /**
     * Resolve jar task from path.
     */
    private TaskProvider<?> resolveJarTask(Project project, String jarTaskPath) {
        try {
            if (!jarTaskPath.contains(':')) {
                return project.tasks.named(jarTaskPath)
            } else if (jarTaskPath.startsWith(':')) {
                def pathWithoutLeadingColon = jarTaskPath.substring(1)
                def lastColonIndex = pathWithoutLeadingColon.lastIndexOf(':')

                if (lastColonIndex == -1) {
                    return project.rootProject.tasks.named(pathWithoutLeadingColon)
                } else {
                    def jarTaskName = pathWithoutLeadingColon.substring(lastColonIndex + 1)
                    def projectPath = ':' + pathWithoutLeadingColon.substring(0, lastColonIndex)
                    def jarProject = project.project(projectPath)
                    return jarProject.tasks.named(jarTaskName)
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve jar task '{}': {}", jarTaskPath, e.message)
        }
        return null
    }

    /**
     * Register the tag on success task.
     */
    private TaskProvider<TagOnSuccessTask> registerTagOnSuccessTask(
            Project project,
            ProjectImageSpec imageSpec,
            ProjectSuccessSpec successSpec,
            String sanitizedImageName,
            Provider<DockerService> dockerServiceProvider) {

        def taskName = TaskNamingUtils.tagOnSuccessTaskName(TaskNamingUtils.dockerProjectPrefix())

        if (taskExists(project, taskName)) {
            LOGGER.debug("Tag on success task {} already exists, skipping registration", taskName)
            return project.tasks.named(taskName, TagOnSuccessTask)
        }

        LOGGER.debug("Registering tag on success task: {}", taskName)

        def resultFile = project.layout.buildDirectory.file("${STATE_DIR}/test-result.json")

        return project.tasks.register(taskName, TagOnSuccessTask) { task ->
            task.group = TASK_GROUP
            task.description = "Apply additional tags to image on test success"

            task.dockerService.set(dockerServiceProvider)

            // Configure image name
            if (imageSpec.imageName.isPresent()) {
                task.imageName.set(imageSpec.imageName)
            } else if (imageSpec.legacyName.isPresent()) {
                task.imageName.set(imageSpec.legacyName)
            } else {
                task.imageName.set(sanitizedImageName)
            }

            // Configure additional tags
            task.additionalTags.set(successSpec.additionalTags)

            // Configure test result file
            task.testResultFile.set(resultFile)
        }
    }

    /**
     * Register the save task.
     */
    private TaskProvider<DockerSaveTask> registerSaveTask(
            Project project,
            ProjectImageSpec imageSpec,
            ProjectSuccessSpec successSpec,
            String sanitizedImageName,
            Provider<DockerService> dockerServiceProvider) {

        def taskName = TaskNamingUtils.saveTaskName(TaskNamingUtils.dockerProjectPrefix())

        if (taskExists(project, taskName)) {
            LOGGER.debug("Save task {} already exists, skipping registration", taskName)
            return project.tasks.named(taskName, DockerSaveTask)
        }

        LOGGER.debug("Registering save task: {}", taskName)

        def resultFile = project.layout.buildDirectory.file("${STATE_DIR}/test-result.json")

        return project.tasks.register(taskName, DockerSaveTask) { task ->
            task.group = TASK_GROUP
            task.description = "Save Docker image to file on test success"

            task.dockerService.set(dockerServiceProvider)

            // Configure image name
            if (imageSpec.imageName.isPresent()) {
                task.imageName.set(imageSpec.imageName)
            } else if (imageSpec.legacyName.isPresent()) {
                task.imageName.set(imageSpec.legacyName)
            } else {
                task.imageName.set(sanitizedImageName)
            }

            // Configure save file
            def saveFilePath = successSpec.saveFile.get()
            task.outputFile.set(project.file(saveFilePath))

            // Configure compression
            def compression = successSpec.inferCompression()
            if (compression != null) {
                task.compression.set(compression)
            }

            // Add onlyIf based on test result
            task.onlyIf { t ->
                def file = resultFile.get().asFile
                if (!file.exists()) {
                    LOGGER.debug("Test result file not found, skipping save")
                    return false
                }
                return PipelineStateFile.isTestSuccessful(file)
            }
        }
    }

    /**
     * Register publish tasks for each target.
     */
    private List<TaskProvider<DockerPublishTask>> registerPublishTasks(
            Project project,
            ProjectImageSpec imageSpec,
            ProjectSuccessSpec successSpec,
            String sanitizedImageName,
            Provider<DockerService> dockerServiceProvider) {

        def taskProviders = []
        def resultFile = project.layout.buildDirectory.file("${STATE_DIR}/test-result.json")

        // Check for flat publish configuration
        if (successSpec.publishRegistry.isPresent() && !successSpec.publishRegistry.get().isEmpty()) {
            def taskName = TaskNamingUtils.publishTaskName(TaskNamingUtils.dockerProjectPrefix())

            if (!taskExists(project, taskName)) {
                LOGGER.debug("Registering publish task: {}", taskName)

                def taskProvider = project.tasks.register(taskName, DockerPublishTask) { task ->
                    task.group = TASK_GROUP
                    task.description = "Publish Docker image on test success"

                    task.dockerService.set(dockerServiceProvider)

                    // Configure image name
                    if (imageSpec.imageName.isPresent()) {
                        task.imageName.set(imageSpec.imageName)
                    } else if (imageSpec.legacyName.isPresent()) {
                        task.imageName.set(imageSpec.legacyName)
                    } else {
                        task.imageName.set(sanitizedImageName)
                    }

                    // Configure registry
                    task.registry.set(successSpec.publishRegistry)

                    // Configure namespace
                    if (successSpec.publishNamespace.isPresent()) {
                        task.namespace.set(successSpec.publishNamespace)
                    }

                    // Configure tags
                    def tagsToPublish = successSpec.publishTags.get().isEmpty() ?
                        successSpec.additionalTags.get() : successSpec.publishTags.get()
                    task.tags.set(tagsToPublish)

                    // Add onlyIf based on test result
                    task.onlyIf { t ->
                        def file = resultFile.get().asFile
                        if (!file.exists()) {
                            LOGGER.debug("Test result file not found, skipping publish")
                            return false
                        }
                        return PipelineStateFile.isTestSuccessful(file)
                    }
                }

                taskProviders << taskProvider
            }
        }

        // Check for multiple publish targets
        successSpec.publishTargets.each { PublishTargetSpec targetSpec ->
            def taskName = TaskNamingUtils.publishTaskName(
                TaskNamingUtils.dockerProjectPrefix(),
                targetSpec.name
            )

            if (!taskExists(project, taskName)) {
                LOGGER.debug("Registering publish task for target '{}': {}", targetSpec.name, taskName)

                def taskProvider = project.tasks.register(taskName, DockerPublishTask) { task ->
                    task.group = TASK_GROUP
                    task.description = "Publish Docker image to ${targetSpec.name} on test success"

                    task.dockerService.set(dockerServiceProvider)

                    // Configure image name
                    if (imageSpec.imageName.isPresent()) {
                        task.imageName.set(imageSpec.imageName)
                    } else if (imageSpec.legacyName.isPresent()) {
                        task.imageName.set(imageSpec.legacyName)
                    } else {
                        task.imageName.set(sanitizedImageName)
                    }

                    // Configure registry from target
                    if (targetSpec.registry.isPresent()) {
                        task.registry.set(targetSpec.registry)
                    }

                    // Configure namespace from target
                    if (targetSpec.namespace.isPresent()) {
                        task.namespace.set(targetSpec.namespace)
                    }

                    // Configure tags from target
                    def targetTags = targetSpec.tags.get()
                    if (!targetTags.isEmpty()) {
                        task.tags.set(targetTags)
                    } else {
                        task.tags.set(successSpec.additionalTags)
                    }

                    // Note: Auth is handled through publishTargets configured separately
                    // The task will use the auth from its publishTargets property at execution time

                    // Add onlyIf based on test result
                    task.onlyIf { t ->
                        def file = resultFile.get().asFile
                        if (!file.exists()) {
                            LOGGER.debug("Test result file not found, skipping publish to {}", targetSpec.name)
                            return false
                        }
                        return PipelineStateFile.isTestSuccessful(file)
                    }
                }

                taskProviders << taskProvider
            }
        }

        return taskProviders
    }

    /**
     * Wire all task dependencies.
     *
     * @param buildTaskProviders Map of image name -> build task provider (supports multiple images)
     */
    private void wireTaskDependencies(
            Project project,
            ProjectTestSpec testSpec,
            String sanitizedImageName,
            Map<String, TaskProvider<DockerBuildTask>> buildTaskProviders,
            String composeUpTaskName,
            String composeDownTaskName,
            TaskProvider<TagOnSuccessTask> tagOnSuccessTaskProvider,
            TaskProvider<DockerSaveTask> saveTaskProvider,
            List<TaskProvider<DockerPublishTask>> publishTaskProviders) {

        def testTaskName = testSpec.testTaskName.getOrElse('integrationTest')
        def resultFile = project.layout.buildDirectory.file("${STATE_DIR}/test-result.json")

        // Wire compose tasks if configured
        if (composeUpTaskName != null && taskExists(project, composeUpTaskName)) {
            // composeUp depends on ALL build tasks (all images must be built first)
            buildTaskProviders.values().each { buildTaskProvider ->
                wireTaskDependency(project, composeUpTaskName, buildTaskProvider.name)
            }

            // Test task depends on composeUp, finalizedBy composeDown
            if (taskExists(project, testTaskName)) {
                def lifecycle = testSpec.lifecycle.getOrElse(Lifecycle.CLASS)
                if (lifecycle == Lifecycle.CLASS) {
                    wireTaskDependency(project, testTaskName, composeUpTaskName)
                    if (composeDownTaskName != null && taskExists(project, composeDownTaskName)) {
                        wireFinalizedBy(project, testTaskName, composeDownTaskName)
                    }
                }

                // Configure test task to write result file
                project.tasks.named(testTaskName).configure { task ->
                    if (task instanceof org.gradle.api.tasks.testing.Test) {
                        task.outputs.file(resultFile)
                        task.doLast {
                            def success = task.state.failure == null
                            def message = success ? "Tests passed" : (task.state.failure?.message ?: "Tests failed")
                            PipelineStateFile.writeTestResult(
                                resultFile.get().asFile,
                                success,
                                message,
                                System.currentTimeMillis()
                            )
                        }
                    }
                }
            }
        } else if (taskExists(project, testTaskName)) {
            // No compose - test depends directly on ALL build tasks
            buildTaskProviders.values().each { buildTaskProvider ->
                wireTaskDependency(project, testTaskName, buildTaskProvider.name)
            }

            // Configure test task to write result file
            project.tasks.named(testTaskName).configure { task ->
                if (task instanceof org.gradle.api.tasks.testing.Test) {
                    task.outputs.file(resultFile)
                    task.doLast {
                        def success = task.state.failure == null
                        def message = success ? "Tests passed" : (task.state.failure?.message ?: "Tests failed")
                        PipelineStateFile.writeTestResult(
                            resultFile.get().asFile,
                            success,
                            message,
                            System.currentTimeMillis()
                        )
                    }
                }
            }
        }

        // Tag on success depends on test
        if (taskExists(project, testTaskName)) {
            tagOnSuccessTaskProvider.configure { task ->
                task.dependsOn(testTaskName)
                task.mustRunAfter(testTaskName)
            }
        }

        // Save depends on tag on success
        if (saveTaskProvider != null) {
            saveTaskProvider.configure { task ->
                task.dependsOn(tagOnSuccessTaskProvider)
            }
        }

        // Publish tasks depend on save (if present) or tag on success
        publishTaskProviders.each { publishTaskProvider ->
            publishTaskProvider.configure { task ->
                if (saveTaskProvider != null) {
                    task.dependsOn(saveTaskProvider)
                } else {
                    task.dependsOn(tagOnSuccessTaskProvider)
                }
            }
        }
    }

    /**
     * Create the lifecycle task.
     */
    private TaskProvider<Task> createLifecycleTask(
            Project project,
            TaskProvider<TagOnSuccessTask> tagOnSuccessTaskProvider,
            TaskProvider<DockerSaveTask> saveTaskProvider,
            List<TaskProvider<DockerPublishTask>> publishTaskProviders) {

        def taskName = TaskNamingUtils.lifecycleTaskName(TaskNamingUtils.dockerProjectPrefix())

        def lifecycleTask = getOrCreateLifecycleTask(
            project,
            taskName,
            TASK_GROUP,
            "Run the complete dockerProject workflow"
        )

        lifecycleTask.configure { task ->
            // Depend on the last task in the chain
            if (!publishTaskProviders.isEmpty()) {
                publishTaskProviders.each { publishTask ->
                    task.dependsOn(publishTask)
                }
            } else if (saveTaskProvider != null) {
                task.dependsOn(saveTaskProvider)
            } else {
                task.dependsOn(tagOnSuccessTaskProvider)
            }
        }

        return lifecycleTask
    }
}
