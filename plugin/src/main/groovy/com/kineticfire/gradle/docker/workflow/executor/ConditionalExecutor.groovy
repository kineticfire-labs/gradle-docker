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

import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TestResult
import org.gradle.api.Action
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Executor for conditional logic that routes to success or failure paths based on test results
 *
 * Evaluates test results and executes the appropriate path (success or failure operations).
 * This is the core component that enables build → test → conditional publish workflows.
 */
class ConditionalExecutor {

    private static final Logger LOGGER = Logging.getLogger(ConditionalExecutor)

    /**
     * Execute conditional logic based on test results
     *
     * @param testResult The test execution result
     * @param successSpec The success step specification (may be null)
     * @param failureSpec The failure step specification (may be null)
     * @param context The current pipeline context
     * @return Updated pipeline context after conditional execution
     */
    PipelineContext executeConditional(
            TestResult testResult,
            SuccessStepSpec successSpec,
            FailureStepSpec failureSpec,
            PipelineContext context) {

        if (testResult == null) {
            LOGGER.warn("Test result is null - skipping conditional execution")
            return context
        }

        LOGGER.lifecycle("Evaluating test result: success={}, failures={}, total={}",
            testResult.success, testResult.failureCount, testResult.totalCount)

        if (testResult.success) {
            LOGGER.lifecycle("Tests passed - executing success path")
            return executeSuccessPath(successSpec, context)
        } else {
            LOGGER.lifecycle("Tests failed - executing failure path")
            return executeFailurePath(failureSpec, context)
        }
    }

    /**
     * Execute the success path operations
     *
     * @param successSpec The success step specification
     * @param context The current pipeline context
     * @return Updated pipeline context
     */
    PipelineContext executeSuccessPath(SuccessStepSpec successSpec, PipelineContext context) {
        if (successSpec == null) {
            LOGGER.info("No success spec configured - skipping success operations")
            return context
        }

        LOGGER.info("Executing success path operations")

        // Apply additional tags if configured
        if (hasAdditionalTags(successSpec)) {
            def tags = successSpec.additionalTags.get()
            LOGGER.info("Applying additional tags: {}", tags)
            context = context.withAppliedTags(tags)
        }

        // Save operation placeholder (to be implemented in Step 7)
        if (successSpec.save.isPresent()) {
            LOGGER.info("Save operation configured - will be executed by SaveOperationExecutor")
        }

        // Publish operation placeholder (to be implemented in Step 8)
        if (successSpec.publish.isPresent()) {
            LOGGER.info("Publish operation configured - will be executed by PublishOperationExecutor")
        }

        // Execute afterSuccess hook if configured
        executeAfterSuccessHook(successSpec)

        LOGGER.lifecycle("Success path completed")
        return context
    }

    /**
     * Execute the failure path operations
     *
     * @param failureSpec The failure step specification
     * @param context The current pipeline context
     * @return Updated pipeline context
     */
    PipelineContext executeFailurePath(FailureStepSpec failureSpec, PipelineContext context) {
        if (failureSpec == null) {
            LOGGER.info("No failure spec configured - skipping failure operations")
            return context
        }

        LOGGER.info("Executing failure path operations")

        // Apply failure tags if configured
        if (hasAdditionalTags(failureSpec)) {
            def tags = failureSpec.additionalTags.get()
            LOGGER.info("Applying failure tags: {}", tags)
            context = context.withAppliedTags(tags)
        }

        // Save failure logs placeholder (to be implemented in Step 9)
        if (failureSpec.saveFailureLogsDir.isPresent()) {
            LOGGER.info("Failure logs directory configured: {}",
                failureSpec.saveFailureLogsDir.get().asFile.absolutePath)
        }

        // Execute afterFailure hook if configured
        executeAfterFailureHook(failureSpec)

        LOGGER.lifecycle("Failure path completed")
        return context
    }

    /**
     * Check if success spec has additional tags configured
     */
    boolean hasAdditionalTags(SuccessStepSpec successSpec) {
        return successSpec.additionalTags.isPresent() && !successSpec.additionalTags.get().isEmpty()
    }

    /**
     * Check if failure spec has additional tags configured
     */
    boolean hasAdditionalTags(FailureStepSpec failureSpec) {
        return failureSpec.additionalTags.isPresent() && !failureSpec.additionalTags.get().isEmpty()
    }

    /**
     * Execute the afterSuccess hook if configured
     */
    void executeAfterSuccessHook(SuccessStepSpec successSpec) {
        if (successSpec.afterSuccess.isPresent()) {
            LOGGER.info("Executing afterSuccess hook")
            executeHook(successSpec.afterSuccess.get())
        }
    }

    /**
     * Execute the afterFailure hook if configured
     */
    void executeAfterFailureHook(FailureStepSpec failureSpec) {
        if (failureSpec.afterFailure.isPresent()) {
            LOGGER.info("Executing afterFailure hook")
            executeHook(failureSpec.afterFailure.get())
        }
    }

    /**
     * Execute a hook action
     * Separated for testability
     */
    void executeHook(Action<Void> hook) {
        hook.execute(null)
    }
}
