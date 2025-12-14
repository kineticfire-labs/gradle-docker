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

package com.kineticfire.gradle.docker.service

import com.kineticfire.gradle.docker.Lifecycle
import com.kineticfire.gradle.docker.extension.DockerExtension
import com.kineticfire.gradle.docker.extension.DockerTestExtension
import com.kineticfire.gradle.docker.extension.DockerWorkflowsExtension
import com.kineticfire.gradle.docker.spec.project.DockerProjectSpec
import com.kineticfire.gradle.docker.spec.project.ProjectImageSpec
import com.kineticfire.gradle.docker.spec.project.ProjectTestSpec
import com.kineticfire.gradle.docker.spec.project.ProjectSuccessSpec
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Copy

/**
 * Translates dockerProject spec into docker, dockerTest, and dockerWorkflows configurations.
 *
 * This translator implements the "facade pattern" where the simplified dockerProject DSL
 * is translated into the full three-DSL configuration that the plugin already supports.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: This translator runs during CONFIGURATION TIME only,
 * within the plugin's existing afterEvaluate block. It does NOT run at execution time.
 * All task dependency wiring uses provider-based lazy configuration via tasks.named().configure()
 * instead of additional afterEvaluate blocks.
 *
 * EXECUTION ORDER NOTE: This translator is called from the plugin's second afterEvaluate block
 * (in configureAfterEvaluation), AFTER the first afterEvaluate (in registerTaskCreationRules)
 * has already registered tasks. This ensures all referenced tasks exist when wiring dependencies.
 */
class DockerProjectTranslator {

    /**
     * Translate the dockerProject spec into the underlying DSL configurations.
     *
     * IMPORTANT: This method runs during configuration time (within afterEvaluate).
     * It configures extensions and wires task dependencies using provider-based patterns.
     *
     * @param project The Gradle project (configuration-time only)
     * @param projectSpec The dockerProject specification
     * @param dockerExt The docker extension to configure
     * @param dockerTestExt The dockerTest extension to configure
     * @param dockerWorkflowsExt The dockerWorkflows extension to configure
     */
    void translate(Project project, DockerProjectSpec projectSpec,
                   DockerExtension dockerExt, DockerTestExtension dockerTestExt,
                   DockerWorkflowsExtension dockerWorkflowsExt) {

        def imageSpec = projectSpec.image.get()
        def testSpec = projectSpec.test.get()
        def successSpec = projectSpec.onSuccess.get()
        def failureSpec = projectSpec.onFailure.get()

        // Validate configuration and extension availability
        validateSpec(projectSpec, dockerExt, dockerTestExt, dockerWorkflowsExt)

        // Generate sanitized names for internal use
        def imageName = deriveImageName(imageSpec, project)
        def sanitizedName = sanitizeName(imageName)
        def stackName = "${sanitizedName}Test"
        def pipelineName = "${sanitizedName}Pipeline"

        // 1. Configure docker.images
        configureDockerImage(project, dockerExt, imageSpec, sanitizedName)

        // 2. Configure dockerTest.composeStacks (if test block is configured)
        if (testSpec.compose.isPresent()) {
            configureComposeStack(project, dockerTestExt, testSpec, stackName)
        }

        // 3. Configure dockerWorkflows.pipelines
        configurePipeline(project, dockerWorkflowsExt, dockerExt, dockerTestExt,
                          sanitizedName, stackName, pipelineName, testSpec, successSpec, failureSpec)

        // 4. Configure task dependencies using provider-based wiring (no afterEvaluate)
        configureTaskDependencies(project, sanitizedName, stackName, testSpec)

        project.logger.lifecycle("dockerProject: Configured image '${imageName}' with pipeline '${pipelineName}'")
    }

