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

import com.kineticfire.gradle.docker.service.ComposeService
import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TestResult
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

    private final SuccessStepExecutor successStepExecutor
    private final FailureStepExecutor failureStepExecutor

    ConditionalExecutor() {
        this.successStepExecutor = new SuccessStepExecutor()
        this.failureStepExecutor = new FailureStepExecutor()
    }

    /**
     * Constructor for dependency injection (testing)
     */
    ConditionalExecutor(SuccessStepExecutor successStepExecutor, FailureStepExecutor failureStepExecutor) {
        this.successStepExecutor = successStepExecutor
        this.failureStepExecutor = failureStepExecutor
    }

    /**
     * Set the DockerService for both success and failure executors
     */
    void setDockerService(DockerService dockerService) {
        successStepExecutor.setDockerService(dockerService)
        failureStepExecutor.setDockerService(dockerService)
    }

    /**
     * Set the ComposeService for failure executor (log capture)
     */
    void setComposeService(ComposeService composeService) {
        failureStepExecutor.setComposeService(composeService)
    }

    /**
     * Set the Compose project name for failure executor
     */
    void setComposeProjectName(String projectName) {
        failureStepExecutor.setComposeProjectName(projectName)
    }

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
        return successStepExecutor.execute(successSpec, context)
    }

    /**
     * Execute the failure path operations
     *
     * @param failureSpec The failure step specification
     * @param context The current pipeline context
     * @return Updated pipeline context
     */
    PipelineContext executeFailurePath(FailureStepSpec failureSpec, PipelineContext context) {
        return failureStepExecutor.execute(failureSpec, context)
    }

}
