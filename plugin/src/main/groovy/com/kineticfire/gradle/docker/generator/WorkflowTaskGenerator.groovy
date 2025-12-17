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

import com.kineticfire.gradle.docker.extension.DockerWorkflowsExtension
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle
import com.kineticfire.gradle.docker.task.CleanupTask
import com.kineticfire.gradle.docker.task.DockerPublishTask
import com.kineticfire.gradle.docker.task.DockerSaveTask
import com.kineticfire.gradle.docker.task.TagOnSuccessTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Task generator for the dockerWorkflows DSL.
 *
 * This generator creates a configuration-cache-compatible task graph for each
 * pipeline defined in the dockerWorkflows extension. All tasks are registered
 * at configuration time with proper dependency wiring.
 *
 * Generated Task Graph (per pipeline):
 * <pre>
 * dockerBuild{ImageName} (existing task from docker DSL)
 *         ↓
 * composeUp{StackName} (if test.stack is configured)
 *         ↓
 * {testTaskName} (test task, e.g., integrationTest)
 *         ↓
 * composeDown{StackName}
 *         ↓
 * workflow{PipelineName}TagOnSuccess (conditional on test success)
 *         ↓
 * workflow{PipelineName}Save (optional, conditional)
 *         ↓
 * workflow{PipelineName}Publish (optional, conditional)
 *         ↓
 * workflow{PipelineName}Cleanup (always runs)
 *         ↓
 * run{PipelineName} (lifecycle task)
 * </pre>
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: This generator runs during configuration time
 * only. All task relationships are established using lazy providers and proper
 * task dependency wiring. No Project reference is captured for execution time.
 */