    private void validateSpec(DockerProjectSpec projectSpec, DockerExtension dockerExt,
                              DockerTestExtension dockerTestExt, DockerWorkflowsExtension dockerWorkflowsExt) {
        // Validate all three extensions are available
        if (dockerExt == null) {
            throw new GradleException(
                "dockerProject requires the 'docker' extension to be registered. " +
                "Ensure the com.kineticfire.gradle.docker plugin is applied."
            )
        }
        if (dockerTestExt == null) {
            throw new GradleException(
                "dockerProject requires the 'dockerTest' extension to be registered. " +
                "Ensure the com.kineticfire.gradle.docker plugin is applied."
            )
        }
        if (dockerWorkflowsExt == null) {
            throw new GradleException(
                "dockerProject requires the 'dockerWorkflows' extension to be registered. " +
                "Ensure the com.kineticfire.gradle.docker plugin is applied."
            )
        }

        def imageSpec = projectSpec.image.get()

        // Must have either build mode or sourceRef mode configured
        if (!imageSpec.isBuildMode() && !imageSpec.isSourceRefMode()) {
            throw new GradleException(
                "dockerProject.image must specify either build properties (jarFrom or contextDir) " +
                "or sourceRef properties (sourceRef, sourceRefImageName, or sourceRefRepository)"
            )
        }

        // Cannot mix build mode and sourceRef mode
        if (imageSpec.isBuildMode() && imageSpec.isSourceRefMode()) {
            throw new GradleException(
                "dockerProject.image cannot mix build mode (jarFrom/contextDir) with sourceRef mode"
            )
        }

        // Cannot specify both jarFrom and contextDir
        def hasJarFrom = imageSpec.jarFrom.isPresent() && !imageSpec.jarFrom.get().isEmpty()
        def hasContextDir = imageSpec.contextDir.isPresent() && !imageSpec.contextDir.get().isEmpty()
        if (hasJarFrom && hasContextDir) {
            throw new GradleException(
                "dockerProject.image cannot specify both jarFrom and contextDir - use one or the other"
            )
        }

        // Validate mutual exclusivity: check for conflicting direct docker{} configuration
        def derivedImageName = imageSpec.name.isPresent() ? imageSpec.name.get() : ''
        def sanitizedName = sanitizeName(derivedImageName)
        if (!sanitizedName.isEmpty() && dockerExt.images.findByName(sanitizedName) != null) {
            throw new GradleException(
                "Image '${sanitizedName}' is already configured in docker.images { }. " +
                "Cannot use dockerProject { } and docker { } to configure the same image. " +
                "Use either dockerProject { } (simplified) OR docker { } (advanced), not both."
            )
        }
    }

    /**
     * Derive the image name from the ProjectImageSpec or fall back to project name.
     */
    String deriveImageName(ProjectImageSpec imageSpec, Project project) {
        if (imageSpec.name.isPresent() && !imageSpec.name.get().isEmpty()) {
            return imageSpec.name.get()
        }
        if (imageSpec.sourceRefImageName.isPresent() && !imageSpec.sourceRefImageName.get().isEmpty()) {
            return imageSpec.sourceRefImageName.get()
        }
        // Fall back to project name
        return project.name
    }

    /**
     * Sanitize name for use in task names and container names.
     * Reuses the same sanitization pattern as existing plugin code.
     */
    String sanitizeName(String name) {
        if (name == null || name.isEmpty()) {
            return ''
        }
        // Remove special characters, convert to camelCase-safe format
        return name.replaceAll('[^a-zA-Z0-9]', '')
                   .replaceFirst('^([A-Z])', { it[0].toLowerCase() })
    }

