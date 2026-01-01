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

package com.kineticfire.gradle.docker.workflow

import groovy.transform.Immutable

/**
 * Context data passed to hook callbacks during pipeline execution.
 *
 * <p>Provides useful information about the current execution context to hook implementations,
 * enabling better debugging and conditional logic within hooks.</p>
 *
 * <h4>Usage Example</h4>
 * <pre>{@code
 * dockerWorkflows {
 *     pipelines {
 *         ci {
 *             build {
 *                 image = docker.images.myApp
 *                 beforeBuild { HookContext ctx ->
 *                     println "Starting build at ${ctx.timestamp} for pipeline '${ctx.pipelineName}'"
 *                 }
 *                 afterBuild { HookContext ctx ->
 *                     println "Build completed for task '${ctx.taskName}'"
 *                 }
 *             }
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
 * @see com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
 * @see com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
 */
@Immutable
class HookContext {

    /**
     * The name of the task being executed when the hook is invoked.
     * For example: "dockerBuildMyApp", "integrationTest", "workflowCiTagOnSuccess"
     */
    String taskName

    /**
     * The name of the pipeline this hook belongs to.
     * Corresponds to the pipeline name defined in dockerWorkflows.pipelines { }
     */
    String pipelineName

    /**
     * The timestamp (in milliseconds since epoch) when the hook was invoked.
     * Useful for timing and debugging.
     */
    long timestamp

    /**
     * The phase of execution - either "before" or "after".
     * Indicates whether the hook is running before or after the main task action.
     */
    String phase

    /**
     * Create a HookContext for a "before" phase hook.
     *
     * @param taskName The name of the task being executed
     * @param pipelineName The name of the pipeline
     * @return A new HookContext with phase="before" and current timestamp
     */
    static HookContext before(String taskName, String pipelineName) {
        return new HookContext(
            taskName: taskName,
            pipelineName: pipelineName,
            timestamp: System.currentTimeMillis(),
            phase: 'before'
        )
    }

    /**
     * Create a HookContext for an "after" phase hook.
     *
     * @param taskName The name of the task being executed
     * @param pipelineName The name of the pipeline
     * @return A new HookContext with phase="after" and current timestamp
     */
    static HookContext after(String taskName, String pipelineName) {
        return new HookContext(
            taskName: taskName,
            pipelineName: pipelineName,
            timestamp: System.currentTimeMillis(),
            phase: 'after'
        )
    }

    /**
     * Create a HookContext with a specific timestamp (useful for testing).
     *
     * @param taskName The name of the task being executed
     * @param pipelineName The name of the pipeline
     * @param timestamp The timestamp in milliseconds since epoch
     * @param phase The phase ("before" or "after")
     * @return A new HookContext with the specified values
     */
    static HookContext of(String taskName, String pipelineName, long timestamp, String phase) {
        return new HookContext(
            taskName: taskName,
            pipelineName: pipelineName,
            timestamp: timestamp,
            phase: phase
        )
    }
}
