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

package com.kineticfire.gradle.docker.workflow.executor

import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TaskLookup
import com.kineticfire.gradle.docker.workflow.TestResult
import com.kineticfire.gradle.docker.workflow.TestResultCapture
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.testing.Test

/**
 * Executor for the test step in a pipeline workflow.
 *
 * Orchestrates: composeUp → test task execution → result capture → composeDown (in finally block)
 * Updates the PipelineContext with test results.
 *
 * Configuration cache compatible - uses TaskLookup abstraction which is backed
 * by TaskExecutionService (a Gradle BuildService).
 */
class TestStepExecutor {

    private static final Logger LOGGER = Logging.getLogger(TestStepExecutor)

    private final TaskLookup taskLookup
    private final TestResultCapture resultCapture

    /**
     * Create executor with TaskLookup abstraction.
     *
     * @param taskLookup The task lookup for finding and executing tasks
     */
    TestStepExecutor(TaskLookup taskLookup) {
        this(taskLookup, new TestResultCapture())
    }

    /**
     * Create executor with TaskLookup and custom result capture.
     *
     * @param taskLookup The task lookup for finding and executing tasks
     * @param resultCapture The result capture for test results
     */
    TestStepExecutor(TaskLookup taskLookup, TestResultCapture resultCapture) {
        this.taskLookup = taskLookup
        this.resultCapture = resultCapture
    }

    /**
     * Execute the test step.
     *
     * @param testSpec The test step specification
     * @param context The current pipeline context
     * @return Updated pipeline context with test results
     * @throws GradleException if test execution fails or required configuration is missing
     */
    PipelineContext execute(TestStepSpec testSpec, PipelineContext context) {
        validateTestSpec(testSpec)

        def lifecycle = testSpec.lifecycle.getOrElse(WorkflowLifecycle.CLASS)
        def delegateStackManagement = testSpec.delegateStackManagement.getOrElse(false)
        def testTaskName = testSpec.testTaskName.get()

        // METHOD lifecycle forces delegation to test framework
        def shouldDelegateCompose = (lifecycle == WorkflowLifecycle.METHOD) || delegateStackManagement

        // Get stack name only when we manage lifecycle (stack is optional when delegating)
        def stackName = shouldDelegateCompose ? null : testSpec.stack.get().name

        if (lifecycle == WorkflowLifecycle.METHOD) {
            LOGGER.lifecycle("Executing test step with METHOD lifecycle, test task: {}", testTaskName)
        } else if (delegateStackManagement) {
            LOGGER.lifecycle("Executing test step with delegated stack management, test task: {}", testTaskName)
        } else {
            LOGGER.lifecycle("Executing test step for stack: {} with test task: {}", stackName, testTaskName)
        }

        // Look up the test task at execution time
        def testTask = lookupTask(testTaskName)
        if (testTask == null) {
            throw new GradleException("Test task '${testTaskName}' not found.")
        }

        // For METHOD lifecycle, set system properties so test framework knows what to do
        if (lifecycle == WorkflowLifecycle.METHOD) {
            def stackSpec = testSpec.stack.get()
            setSystemPropertiesForMethodLifecycle(testTask, stackSpec)
        }

        TestResult testResult = null
        Exception testException = null

        try {
            // Execute beforeTest hook if configured
            executeBeforeTestHook(testSpec)

            // Execute compose up only when we manage lifecycle (not when delegating or METHOD lifecycle)
            if (!shouldDelegateCompose) {
                def composeUpTaskName = computeComposeUpTaskName(stackName)
                executeComposeUpTask(composeUpTaskName)
            } else {
                LOGGER.info("Skipping composeUp - compose management delegated to test framework (lifecycle: {})",
                    lifecycle)
            }

            // Execute test task
            try {
                executeTestTask(testTask)
                testResult = resultCapture.captureFromTask(testTask)
            } catch (Exception e) {
                LOGGER.warn("Test task failed with exception: {}", e.message)
                testException = e
                testResult = resultCapture.captureFailure(testTask, e)
            }
        } finally {
            // Execute compose down only when we manage lifecycle (not when delegating or METHOD lifecycle)
            if (!shouldDelegateCompose) {
                def composeDownTaskName = computeComposeDownTaskName(stackName)
                executeComposeDownTask(composeDownTaskName)
            } else {
                LOGGER.info("Skipping composeDown - compose management delegated to test framework (lifecycle: {})",
                    lifecycle)
            }
        }

        // Execute afterTest hook if configured (receives TestResult)
        executeAfterTestHook(testSpec, testResult)

        // Re-throw exception after cleanup if test failed with exception
        if (testException != null) {
            throw new GradleException("Test execution failed: ${testException.message}", testException)
        }

        // Update context with test result
        return context.withTestResult(testResult)
    }