class WorkflowTaskGenerator extends TaskGraphGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowTaskGenerator)

    private static final String TASK_GROUP = 'docker workflows'

    /**
     * Main entry point for generating workflow task graphs.
     *
     * @param project The Gradle project
     * @param extension The dockerWorkflows extension containing pipeline configurations
     * @param dockerServiceProvider Provider for DockerService (injected at config time)
     */
    void generate(Project project, DockerWorkflowsExtension extension, Provider<DockerService> dockerServiceProvider) {
        // Always create runPipelines aggregate task (even if no pipelines configured)
        createRunPipelinesTask(project, extension)

        if (extension.pipelines.isEmpty()) {
            LOGGER.debug("No pipelines configured, skipping pipeline task generation")
            return
        }

        LOGGER.lifecycle("dockerWorkflows: Generating task graphs for {} pipeline(s)", extension.pipelines.size())

        // Generate tasks for each pipeline
        extension.pipelines.each { pipelineSpec ->
            generatePipelineTasks(project, pipelineSpec, dockerServiceProvider)
        }

        LOGGER.lifecycle("dockerWorkflows: Task graph generation complete")
    }

    /**
     * Generate task graph for a single pipeline.
     */
    private void generatePipelineTasks(
            Project project,
            PipelineSpec pipelineSpec,
            Provider<DockerService> dockerServiceProvider) {

        def pipelineName = pipelineSpec.name
        def prefix = TaskNamingUtils.workflowPrefix(pipelineName)
        def stateDir = TaskNamingUtils.stateDirectory(prefix)

        LOGGER.debug("Generating tasks for pipeline '{}'", pipelineName)

        // 1. Build step - just reference existing docker build task
        def buildTaskName = resolveBuildTaskName(pipelineSpec)

        // 2. Test step - configure test infrastructure
        def testSpec = pipelineSpec.test.get()
        def testTaskName = testSpec.testTaskName.getOrElse('integrationTest')
        def composeUpTaskName = null
        def composeDownTaskName = null

        if (testSpec.stack.isPresent()) {
            def stackName = testSpec.stack.get().name
            composeUpTaskName = TaskNamingUtils.composeUpTaskName(stackName)
            composeDownTaskName = TaskNamingUtils.composeDownTaskName(stackName)
        }

        // 3. Register tag on success task
        def tagOnSuccessTaskProvider = registerTagOnSuccessTask(
            project, pipelineSpec, prefix, stateDir, dockerServiceProvider
        )

        // 4. Register save task if configured
        def saveTaskProvider = registerSaveTask(
            project, pipelineSpec, prefix, stateDir, dockerServiceProvider
        )

        // 5. Register publish task if configured
        def publishTaskProvider = registerPublishTask(
            project, pipelineSpec, prefix, stateDir, dockerServiceProvider
        )

        // 6. Register cleanup task if configured
        def cleanupTaskProvider = registerCleanupTask(
            project, pipelineSpec, prefix, dockerServiceProvider
        )

        // 7. Configure test task to write result file
        configureTestResultCapture(project, testTaskName, stateDir)

        // 7a. Configure build hooks if present
        configureBuildHooks(project, pipelineSpec, buildTaskName)

        // 7b. Configure test hooks if present
        configureTestHooks(project, pipelineSpec, testTaskName, stateDir)

        // 8. Wire task dependencies
        wirePipelineDependencies(
            project, pipelineSpec, prefix,
            buildTaskName, composeUpTaskName, composeDownTaskName, testTaskName,
            tagOnSuccessTaskProvider, saveTaskProvider, publishTaskProvider, cleanupTaskProvider
        )

        // 9. Create pipeline lifecycle task
        createPipelineLifecycleTask(
            project, pipelineName, prefix, testTaskName,
            tagOnSuccessTaskProvider, saveTaskProvider, publishTaskProvider, cleanupTaskProvider
        )
    }

    /**
     * Resolve the build task name from the pipeline's build step.
     */
    private String resolveBuildTaskName(PipelineSpec pipelineSpec) {
        if (!pipelineSpec.build.isPresent()) {
            return null
        }

        def buildSpec = pipelineSpec.build.get()
        if (!buildSpec.image.isPresent()) {
            return null
        }

        def imageSpec = buildSpec.image.get()
        def imageName = imageSpec.name
        // Use the image name directly (preserve camelCase) to match how docker DSL creates tasks
        return TaskNamingUtils.buildTaskName(imageName)
    }

    /**
     * Configure build hooks (beforeBuild/afterBuild) on the build task.
     */
    private void configureBuildHooks(Project project, PipelineSpec pipelineSpec, String buildTaskName) {
        LOGGER.debug("configureBuildHooks: buildTaskName={}, exists={}", buildTaskName,
            buildTaskName != null ? taskExists(project, buildTaskName) : "null")

        if (buildTaskName == null || !taskExists(project, buildTaskName)) {
            LOGGER.debug("configureBuildHooks: skipping - task doesn't exist")
            return
        }

        if (!pipelineSpec.build.isPresent()) {
            LOGGER.debug("configureBuildHooks: skipping - build spec not present")
            return
        }

        def buildSpec = pipelineSpec.build.get()
        LOGGER.debug("configureBuildHooks: beforeBuild present={}, afterBuild present={}",
            buildSpec.beforeBuild.isPresent(), buildSpec.afterBuild.isPresent())

        project.tasks.named(buildTaskName).configure { task ->
            // Execute beforeBuild hook
            if (buildSpec.beforeBuild.isPresent()) {
                task.doFirst {
                    LOGGER.lifecycle("Executing beforeBuild hook for pipeline '{}'", pipelineSpec.name)
                    buildSpec.beforeBuild.get().execute(null)
                }
            }

            // Execute afterBuild hook
            if (buildSpec.afterBuild.isPresent()) {
                task.doLast {
                    LOGGER.lifecycle("Executing afterBuild hook for pipeline '{}'", pipelineSpec.name)
                    buildSpec.afterBuild.get().execute(null)
                }
            }
        }
    }

    /**
     * Configure test hooks (beforeTest/afterTest) on the test task.
     */
    private void configureTestHooks(Project project, PipelineSpec pipelineSpec, String testTaskName, String stateDir) {
        if (testTaskName == null || !taskExists(project, testTaskName)) {
            return
        }

        if (!pipelineSpec.test.isPresent()) {
            return
        }

        def testSpec = pipelineSpec.test.get()

        project.tasks.named(testTaskName).configure { task ->
            // Execute beforeTest hook
            if (testSpec.beforeTest.isPresent()) {
                task.doFirst {
                    LOGGER.lifecycle("Executing beforeTest hook for pipeline '{}'", pipelineSpec.name)
                    testSpec.beforeTest.get().execute(null)
                }
            }

            // Execute afterTest hook
            if (testSpec.afterTest.isPresent()) {
                task.doLast {
                    LOGGER.lifecycle("Executing afterTest hook for pipeline '{}'", pipelineSpec.name)
                    // Create a TestResult object from the test outcome
                    def success = task.state.failure == null
                    def testResult = success ?
                        com.kineticfire.gradle.docker.workflow.TestResult.success(1, 1, 0) :
                        com.kineticfire.gradle.docker.workflow.TestResult.failure(1, 1, 1, 0)
                    testSpec.afterTest.get().execute(testResult)
                }
            }
        }
    }

    /**
     * Register the tag on success task.
     */
    private TaskProvider<TagOnSuccessTask> registerTagOnSuccessTask(
            Project project,
            PipelineSpec pipelineSpec,
            String prefix,
            String stateDir,
            Provider<DockerService> dockerServiceProvider) {

        // Prefer onTestSuccess if it has non-empty additionalTags, otherwise fall back to onSuccess
        def testSuccessSpec = pipelineSpec.onTestSuccess.get()
        def generalSuccessSpec = pipelineSpec.onSuccess.get()

        def successSpec = !testSuccessSpec.additionalTags.get().isEmpty() ?
            testSuccessSpec : generalSuccessSpec

        def additionalTags = successSpec.additionalTags.get()
        if (additionalTags.isEmpty()) {
            LOGGER.debug("No additional tags configured for pipeline '{}', skipping TagOnSuccess task", pipelineSpec.name)
            return null
        }

        def taskName = TaskNamingUtils.tagOnSuccessTaskName(prefix)

        if (taskExists(project, taskName)) {
            return project.tasks.named(taskName, TagOnSuccessTask)
        }

        LOGGER.debug("Registering tag on success task: {}", taskName)

        def resultFile = project.layout.buildDirectory.file("${stateDir}/test-result.json")

        return project.tasks.register(taskName, TagOnSuccessTask) { task ->
            task.group = TASK_GROUP
            task.description = "Apply additional tags on test success for pipeline ${pipelineSpec.name}"

            task.dockerService.set(dockerServiceProvider)

            // Get image name from build spec
            if (pipelineSpec.build.isPresent() && pipelineSpec.build.get().image.isPresent()) {
                def imageSpec = pipelineSpec.build.get().image.get()
                def baseImageName = imageSpec.imageName.getOrElse(imageSpec.name)
                task.imageName.set(baseImageName)

                // For sourceRef mode, set the source image reference
                // Check if this is a sourceRef image (has sourceRef or sourceRefImageName set)
                def hasSourceRef = (imageSpec.sourceRef.isPresent() && !imageSpec.sourceRef.get().isEmpty()) ||
                                   (imageSpec.sourceRefImageName.isPresent() && !imageSpec.sourceRefImageName.get().isEmpty()) ||
                                   (imageSpec.sourceRefRepository.isPresent() && !imageSpec.sourceRefRepository.get().isEmpty())

                if (hasSourceRef) {
                    // Use the effective sourceRef as the source image to tag from
                    def effectiveSourceRef = imageSpec.getEffectiveSourceRef()
                    task.sourceImageRef.set(effectiveSourceRef)
                    LOGGER.debug("TagOnSuccess using sourceRef image: {}", effectiveSourceRef)
                }
            }

            task.additionalTags.set(additionalTags)
            task.testResultFile.set(resultFile)

            // Execute afterSuccess hook if configured
            if (successSpec.afterSuccess.isPresent()) {
                task.doLast {
                    LOGGER.lifecycle("Executing afterSuccess hook for pipeline '{}'", pipelineSpec.name)
                    successSpec.afterSuccess.get().execute(null)
                }
            }
        }
    }

    /**
     * Register the save task if configured.
     */
    private TaskProvider<DockerSaveTask> registerSaveTask(
            Project project,
            PipelineSpec pipelineSpec,
            String prefix,
            String stateDir,
            Provider<DockerService> dockerServiceProvider) {

        // Prefer onTestSuccess if it has save configured, otherwise fall back to onSuccess
        def testSuccessSpec = pipelineSpec.onTestSuccess.get()
        def generalSuccessSpec = pipelineSpec.onSuccess.get()

        def successSpec = testSuccessSpec.save.isPresent() ?
            testSuccessSpec : generalSuccessSpec

        if (!successSpec.save.isPresent()) {
            return null
        }

        def saveSpec = successSpec.save.get()
        if (!saveSpec.outputFile.isPresent()) {
            return null
        }

        def taskName = TaskNamingUtils.saveTaskName(prefix)

        if (taskExists(project, taskName)) {
            return project.tasks.named(taskName, DockerSaveTask)
        }

        LOGGER.debug("Registering save task: {}", taskName)

        def resultFile = project.layout.buildDirectory.file("${stateDir}/test-result.json")

        return project.tasks.register(taskName, DockerSaveTask) { task ->
            task.group = TASK_GROUP
            task.description = "Save Docker image to file for pipeline ${pipelineSpec.name}"

            task.dockerService.set(dockerServiceProvider)

            // Get image name and sourceRef info from build spec
            if (pipelineSpec.build.isPresent() && pipelineSpec.build.get().image.isPresent()) {
                def imageSpec = pipelineSpec.build.get().image.get()
                task.imageName.set(imageSpec.imageName.getOrElse(imageSpec.name))

                // Copy sourceRef properties for sourceRef mode images
                def hasSourceRef = (imageSpec.sourceRef.isPresent() && !imageSpec.sourceRef.get().isEmpty()) ||
                                   (imageSpec.sourceRefImageName.isPresent() && !imageSpec.sourceRefImageName.get().isEmpty()) ||
                                   (imageSpec.sourceRefRepository.isPresent() && !imageSpec.sourceRefRepository.get().isEmpty())

                if (hasSourceRef) {
                    task.sourceRef.set(imageSpec.sourceRef)
                    task.sourceRefRegistry.set(imageSpec.sourceRefRegistry)
                    task.sourceRefNamespace.set(imageSpec.sourceRefNamespace)
                    task.sourceRefImageName.set(imageSpec.sourceRefImageName)
                    task.sourceRefRepository.set(imageSpec.sourceRefRepository)
                    task.sourceRefTag.set(imageSpec.sourceRefTag)
                    task.effectiveSourceRef.set(imageSpec.getEffectiveSourceRef())
                    LOGGER.debug("Save task using sourceRef image: {}", imageSpec.getEffectiveSourceRef())
                }

                // Copy tags for building image reference
                task.tags.set(imageSpec.tags)
            }

            task.outputFile.set(saveSpec.outputFile)

            if (saveSpec.compression.isPresent()) {
                task.compression.set(saveSpec.compression)
            }

            // Add onlyIf based on test result
            task.onlyIf { t ->
                def file = resultFile.get().asFile
                if (!file.exists()) {
                    return false
                }
                return PipelineStateFile.isTestSuccessful(file)
            }
        }
    }

    /**
     * Register the publish task if configured.
     */
    private TaskProvider<DockerPublishTask> registerPublishTask(
            Project project,
            PipelineSpec pipelineSpec,
            String prefix,
            String stateDir,
            Provider<DockerService> dockerServiceProvider) {

        // Prefer onTestSuccess if it has publish configured, otherwise fall back to onSuccess
        def testSuccessSpec = pipelineSpec.onTestSuccess.get()
        def generalSuccessSpec = pipelineSpec.onSuccess.get()

        def successSpec = testSuccessSpec.publish.isPresent() ?
            testSuccessSpec : generalSuccessSpec

        if (!successSpec.publish.isPresent()) {
            return null
        }

        def publishSpec = successSpec.publish.get()
        if (publishSpec.to.isEmpty()) {
            return null
        }

        // For simplicity, register a single publish task that handles first target
        // A more complete implementation would register multiple tasks for each target
        def taskName = TaskNamingUtils.publishTaskName(prefix)

        if (taskExists(project, taskName)) {
            return project.tasks.named(taskName, DockerPublishTask)
        }

        LOGGER.debug("Registering publish task: {}", taskName)

        def resultFile = project.layout.buildDirectory.file("${stateDir}/test-result.json")

        return project.tasks.register(taskName, DockerPublishTask) { task ->
            task.group = TASK_GROUP
            task.description = "Publish Docker image for pipeline ${pipelineSpec.name}"

            task.dockerService.set(dockerServiceProvider)

            // Get image name from build spec
            if (pipelineSpec.build.isPresent() && pipelineSpec.build.get().image.isPresent()) {
                def imageSpec = pipelineSpec.build.get().image.get()
                task.imageName.set(imageSpec.imageName.getOrElse(imageSpec.name))

                // Use publish tags from publishSpec if available, otherwise fall back to image tags
                // This is used by EffectiveImageProperties as the source tags
                def specPublishTags = publishSpec.publishTags.getOrElse([])
                if (!specPublishTags.isEmpty()) {
                    task.tags.set(specPublishTags)
                    LOGGER.debug("Using publish tags from publishSpec: {}", specPublishTags)
                } else if (imageSpec.tags.isPresent() && !imageSpec.tags.get().isEmpty()) {
                    task.tags.set(imageSpec.tags)
                    LOGGER.debug("Using image tags as publish tags: {}", imageSpec.tags.get())
                }
            }

            // Set the publish targets from the spec - this is what DockerPublishTask.publishImage() uses
            def targetsList = publishSpec.to.toList()
            task.publishTargets.set(targetsList)
            LOGGER.debug("Registered publish task with {} targets", targetsList.size())

            // Add onlyIf based on test result
            task.onlyIf { t ->
                def file = resultFile.get().asFile
                if (!file.exists()) {
                    return false
                }
                return PipelineStateFile.isTestSuccessful(file)
            }
        }
    }

    /**
     * Register the cleanup task if configured.
     */
    private TaskProvider<CleanupTask> registerCleanupTask(
            Project project,
            PipelineSpec pipelineSpec,
            String prefix,
            Provider<DockerService> dockerServiceProvider) {

        if (!pipelineSpec.always.isPresent()) {
            return null
        }

        def alwaysSpec = pipelineSpec.always.get()

        // Only create cleanup task if at least one cleanup option is enabled
        def hasCleanupConfig = alwaysSpec.removeTestContainers.getOrElse(false) ||
                               alwaysSpec.cleanupImages.getOrElse(false)
        if (!hasCleanupConfig) {
            return null
        }

        def taskName = TaskNamingUtils.cleanupTaskName(prefix)

        if (taskExists(project, taskName)) {
            return project.tasks.named(taskName, CleanupTask)
        }

        LOGGER.debug("Registering cleanup task: {}", taskName)

        return project.tasks.register(taskName, CleanupTask) { task ->
            task.group = TASK_GROUP
            task.description = "Cleanup resources for pipeline ${pipelineSpec.name}"

            task.dockerService.set(dockerServiceProvider)

            // Configure from always spec using actual property names
            if (alwaysSpec.removeTestContainers.isPresent()) {
                task.removeContainers.set(alwaysSpec.removeTestContainers)
            }
            if (alwaysSpec.cleanupImages.isPresent()) {
                task.removeImages.set(alwaysSpec.cleanupImages)
            }
        }
    }

    /**
     * Configure test task to write result file.
     */
    private void configureTestResultCapture(Project project, String testTaskName, String stateDir) {
        if (!taskExists(project, testTaskName)) {
            LOGGER.debug("Test task '{}' not found, skipping result capture configuration", testTaskName)
            return
        }

        def resultFile = project.layout.buildDirectory.file("${stateDir}/test-result.json")

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

    /**
     * Wire all task dependencies for a pipeline.
     */
    private void wirePipelineDependencies(
            Project project,
            PipelineSpec pipelineSpec,
            String prefix,
            String buildTaskName,
            String composeUpTaskName,
            String composeDownTaskName,
            String testTaskName,
            TaskProvider<TagOnSuccessTask> tagOnSuccessTaskProvider,
            TaskProvider<DockerSaveTask> saveTaskProvider,
            TaskProvider<DockerPublishTask> publishTaskProvider,
            TaskProvider<CleanupTask> cleanupTaskProvider) {

        def testSpec = pipelineSpec.test.get()
        def lifecycle = testSpec.lifecycle.getOrElse(WorkflowLifecycle.CLASS)
        def delegateStackManagement = testSpec.delegateStackManagement.getOrElse(false)

        // If METHOD lifecycle or delegating stack management, don't wire compose tasks
        if (lifecycle == WorkflowLifecycle.METHOD || delegateStackManagement) {
            // Test depends on build
            if (buildTaskName != null && taskExists(project, buildTaskName) && taskExists(project, testTaskName)) {
                wireTaskDependency(project, testTaskName, buildTaskName)
            }
        } else {
            // CLASS lifecycle - wire compose tasks
            if (composeUpTaskName != null && taskExists(project, composeUpTaskName)) {
                // composeUp depends on build
                if (buildTaskName != null && taskExists(project, buildTaskName)) {
                    wireTaskDependency(project, composeUpTaskName, buildTaskName)
                }

                // Test depends on composeUp
                if (taskExists(project, testTaskName)) {
                    wireTaskDependency(project, testTaskName, composeUpTaskName)

                    // Test finalizedBy composeDown
                    if (composeDownTaskName != null && taskExists(project, composeDownTaskName)) {
                        wireFinalizedBy(project, testTaskName, composeDownTaskName)
                    }
                }
            } else if (buildTaskName != null && taskExists(project, buildTaskName) && taskExists(project, testTaskName)) {
                // No compose - test depends directly on build
                wireTaskDependency(project, testTaskName, buildTaskName)
            }
        }

        // Tag on success depends on test
        if (tagOnSuccessTaskProvider != null && taskExists(project, testTaskName)) {
            tagOnSuccessTaskProvider.configure { task ->
                task.dependsOn(testTaskName)
                task.mustRunAfter(testTaskName)
            }
        }

        // Save depends on tag on success
        if (saveTaskProvider != null && tagOnSuccessTaskProvider != null) {
            saveTaskProvider.configure { task ->
                task.dependsOn(tagOnSuccessTaskProvider)
            }
        }

        // Publish depends on save (if present) or tag on success
        if (publishTaskProvider != null) {
            publishTaskProvider.configure { task ->
                if (saveTaskProvider != null) {
                    task.dependsOn(saveTaskProvider)
                } else if (tagOnSuccessTaskProvider != null) {
                    task.dependsOn(tagOnSuccessTaskProvider)
                }
            }
        }

        // Cleanup runs after everything (finalizedBy)
        if (cleanupTaskProvider != null && taskExists(project, testTaskName)) {
            wireFinalizedBy(project, testTaskName, cleanupTaskProvider.name)
        }
    }

    /**
     * Create lifecycle task for a single pipeline.
     */
    private TaskProvider<Task> createPipelineLifecycleTask(
            Project project,
            String pipelineName,
            String prefix,
            String testTaskName,
            TaskProvider<TagOnSuccessTask> tagOnSuccessTaskProvider,
            TaskProvider<DockerSaveTask> saveTaskProvider,
            TaskProvider<DockerPublishTask> publishTaskProvider,
            TaskProvider<CleanupTask> cleanupTaskProvider) {

        def taskName = TaskNamingUtils.lifecycleTaskName(pipelineName)

        def lifecycleTask = getOrCreateLifecycleTask(
            project,
            taskName,
            TASK_GROUP,
            "Run the complete ${pipelineName} pipeline workflow"
        )

        lifecycleTask.configure { task ->
            // Depend on the last task in the pipeline chain
            // Success chain: tagOnSuccess → save → publish
            // If no success chain exists, depend on test task to ensure pipeline runs
            if (publishTaskProvider != null) {
                task.dependsOn(publishTaskProvider)
            } else if (saveTaskProvider != null) {
                task.dependsOn(saveTaskProvider)
            } else if (tagOnSuccessTaskProvider != null) {
                task.dependsOn(tagOnSuccessTaskProvider)
            } else if (testTaskName != null && taskExists(project, testTaskName)) {
                // No success/save/publish tasks - depend on test to ensure pipeline runs
                // This handles pipelines with just build+test and no onTestSuccess
                task.dependsOn(testTaskName)
            }

            // Cleanup should always run as a finalizer
            if (cleanupTaskProvider != null) {
                task.finalizedBy(cleanupTaskProvider)
            }
        }

        return lifecycleTask
    }

    /**
     * Create aggregate task that runs all pipelines.
     */
    private void createRunPipelinesTask(Project project, DockerWorkflowsExtension extension) {
        def taskName = 'runPipelines'

        def lifecycleTask = getOrCreateLifecycleTask(
            project,
            taskName,
            TASK_GROUP,
            "Run all configured docker workflows pipelines"
        )

        lifecycleTask.configure { task ->
            extension.pipelines.each { pipelineSpec ->
                def pipelineTaskName = TaskNamingUtils.lifecycleTaskName(pipelineSpec.name)
                if (taskExists(project, pipelineTaskName)) {
                    task.dependsOn(pipelineTaskName)
                }
            }
        }
    }
}
