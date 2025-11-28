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

import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.executor.AlwaysStepExecutor
import com.kineticfire.gradle.docker.workflow.executor.BuildStepExecutor
import com.kineticfire.gradle.docker.workflow.executor.ConditionalExecutor
import com.kineticfire.gradle.docker.workflow.executor.TestStepExecutor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import javax.inject.Inject

/**
 * Task that executes a complete pipeline workflow
 *
 * Orchestrates the execution of: build → test → conditional (success/failure) → always (cleanup)
 * Uses executor pattern for each step to maintain separation of concerns.
 *
 * NOTE: This task is NOT compatible with Gradle's configuration cache because it:
 * 1. Needs to look up and execute other tasks dynamically at execution time
 * 2. Uses TaskContainer which cannot be serialized
 *
 * This is a fundamental limitation of the pipeline orchestration pattern.
 * The task explicitly opts out of configuration cache.
 */
abstract class PipelineRunTask extends DefaultTask {

    private static final Logger LOGGER = Logging.getLogger(PipelineRunTask)

    // Executors are created lazily at execution time, not at configuration time
    private BuildStepExecutor buildStepExecutor
    private TestStepExecutor testStepExecutor
    private ConditionalExecutor conditionalExecutor
    private AlwaysStepExecutor alwaysStepExecutor

    // Flag to track if executors were explicitly set (for testing)
    private boolean executorsInjected = false

    // TaskContainer captured at configuration time
    // This is set by the plugin when registering the task
    private TaskContainer taskContainer

    @Inject
    PipelineRunTask() {
        description = 'Executes a complete pipeline workflow'
        group = 'docker workflows'

        // This task is NOT compatible with configuration cache because it needs to
        // dynamically look up and execute other tasks at runtime
        notCompatibleWithConfigurationCache(
            "PipelineRunTask requires TaskContainer access which is not configuration cache compatible"
        )

        // Note: Executors are NOT created here - they're created lazily in runPipeline()
    }

    @Internal
    abstract Property<PipelineSpec> getPipelineSpec()

    @Input
    abstract Property<String> getPipelineName()

    /**
     * Inject custom executors for testing
     */
    void setBuildStepExecutor(BuildStepExecutor executor) {
        this.buildStepExecutor = executor
        this.executorsInjected = true
    }

    void setTestStepExecutor(TestStepExecutor executor) {
        this.testStepExecutor = executor
        this.executorsInjected = true
    }

    void setConditionalExecutor(ConditionalExecutor executor) {
        this.conditionalExecutor = executor
        this.executorsInjected = true
    }

    void setAlwaysStepExecutor(AlwaysStepExecutor executor) {
        this.alwaysStepExecutor = executor
        this.executorsInjected = true
    }

    /**
     * Set the TaskContainer for configuration cache compatibility.
     * This must be called during configuration time by the plugin.
     */
    void setTaskContainer(TaskContainer taskContainer) {
        this.taskContainer = taskContainer
    }

    /**
     * Initialize executors at execution time
     *
     * This method creates the executors lazily when the task runs, not during configuration.
     * The TaskContainer is captured at configuration time and stored in taskContainer field.
     * This is required for configuration cache compatibility.
     */
    private void initializeExecutors() {
        if (executorsInjected) {
            // Executors were set explicitly (e.g., for testing) - don't override
            return
        }

        if (taskContainer == null) {
            throw new GradleException("TaskContainer was not set. " +
                "Ensure the plugin sets taskContainer during task configuration.")
        }

        // Create executors with the TaskContainer captured at configuration time
        if (buildStepExecutor == null) {
            buildStepExecutor = new BuildStepExecutor(taskContainer)
        }
        if (testStepExecutor == null) {
            testStepExecutor = new TestStepExecutor(taskContainer)
        }
        if (conditionalExecutor == null) {
            conditionalExecutor = new ConditionalExecutor()
        }
        if (alwaysStepExecutor == null) {
            alwaysStepExecutor = new AlwaysStepExecutor()
        }
    }