    private void configureDockerImage(Project project, DockerExtension dockerExt,
                                       ProjectImageSpec imageSpec, String sanitizedName) {
        dockerExt.images.create(sanitizedName) { image ->
            // Map ProjectImageSpec.name -> ImageSpec.imageName (the Docker image name)
            def derivedImageName = deriveImageName(imageSpec, project)
            if (imageSpec.name.isPresent() && !imageSpec.name.get().isEmpty()) {
                image.imageName.set(imageSpec.name)
            } else {
                // Fallback: use derived name as the Docker image name
                image.imageName.set(derivedImageName)
            }
            image.tags.set(imageSpec.tags)
            image.registry.set(imageSpec.registry)
            image.namespace.set(imageSpec.namespace)
            image.buildArgs.putAll(imageSpec.buildArgs)
            image.labels.putAll(imageSpec.labels)

            // Map version - derive from tags if not explicitly set
            def derivedVersion = imageSpec.deriveVersion()
            if (!derivedVersion.isEmpty()) {
                image.version.set(derivedVersion)
            }

            if (imageSpec.isBuildMode()) {
                // Build mode configuration
                if (imageSpec.jarFrom.isPresent() && !imageSpec.jarFrom.get().isEmpty()) {
                    def contextTaskProvider = createContextTask(project, sanitizedName, imageSpec)
                    image.contextTask = contextTaskProvider
                    // Also set contextTaskName for configuration cache compatibility
                    image.contextTaskName.set("prepare${sanitizedName.capitalize()}Context")
                    // NOTE: Do NOT set image.dockerfile here - the contextTask copies the Dockerfile
                    // to the context, and GradleDockerPlugin.configureDockerfile() will automatically
                    // resolve it from within the context when hasContextTask is true.
                } else if (imageSpec.contextDir.isPresent()) {
                    image.context.set(project.file(imageSpec.contextDir.get()))
                    // For contextDir mode (pre-existing context), set dockerfile if non-default
                    def defaultDockerfile = 'src/main/docker/Dockerfile'
                    if (imageSpec.dockerfile.isPresent() && imageSpec.dockerfile.get() != defaultDockerfile) {
                        image.dockerfile.set(project.file(imageSpec.dockerfile.get()))
                    }
                }
            } else {
                // SourceRef mode configuration
                if (imageSpec.sourceRef.isPresent() && !imageSpec.sourceRef.get().isEmpty()) {
                    image.sourceRef.set(imageSpec.sourceRef)
                } else {
                    image.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
                    image.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
                    image.sourceRefImageName.set(imageSpec.sourceRefImageName)
                    image.sourceRefTag.set(imageSpec.sourceRefTag)
                    image.sourceRefRepository.set(imageSpec.sourceRefRepository)
                }

                image.pullIfMissing.set(imageSpec.pullIfMissing)

                if (imageSpec.pullAuth != null) {
                    image.pullAuth {
                        username.set(imageSpec.pullAuth.username)
                        password.set(imageSpec.pullAuth.password)
                    }
                }
            }
        }
    }

    private def createContextTask(Project project, String imageName, ProjectImageSpec imageSpec) {
        def taskName = "prepare${imageName.capitalize()}Context"
        def jarTaskPath = imageSpec.jarFrom.get()

        // Validate jarFrom task path exists before attempting to resolve
        validateJarTaskPath(project, jarTaskPath)

        return project.tasks.register(taskName, Copy) { task ->
            task.group = 'docker'
            task.description = "Prepare Docker build context for ${imageName}"
            task.into(project.layout.buildDirectory.dir("docker-context/${imageName}"))

            // Copy Dockerfile
            def dockerfilePath = imageSpec.dockerfile.getOrElse('src/main/docker/Dockerfile')
            def dockerfileFile = project.file(dockerfilePath)
            task.from(dockerfileFile.parentFile) { spec ->
                spec.include(dockerfileFile.name)
            }

            // Copy JAR from specified task
            def jarTaskName
            def jarProject

            if (!jarTaskPath.contains(':')) {
                // Case 1: Simple task name like 'jar' - use current project
                jarTaskName = jarTaskPath
                jarProject = project
            } else if (jarTaskPath.startsWith(':')) {
                // Starts with ':' - either root project task or subproject task
                def pathWithoutLeadingColon = jarTaskPath.substring(1)
                def lastColonIndex = pathWithoutLeadingColon.lastIndexOf(':')

                if (lastColonIndex == -1) {
                    // Case 2: Root project reference like ':jar' (no more colons after first)
                    jarTaskName = pathWithoutLeadingColon
                    jarProject = project.rootProject
                } else {
                    // Case 3: Subproject reference like ':app:jar' or ':sub:project:jar'
                    jarTaskName = pathWithoutLeadingColon.substring(lastColonIndex + 1)
                    def projectPath = ':' + pathWithoutLeadingColon.substring(0, lastColonIndex)
                    jarProject = project.project(projectPath)
                }
            } else {
                // Invalid format like 'foo:jar' - should have been caught by validation
                throw new GradleException(
                    "Invalid jarFrom format '${jarTaskPath}'. " +
                    "Task paths must start with ':' for cross-project references or be a simple task name."
                )
            }

            def jarTask = jarProject.tasks.named(jarTaskName)
            task.from(jarTask.flatMap { it.archiveFile }) { spec ->
                spec.rename { 'app.jar' }
            }

            task.dependsOn(jarTaskPath)
        }
    }