    /**
     * Validate the test step specification.
     *
     * When delegateStackManagement is true, stack is optional (lifecycle managed by testIntegration).
     * When delegateStackManagement is false (default), stack is required.
     */
    void validateTestSpec(TestStepSpec testSpec) {
        if (testSpec == null) {
            throw new GradleException("TestStepSpec cannot be null")
        }
        if (!testSpec.testTaskName.isPresent()) {
            throw new GradleException("TestStepSpec.testTaskName must be configured")
        }

        def delegateStackManagement = testSpec.delegateStackManagement.getOrElse(false)

        // Stack is required only when we manage lifecycle (delegateStackManagement = false)
        if (!delegateStackManagement && !testSpec.stack.isPresent()) {
            throw new GradleException("TestStepSpec.stack must be configured when delegateStackManagement is false")
        }
    }

    /**
     * Compute the compose up task name for a stack.
     * Follows the pattern: composeUp{StackName} where StackName is capitalized.
     */
    String computeComposeUpTaskName(String stackName) {
        def capitalizedName = capitalizeFirstLetter(stackName)
        return "composeUp${capitalizedName}"
    }

    /**
     * Compute the compose down task name for a stack.
     * Follows the pattern: composeDown{StackName} where StackName is capitalized.
     */
    String computeComposeDownTaskName(String stackName) {
        def capitalizedName = capitalizeFirstLetter(stackName)
        return "composeDown${capitalizedName}"
    }

