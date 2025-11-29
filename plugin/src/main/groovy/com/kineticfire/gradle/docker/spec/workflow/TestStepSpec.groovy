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

import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import com.kineticfire.gradle.docker.workflow.TestResult
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * Specification for the test step in a pipeline workflow
 *
 * Defines which Compose stack to start and which test task to run.
 *
 * Configuration cache compatibility: Stores task name (String) instead of Task reference
 * because Task objects cannot be serialized. The actual Task is looked up at execution time.
 */
abstract class TestStepSpec {

    private final ObjectFactory objectFactory

    @Inject
    TestStepSpec(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory

        timeoutMinutes.convention(30)
        delegateStackManagement.convention(false)
    }

    /**
     * Reference to the ComposeStackSpec from dockerOrch.composeStacks that should be started
     */
    abstract Property<ComposeStackSpec> getStack()

    /**
     * The name of the test task to execute (e.g., 'integrationTest')
     *
     * Configuration cache compatible: stores task name, not Task reference.
     * The actual Task is looked up at execution time.
     */
    abstract Property<String> getTestTaskName()

    /**
     * Timeout in minutes for the test execution
     */
    abstract Property<Integer> getTimeoutMinutes()

    /**
     * Hook executed before the test step runs (before composeUp)
     */
    abstract Property<Action<Void>> getBeforeTest()

    /**
     * Hook executed after the test step completes (receives TestResult)
     */
    abstract Property<Action<TestResult>> getAfterTest()

    /**
     * When true, the pipeline delegates compose stack lifecycle management to testIntegration extension.
     * The pipeline will NOT execute composeUp/composeDown - instead relying on the test task's
     * dependsOn and finalizedBy relationships configured by testIntegration.usesCompose().
     *
     * When false (default), the pipeline manages compose lifecycle directly by executing
     * composeUp before tests and composeDown after tests.
     *
     * Use this when you need per-class or per-method container lifecycle control:
     * - Configure testIntegration.usesCompose(testTask, 'stackName', 'class') or 'method'
     * - Set delegateStackManagement = true in the pipeline test step
     * - The stack property becomes optional when delegateStackManagement is true
     *
     * Default: false (pipeline manages compose lifecycle)
     */
    abstract Property<Boolean> getDelegateStackManagement()
}