    /**
     * Validate that the jarFrom task path references an existing project and task.
     * Provides clear error messages for common misconfigurations.
     *
     * Supported formats:
     * - 'jar' -> current project's jar task
     * - ':jar' -> root project's jar task
     * - ':app:jar' -> subproject :app's jar task
     * - ':sub:project:jar' -> nested subproject's jar task
     */
    void validateJarTaskPath(Project project, String jarTaskPath) {
        // Validate required plugins are applied
        if (!project.plugins.hasPlugin('java') && !project.plugins.hasPlugin('groovy')) {
            throw new GradleException(
                "dockerProject.image.jarFrom requires 'java' or 'groovy' plugin to be applied. " +
                "Add: plugins { id 'java' } or plugins { id 'groovy' } to your build.gradle"
            )
        }

        // Validate jarTaskPath is not empty
        if (jarTaskPath == null || jarTaskPath.trim().isEmpty()) {
            throw new GradleException(
                "dockerProject.image.jarFrom cannot be empty. " +
                "Provide a valid task path like 'jar', ':jar', or ':app:jar'"
            )
        }

        // Validate format - should end with a task name, not a colon
        if (jarTaskPath.endsWith(':')) {
            throw new GradleException(
                "dockerProject.image.jarFrom '${jarTaskPath}' is invalid. " +
                "Task path must end with a task name, not a colon."
            )
        }

        // Validate format - cross-project references must start with ':'
        if (jarTaskPath.contains(':') && !jarTaskPath.startsWith(':')) {
            throw new GradleException(
                "dockerProject.image.jarFrom '${jarTaskPath}' is invalid. " +
                "Cross-project task paths must start with ':'. " +
                "Use ':${jarTaskPath}' or a simple task name like 'jar'."
            )
        }

        // Validate cross-project references - check project exists
        if (jarTaskPath.startsWith(':') && jarTaskPath.indexOf(':', 1) > 0) {
            // Has colon after the first one, so it's a subproject reference like ':app:jar'
            def pathWithoutLeadingColon = jarTaskPath.substring(1)
            def lastColonIndex = pathWithoutLeadingColon.lastIndexOf(':')
            def projectPath = ':' + pathWithoutLeadingColon.substring(0, lastColonIndex)

            try {
                project.project(projectPath)
            } catch (Exception e) {
                throw new GradleException(
                    "dockerProject.image.jarFrom references non-existent project '${projectPath}'. " +
                    "Verify the project path in your jarFrom setting: '${jarTaskPath}'. " +
                    "Available subprojects: ${project.rootProject.subprojects.collect { it.path }}"
                )
            }
        }
        // Note: Task existence is validated lazily by Gradle when tasks.named() is called
    }

    private void configureComposeStack(Project project, DockerTestExtension dockerTestExt,
                                        ProjectTestSpec testSpec, String stackName) {
        dockerTestExt.composeStacks.create(stackName) { stack ->
            // Resolve compose file path and add to files collection
            stack.files.from(project.file(testSpec.compose.get()))

            if (testSpec.projectName.isPresent()) {
                stack.projectName.set(testSpec.projectName)
            } else {
                // Docker Compose project names must be lowercase alphanumeric with hyphens/underscores
                stack.projectName.set("${project.name}-${stackName}".toLowerCase())
            }

            if (testSpec.waitForHealthy.isPresent() && !testSpec.waitForHealthy.get().isEmpty()) {
                stack.waitForHealthy {
                    waitForServices.set(testSpec.waitForHealthy.get())
                    timeoutSeconds.set(testSpec.timeoutSeconds.get())
                    pollSeconds.set(testSpec.pollSeconds.get())
                }
            }

            if (testSpec.waitForRunning.isPresent() && !testSpec.waitForRunning.get().isEmpty()) {
                stack.waitForRunning {
                    waitForServices.set(testSpec.waitForRunning.get())
                    timeoutSeconds.set(testSpec.timeoutSeconds.get())
                    pollSeconds.set(testSpec.pollSeconds.get())
                }
            }
        }
    }

