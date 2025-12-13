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

package com.kineticfire.gradle.docker.spec.workflow

/**
 * Lifecycle modes for Docker Compose orchestration in pipeline test steps.
 *
 * <p>Determines when containers start and stop during test execution within a
 * {@code dockerWorkflows} pipeline. This affects how the pipeline manages the
 * compose stack lifecycle relative to test method execution.</p>
 *
 * <h3>Lifecycle Comparison</h3>
 * <table border="1">
 *   <tr><th>Lifecycle</th><th>Containers Start</th><th>Containers Stop</th><th>Use Case</th></tr>
 *   <tr><td>{@link #CLASS}</td><td>Before all tests</td><td>After all tests</td><td>Fast, shared state OK</td></tr>
 *   <tr><td>{@link #METHOD}</td><td>Before each test method</td><td>After each test method</td>
 *       <td>Complete isolation</td></tr>
 * </table>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * dockerWorkflows {
 *     pipelines {
 *         ciPipeline {
 *             test {
 *                 stack = dockerTest.composeStacks.testStack
 *                 testTaskName = 'integrationTest'
 *                 lifecycle = WorkflowLifecycle.METHOD  // Fresh containers per test method
 *             }
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>Method Lifecycle Requirements</h3>
 * <p>When using {@link #METHOD} lifecycle:</p>
 * <ul>
 *   <li>Test task must have {@code maxParallelForks = 1} (sequential execution required)</li>
 *   <li>Test class must use {@code @ComposeUp} (Spock) or {@code @ExtendWith(DockerComposeMethodExtension.class)}
 *       (JUnit 5)</li>
 *   <li>A compose stack must be configured in the test step</li>
 * </ul>
 *
 * @see TestStepSpec
 * @see com.kineticfire.gradle.docker.spock.LifecycleMode
 * @since 1.0.0
 */
enum WorkflowLifecycle {

    /**
     * CLASS lifecycle - Containers start once before all test methods and stop after all complete.
     *
     * <p>This is the default lifecycle mode. The pipeline orchestrates compose up/down via Gradle tasks,
     * or delegates to testIntegration when {@code delegateStackManagement = true}.</p>
     *
     * <h4>Container Lifecycle</h4>
     * <pre>
     * Pipeline executes:
     *   composeUp (once)
     *     → test method 1
     *     → test method 2
     *     → test method 3
     *   composeDown (once)
     * </pre>
     *
     * <h4>Use CLASS Lifecycle When</h4>
     * <ul>
     *   <li>Tests can share the same container environment</li>
     *   <li>Tests build on each other (workflow tests)</li>
     *   <li>Container startup time is expensive</li>
     *   <li>Tests perform read-only operations</li>
     * </ul>
     *
     * <p><b>Performance</b>: Fastest - containers start once</p>
     * <p><b>Isolation</b>: Low - tests share state</p>
     */
    CLASS,

    /**
     * METHOD lifecycle - Containers start fresh before each test method and stop after each.
     *
     * <p>When METHOD is selected, the pipeline automatically delegates compose management to the
     * test framework extension ({@code @ComposeUp} for Spock, {@code @ExtendWith} for JUnit 5).
     * The pipeline sets system properties to communicate the compose configuration to the test
     * framework.</p>
     *
     * <h4>Container Lifecycle</h4>
     * <pre>
     * Test framework executes:
     *   composeUp → test method 1 → composeDown
     *   composeUp → test method 2 → composeDown
     *   composeUp → test method 3 → composeDown
     * </pre>
     *
     * <h4>Use METHOD Lifecycle When</h4>
     * <ul>
     *   <li>Tests need complete isolation</li>
     *   <li>Tests modify database or application state</li>
     *   <li>Tests must be independent and idempotent</li>
     *   <li>Order independence is critical</li>
     * </ul>
     *
     * <h4>Requirements</h4>
     * <ul>
     *   <li>Test task must have {@code maxParallelForks = 1} - sequential execution is required
     *       to avoid port conflicts when multiple tests try to start containers simultaneously</li>
     *   <li>Test class must have the appropriate annotation:
     *       <ul>
     *         <li>Spock: {@code @ComposeUp}</li>
     *         <li>JUnit 5: {@code @ExtendWith(DockerComposeMethodExtension.class)}</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * <p><b>Performance</b>: Slower - containers restart for each test</p>
     * <p><b>Isolation</b>: Maximum - each test gets fresh containers</p>
     */
    METHOD
}