    @TaskAction
    void runPipeline() {
        validatePipelineSpec()

        // Create executors lazily at execution time for configuration cache compatibility
        // Only create if not already set (e.g., by tests)
        initializeExecutors()

        def spec = pipelineSpec.get()
        def name = pipelineName.get()
        LOGGER.lifecycle("Starting pipeline: {} - {}", name, spec.description.getOrElse(""))

        def context = PipelineContext.create(name)
        Exception pipelineException = null

        try {
            context = executeBuildStep(spec, context)
            context = executeTestStep(spec, context)
            context = executeConditionalStep(spec, context)
        } catch (Exception e) {
            LOGGER.error("Pipeline {} failed: {}", name, e.message)
            pipelineException = e
        } finally {
            executeAlwaysStep(spec, context)
        }

        if (pipelineException != null) {
            throw new GradleException("Pipeline '${name}' failed: ${pipelineException.message}", pipelineException)
        }

        LOGGER.lifecycle("Pipeline {} completed successfully", name)
    }

    /**
     * Validate that the pipeline spec is configured
     */
    void validatePipelineSpec() {
        if (!pipelineSpec.isPresent()) {
            throw new GradleException("PipelineSpec must be configured")
        }
        if (!pipelineName.isPresent()) {
            throw new GradleException("Pipeline name must be configured")
        }
    }

    /**
     * Execute the build step if configured
     */
    PipelineContext executeBuildStep(PipelineSpec spec, PipelineContext context) {
        def buildSpec = spec.build.getOrNull()
        if (buildSpec == null || !isBuildStepConfigured(buildSpec)) {
            LOGGER.info("No build step configured - skipping")
            return context
        }

        LOGGER.lifecycle("Executing build step")
        return buildStepExecutor.execute(buildSpec, context)
    }

    /**
     * Check if the build step has an image configured
     */
    boolean isBuildStepConfigured(BuildStepSpec buildSpec) {
        return buildSpec.image.isPresent()
    }

    /**
     * Execute the test step if configured
     */
    PipelineContext executeTestStep(PipelineSpec spec, PipelineContext context) {
        def testSpec = spec.test.getOrNull()
        if (testSpec == null || !isTestStepConfigured(testSpec)) {
            LOGGER.info("No test step configured - skipping")
            return context
        }

        LOGGER.lifecycle("Executing test step")
        return testStepExecutor.execute(testSpec, context)
    }

    /**
     * Check if the test step has required configuration
     */
    boolean isTestStepConfigured(TestStepSpec testSpec) {
        return testSpec.stack.isPresent() && testSpec.testTaskName.isPresent()
    }

    /**
     * Execute the conditional step (success or failure path) based on test results
     */
    PipelineContext executeConditionalStep(PipelineSpec spec, PipelineContext context) {
        if (!context.testCompleted) {
            LOGGER.info("No test results available - skipping conditional step")
            return context
        }

        def testResult = context.testResult
        def successSpec = getSuccessSpec(spec)
        def failureSpec = getFailureSpec(spec)

        LOGGER.lifecycle("Executing conditional step based on test result: success={}", testResult?.success)
        return conditionalExecutor.executeConditional(testResult, successSpec, failureSpec, context)
    }

    /**
     * Get the success spec from either onTestSuccess or onSuccess
     */
    SuccessStepSpec getSuccessSpec(PipelineSpec spec) {
        if (spec.onTestSuccess.isPresent()) {
            return spec.onTestSuccess.get()
        }
        if (spec.onSuccess.isPresent()) {
            return spec.onSuccess.get()
        }
        return null
    }

    /**
     * Get the failure spec from either onTestFailure or onFailure
     */
    FailureStepSpec getFailureSpec(PipelineSpec spec) {
        if (spec.onTestFailure.isPresent()) {
            return spec.onTestFailure.get()
        }
        if (spec.onFailure.isPresent()) {
            return spec.onFailure.get()
        }
        return null
    }

    /**
     * Execute the always step for cleanup operations
     */
    void executeAlwaysStep(PipelineSpec spec, PipelineContext context) {
        def alwaysSpec = spec.always.getOrNull()
        if (alwaysSpec == null) {
            LOGGER.info("No always step configured - skipping cleanup")
            return
        }

        LOGGER.lifecycle("Executing always (cleanup) step")
        def testsPassed = context.testCompleted && context.isTestSuccessful()
        try {
            alwaysStepExecutor.execute(alwaysSpec, context, testsPassed)
        } catch (Exception e) {
            LOGGER.error("Cleanup failed: {} - resources may not be fully cleaned up", e.message)
        }
    }
}