    private void configurePipeline(Project project, DockerWorkflowsExtension dockerWorkflowsExt,
                                    DockerExtension dockerExt, DockerTestExtension dockerTestExt,
                                    String imageName, String stackName, String pipelineName,
                                    ProjectTestSpec testSpec, ProjectSuccessSpec successSpec,
                                    def failureSpec) {
        dockerWorkflowsExt.pipelines.create(pipelineName) { pipeline ->
            pipeline.description.set("Auto-generated pipeline for ${imageName}")

            // Configure build step
            pipeline.build { buildStep ->
                buildStep.image.set(dockerExt.images.getByName(imageName))
            }

            if (testSpec.compose.isPresent()) {
                // Configure test step
                pipeline.test { testStep ->
                    testStep.testTaskName.set(testSpec.testTaskName.getOrElse('integrationTest'))

                    def lifecycle = testSpec.lifecycle.getOrElse(Lifecycle.CLASS)
                    def lifecycleValue = convertLifecycle(lifecycle)
                    testStep.lifecycle.set(lifecycleValue)

                    // When METHOD lifecycle, delegate stack management to test framework extension
                    // Don't set stack since the test framework extension handles compose lifecycle
                    if (lifecycleValue == WorkflowLifecycle.METHOD) {
                        testStep.delegateStackManagement.set(true)

                        project.logger.lifecycle(
                            "dockerProject: Using METHOD lifecycle for pipeline '${pipelineName}'. " +
                            "Ensure test task '${testSpec.testTaskName.getOrElse('integrationTest')}' " +
                            "has maxParallelForks = 1 and test classes use @ComposeUp (Spock) or " +
                            "@ExtendWith(DockerComposeMethodExtension.class) (JUnit 5)."
                        )
                    } else {
                        // Only set stack for CLASS or SUITE lifecycle where pipeline manages compose
                        testStep.stack.set(dockerTestExt.composeStacks.getByName(stackName))
                    }
                }
            }

            pipeline.onTestSuccess { successStep ->
                successStep.additionalTags.set(successSpec.additionalTags)

                if (successSpec.saveFile.isPresent()) {
                    successStep.save {
                        outputFile.set(project.file(successSpec.saveFile.get()))
                        compression.set(successSpec.inferCompression())
                    }
                }

                if (successSpec.publishRegistry.isPresent()) {
                    successStep.publish {
                        // Determine which tags to publish
                        def tagsToPublish = successSpec.publishTags.isPresent() &&
                            !successSpec.publishTags.get().isEmpty() ?
                            successSpec.publishTags.get() : successSpec.additionalTags.get()
                        publishTags.set(tagsToPublish)

                        // Use PublishSpec.to(String, Closure) API to create named target
                        to('default') {
                            registry.set(successSpec.publishRegistry)
                            if (successSpec.publishNamespace.isPresent()) {
                                namespace.set(successSpec.publishNamespace)
                            }
                        }
                    }
                }
            }

            if (failureSpec.additionalTags.isPresent() && !failureSpec.additionalTags.get().isEmpty()) {
                pipeline.onTestFailure { failureStep ->
                    failureStep.additionalTags.set(failureSpec.additionalTags)
                }
            }
        }
    }

    /**
     * Convert Lifecycle enum to WorkflowLifecycle enum.
     *
     * @param lifecycle The Lifecycle enum value
     * @return The corresponding WorkflowLifecycle enum value
     */
    WorkflowLifecycle convertLifecycle(Lifecycle lifecycle) {
        switch (lifecycle) {
            case Lifecycle.CLASS: return WorkflowLifecycle.CLASS
            case Lifecycle.METHOD: return WorkflowLifecycle.METHOD
            default:
                throw new GradleException(
                    "Unknown lifecycle '${lifecycle}'. Valid values: CLASS, METHOD"
                )
        }
    }

    /**
     * Parse lifecycle string to WorkflowLifecycle enum.
     * @deprecated Use convertLifecycle(Lifecycle) instead
     */
    @Deprecated
    WorkflowLifecycle parseLifecycle(String lifecycle) {
        switch (lifecycle.toLowerCase()) {
            case 'class': return WorkflowLifecycle.CLASS
            case 'method': return WorkflowLifecycle.METHOD
            default:
                throw new GradleException(
                    "Unknown lifecycle '${lifecycle}'. Valid values: class, method"
                )
        }
    }

