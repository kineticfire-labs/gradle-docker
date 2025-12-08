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

package com.kineticfire.gradle.docker.spec.project

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject

/**
 * Simplified test configuration for dockerProject DSL.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Uses @Inject for ObjectFactory injection.
 * All properties use Provider API with proper @Input/@Optional annotations.
 */
abstract class ProjectTestSpec {

    @Inject
    ProjectTestSpec(ObjectFactory objectFactory) {
        lifecycle.convention('class')
        timeoutSeconds.convention(60)
        pollSeconds.convention(2)
        testTaskName.convention('integrationTest')
        waitForHealthy.convention([])
        waitForRunning.convention([])
    }

    /**
     * Path to Docker Compose file relative to project root.
     * (e.g., 'src/integrationTest/resources/compose/app.yml')
     */
    @Input
    @Optional
    abstract Property<String> getCompose()

    /**
     * Services to wait for healthy status before running tests.
     * (e.g., ['app', 'db'])
     */
    @Input
    abstract ListProperty<String> getWaitForHealthy()

    /**
     * Services to wait for running status before running tests.
     * Alternative to waitForHealthy for services without health checks.
     */
    @Input
    abstract ListProperty<String> getWaitForRunning()

    /**
     * Container lifecycle mode: 'class' or 'method'.
     * - 'class': containers start once per test class (default)
     * - 'method': containers restart for each test method
     *
     * When 'method' is selected, the translator automatically configures
     * delegateStackManagement=true in the workflow's TestStepSpec, enabling
     * the test framework extension to control container lifecycle.
     */
    @Input
    abstract Property<String> getLifecycle()

    /**
     * Timeout in seconds for waiting on containers.
     * Default: 60
     */
    @Input
    abstract Property<Integer> getTimeoutSeconds()

    /**
     * Poll interval in seconds when waiting for containers.
     * Default: 2
     */
    @Input
    abstract Property<Integer> getPollSeconds()

    /**
     * Compose project name. If not specified, derived from project name.
     */
    @Input
    @Optional
    abstract Property<String> getProjectName()

    /**
     * Name of the test task to execute.
     * Default: 'integrationTest'
     */
    @Input
    abstract Property<String> getTestTaskName()
}
