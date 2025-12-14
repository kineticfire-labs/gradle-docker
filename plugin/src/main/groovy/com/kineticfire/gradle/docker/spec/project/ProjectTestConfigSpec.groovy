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

import com.kineticfire.gradle.docker.Lifecycle
import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject

/**
 * Named test configuration for dockerProject DSL with multiple test configurations.
 *
 * Allows defining multiple test configurations with different compose files,
 * lifecycle modes, and test class patterns. Each configuration generates
 * a separate test task named {configName}IntegrationTest.
 *
 * Example:
 * <pre>
 * dockerProject {
 *     tests {
 *         apiTests {
 *             compose = 'src/integrationTest/resources/compose/api.yml'
 *             lifecycle = Lifecycle.CLASS
 *             testClasses = ['com.example.api.**']
 *         }
 *         statefulTests {
 *             compose = 'src/integrationTest/resources/compose/stateful.yml'
 *             lifecycle = Lifecycle.METHOD
 *             testClasses = ['com.example.stateful.**']
 *         }
 *     }
 * }
 * </pre>
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: Implements Named interface for NamedDomainObjectContainer.
 * Uses @Inject for ObjectFactory injection.
 * All properties use Provider API with proper @Input/@Optional annotations.
 */
abstract class ProjectTestConfigSpec implements Named {

    private final String name

    @Inject
    ProjectTestConfigSpec(String name, ObjectFactory objectFactory) {
        this.name = name
        lifecycle.convention(Lifecycle.CLASS)
        timeoutSeconds.convention(60)
        pollSeconds.convention(2)
        testClasses.convention([])
        waitForHealthy.convention([])
        waitForRunning.convention([])
    }

    /**
     * The name of this test configuration (used as part of task name).
     * Auto-generates test task named '{name}IntegrationTest'.
     */
    @Override
    @Input
    String getName() {
        return name
    }

    /**
     * Path to Docker Compose file relative to project root.
     * (e.g., 'src/integrationTest/resources/compose/api.yml')
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
     * Container lifecycle mode: CLASS or METHOD.
     * - CLASS: containers start once per test class (default)
     * - METHOD: containers restart for each test method
     *
     * Type-safe: Uses Lifecycle enum for compile-time validation.
     */
    @Input
    abstract Property<Lifecycle> getLifecycle()

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
     * Test class patterns to include in this test configuration.
     * Uses Gradle's Test.filter.includeTestsMatching() patterns.
     * (e.g., ['com.example.api.**', 'com.example.integration.**'])
     *
     * If empty, all test classes are included.
     */
    @Input
    abstract ListProperty<String> getTestClasses()

    /**
     * Compose project name. If not specified, derived from project name and config name.
     */
    @Input
    @Optional
    abstract Property<String> getProjectName()

    /**
     * Generate the test task name for this configuration.
     * Format: {configName}IntegrationTest
     *
     * @return The generated test task name
     */
    String getTestTaskName() {
        return "${name.capitalize()}IntegrationTest"
    }
}