    /**
     * Capitalize the first letter of a string.
     */
    String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1)
    }

    /**
     * Execute the compose up task.
     */
    void executeComposeUpTask(String taskName) {
        LOGGER.info("Looking up composeUp task: {}", taskName)
        def task = lookupTask(taskName)
        if (task == null) {
            throw new GradleException("ComposeUp task '${taskName}' not found. " +
                "Ensure the stack is configured in dockerTest.composeStacks DSL.")
        }

        LOGGER.info("Executing composeUp task: {}", taskName)
        executeTask(task)
        LOGGER.lifecycle("ComposeUp task {} completed successfully", taskName)
    }

    /**
     * Execute the compose down task.
     */
    void executeComposeDownTask(String taskName) {
        LOGGER.info("Looking up composeDown task: {}", taskName)
        def task = lookupTask(taskName)
        if (task == null) {
            LOGGER.warn("ComposeDown task '{}' not found - cleanup may be incomplete", taskName)
            return
        }

        LOGGER.info("Executing composeDown task: {}", taskName)
        try {
            executeTask(task)
            LOGGER.lifecycle("ComposeDown task {} completed successfully", taskName)
        } catch (Exception e) {
            LOGGER.error("ComposeDown task {} failed: {} - cleanup may be incomplete", taskName, e.message)
        }
    }

    /**
     * Execute the test task.
     */
    void executeTestTask(Task testTask) {
        LOGGER.info("Executing test task: {}", testTask.name)
        executeTask(testTask)
        LOGGER.lifecycle("Test task {} completed", testTask.name)
    }

    /**
     * Look up a task by name.
     * Separated for testability.
     */
    Task lookupTask(String taskName) {
        return taskLookup.findByName(taskName)
    }

    /**
     * Execute a task's actions.
     * Separated for testability.
     */
    void executeTask(Task task) {
        taskLookup.execute(task)
    }

    /**
     * Execute the beforeTest hook if configured.
     */
    void executeBeforeTestHook(TestStepSpec testSpec) {
        if (testSpec.beforeTest.isPresent()) {
            LOGGER.info("Executing beforeTest hook")
            executeHook(testSpec.beforeTest.get())
        }
    }

    /**
     * Execute the afterTest hook if configured (receives TestResult).
     */
    void executeAfterTestHook(TestStepSpec testSpec, TestResult testResult) {
        if (testSpec.afterTest.isPresent()) {
            LOGGER.info("Executing afterTest hook with result: {}", testResult)
            testSpec.afterTest.get().execute(testResult)
        }
    }

    /**
     * Execute a hook action.
     * Separated for testability.
     */
    void executeHook(Action<Void> hook) {
        hook.execute(null)
    }

    /**
     * Set system properties on the test task for METHOD lifecycle.
     *
     * <p>These system properties are read by the test framework extension (@ComposeUp for Spock,
     * @ExtendWith for JUnit 5) to configure per-method compose lifecycle.</p>
     *
     * <p>Configuration cache compatible: Uses Test.systemProperty() API which stores values
     * as serializable properties.</p>
     *
     * @param testTask The test task to configure
     * @param stackSpec The compose stack specification containing files and configuration
     */
    void setSystemPropertiesForMethodLifecycle(Task testTask, ComposeStackSpec stackSpec) {
        if (!(testTask instanceof Test)) {
            LOGGER.warn("Test task '{}' is not a Test task - cannot set system properties for METHOD lifecycle. " +
                "Test framework extension may not receive compose configuration.", testTask.name)
            return
        }

        def testTaskTyped = testTask as Test

        LOGGER.info("Setting system properties for METHOD lifecycle on test task: {}", testTask.name)

        // Core lifecycle configuration
        testTaskTyped.systemProperty('docker.compose.lifecycle', 'method')
        testTaskTyped.systemProperty('docker.compose.stack', stackSpec.name)

        // Compose files - collect from files property
        def composeFilePaths = collectComposeFilePaths(stackSpec)
        if (!composeFilePaths.isEmpty()) {
            testTaskTyped.systemProperty('docker.compose.files', composeFilePaths.join(','))
        }

        // Project name (optional)
        if (stackSpec.projectName.isPresent()) {
            testTaskTyped.systemProperty('docker.compose.projectName', stackSpec.projectName.get())
        }

        // Wait for healthy configuration (optional)
        if (stackSpec.waitForHealthy.isPresent()) {
            def waitForHealthy = stackSpec.waitForHealthy.get()
            setWaitSpecSystemProperties(testTaskTyped, 'docker.compose.waitForHealthy', waitForHealthy)
        }

        // Wait for running configuration (optional)
        if (stackSpec.waitForRunning.isPresent()) {
            def waitForRunning = stackSpec.waitForRunning.get()
            setWaitSpecSystemProperties(testTaskTyped, 'docker.compose.waitForRunning', waitForRunning)
        }
    }

    /**
     * Collect compose file paths from the stack specification.
     *
     * @param stackSpec The compose stack specification
     * @return List of absolute file paths, or empty list if no files configured
     */
    List<String> collectComposeFilePaths(ComposeStackSpec stackSpec) {
        def paths = []

        // Collect from files ConfigurableFileCollection
        if (stackSpec.files != null && !stackSpec.files.isEmpty()) {
            stackSpec.files.each { file ->
                paths << file.absolutePath
            }
        }

        // Also check composeFile if set (single file property)
        if (stackSpec.composeFile.isPresent()) {
            paths << stackSpec.composeFile.get().asFile.absolutePath
        }

        // Also check composeFileCollection
        if (stackSpec.composeFileCollection != null && !stackSpec.composeFileCollection.isEmpty()) {
            stackSpec.composeFileCollection.each { file ->
                if (!paths.contains(file.absolutePath)) {
                    paths << file.absolutePath
                }
            }
        }

        return paths
    }

    /**
     * Set wait specification system properties on a test task.
     *
     * @param testTask The test task to configure
     * @param prefix The property prefix (e.g., 'docker.compose.waitForHealthy')
     * @param waitSpec The wait specification
     */
    void setWaitSpecSystemProperties(Test testTask, String prefix, def waitSpec) {
        if (waitSpec.waitForServices != null && waitSpec.waitForServices.isPresent()) {
            def services = waitSpec.waitForServices.get()
            if (!services.isEmpty()) {
                testTask.systemProperty("${prefix}.services", services.join(','))
            }
        }

        if (waitSpec.timeoutSeconds != null && waitSpec.timeoutSeconds.isPresent()) {
            testTask.systemProperty("${prefix}.timeoutSeconds", waitSpec.timeoutSeconds.get().toString())
        }

        if (waitSpec.pollSeconds != null && waitSpec.pollSeconds.isPresent()) {
            testTask.systemProperty("${prefix}.pollSeconds", waitSpec.pollSeconds.get().toString())
        }
    }
}