    /**
     * Configure task dependencies using provider-based lazy wiring.
     *
     * GRADLE 9/10 COMPATIBILITY NOTE: This method does NOT use afterEvaluate.
     * Instead, it uses tasks.named().configure() which is configuration-cache safe.
     * This translator is called from the plugin's existing afterEvaluate block,
     * so tasks are already registered at this point.
     *
     * UNIT TESTING NOTE: In unit tests, the tasks may not exist because the plugin's
     * task registration happens in afterEvaluate. This method safely checks for task
     * existence before attempting to configure dependencies.
     */
    private void configureTaskDependencies(Project project, String imageName, String stackName,
                                            ProjectTestSpec testSpec) {
        def capitalizedImage = imageName.capitalize()
        def capitalizedStack = stackName.capitalize()

        if (testSpec.compose.isPresent()) {
            def composeUpTaskName = "composeUp${capitalizedStack}"
            def dockerBuildTaskName = "dockerBuild${capitalizedImage}"
            def composeDownTaskName = "composeDown${capitalizedStack}"
            def testTaskName = testSpec.testTaskName.getOrElse('integrationTest')

            // Determine if this is METHOD lifecycle (test framework handles compose)
            def lifecycle = testSpec.lifecycle.getOrElse(Lifecycle.CLASS)
            def isMethodLifecycle = lifecycle == Lifecycle.METHOD

            // Check if required tasks exist before configuring dependencies
            // In production, these tasks exist because this runs after afterEvaluate
            // In unit tests, these tasks may not exist
            if (project.tasks.findByName(composeUpTaskName) != null) {
                project.tasks.named(composeUpTaskName).configure { task ->
                    task.dependsOn(dockerBuildTaskName)
                }
            }

            if (project.tasks.findByName(testTaskName) != null) {
                project.tasks.named(testTaskName).configure { task ->
                    if (isMethodLifecycle) {
                        // METHOD lifecycle: test framework handles compose via @ComposeUp
                        // Configure system properties so @ComposeUp can read compose configuration
                        configureTestTaskSystemProperties(task, stackName, testSpec)
                        // Test task still depends on image being built
                        task.dependsOn(dockerBuildTaskName)
                    } else if (project.tasks.findByName(composeUpTaskName) != null) {
                        // CLASS lifecycle: pipeline handles compose up/down
                        task.dependsOn(composeUpTaskName)
                        task.finalizedBy(composeDownTaskName)
                    }
                }
            }
        }
    }

    /**
     * Configures system properties on the test task for @ComposeUp annotation to read.
     * Required for METHOD lifecycle where the test framework extension manages compose.
     *
     * @param task The test task to configure
     * @param stackName The compose stack name
     * @param testSpec The project test specification containing compose settings
     */
    private void configureTestTaskSystemProperties(def task, String stackName, ProjectTestSpec testSpec) {
        // Import needed for Test task type check
        if (task instanceof org.gradle.api.tasks.testing.Test) {
            def testTask = task as org.gradle.api.tasks.testing.Test

            // docker.compose.stack - stack name
            testTask.systemProperty('docker.compose.stack', stackName)

            // docker.compose.files - compose file paths (comma-separated)
            def composePath = testSpec.compose.get()
            testTask.systemProperty('docker.compose.files', composePath)

            // docker.compose.lifecycle - lifecycle mode
            testTask.systemProperty('docker.compose.lifecycle', 'method')

            // docker.compose.projectName - compose project name
            def projectName = testSpec.projectName.getOrElse(stackName)
            testTask.systemProperty('docker.compose.projectName', projectName)

            // docker.compose.waitForHealthy.services - services to wait for healthy
            if (testSpec.waitForHealthy.isPresent() && !testSpec.waitForHealthy.get().isEmpty()) {
                def services = testSpec.waitForHealthy.get().join(',')
                testTask.systemProperty('docker.compose.waitForHealthy.services', services)
            }

            // docker.compose.waitForRunning.services - services to wait for running
            if (testSpec.waitForRunning.isPresent() && !testSpec.waitForRunning.get().isEmpty()) {
                def services = testSpec.waitForRunning.get().join(',')
                testTask.systemProperty('docker.compose.waitForRunning.services', services)
            }

            // docker.compose.timeoutSeconds - timeout for wait operations
            if (testSpec.timeoutSeconds.isPresent()) {
                testTask.systemProperty('docker.compose.timeoutSeconds',
                    testSpec.timeoutSeconds.get().toString())
            }

            // docker.compose.pollSeconds - poll interval for wait operations
            if (testSpec.pollSeconds.isPresent()) {
                testTask.systemProperty('docker.compose.pollSeconds',
                    testSpec.pollSeconds.get().toString())
            }
        }
    }
}
