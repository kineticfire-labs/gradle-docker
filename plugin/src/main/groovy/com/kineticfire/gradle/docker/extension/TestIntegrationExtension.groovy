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

package com.kineticfire.gradle.docker.extension

import com.kineticfire.gradle.docker.Lifecycle
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.testing.Test

import javax.inject.Inject

/**
 * Extension methods for Test task integration with Docker Compose
 */
abstract class TestIntegrationExtension {

    private final ProjectLayout layout
    private final Provider<String> projectNameProvider
    private final Logger logger
    // Store reference to DockerTestExtension for configuration-time access
    private DockerTestExtension dockerTestExtension

    @Inject
    TestIntegrationExtension(String projectName, ProjectLayout layout, ProviderFactory providers) {
        this.layout = layout
        // Store project name as a provider (configuration-cache safe)
        this.projectNameProvider = providers.provider { projectName }
        this.logger = Logging.getLogger(TestIntegrationExtension)
    }
    
    /**
     * Set the DockerTestExtension reference (called by plugin during configuration)
     */
    void setDockerTestExtension(DockerTestExtension extension) {
        this.dockerTestExtension = extension
    }
    
    /**
     * Configure a test task to use Docker Compose stack with specified lifecycle.
     * Type-safe version using Lifecycle enum.
     *
     * @param test Test task to configure
     * @param stackName Name of the compose stack to use
     * @param lifecycle When to start/stop the stack: Lifecycle.CLASS or Lifecycle.METHOD
     */
    void usesCompose(Test test, String stackName, Lifecycle lifecycle) {
        logger.info("Configuring test '{}' to use compose stack '{}' with lifecycle '{}'",
            test.name, stackName, lifecycle)

        // Find the compose stack configuration
        if (!dockerTestExtension) {
            throw new IllegalStateException(
                "dockerTest extension not found. Apply gradle-docker plugin first. " +
                "Ensure 'id \"com.kineticfire.gradle.docker\"' is in plugins block."
            )
        }
        def dockerTestExt = dockerTestExtension

        def stackSpec = dockerTestExt.composeStacks.findByName(stackName)
        if (!stackSpec) {
            // Gather available stack names for helpful error message
            def availableStacks = dockerTestExt.composeStacks.collect { it.name }.join(', ')
            if (availableStacks.isEmpty()) {
                throw new IllegalArgumentException(
                    "Compose stack '${stackName}' not found in dockerTest configuration. " +
                    "No stacks are defined. " +
                    "Add stack definitions in dockerTest { composeStacks { ... } } in build.gradle."
                )
            } else {
                throw new IllegalArgumentException(
                    "Compose stack '${stackName}' not found in dockerTest configuration. " +
                    "Available stacks: [${availableStacks}]. " +
                    "Check dockerTest { composeStacks { ... } } in build.gradle."
                )
            }
        }

        // Configure test task based on lifecycle (type-safe - no validation needed)
        switch (lifecycle) {
            case Lifecycle.CLASS:
                configureClassLifecycle(test, stackName, stackSpec)
                break
            case Lifecycle.METHOD:
                configureMethodLifecycle(test, stackName, stackSpec)
                break
        }
    }

    /**
     * Configure a test task to use Docker Compose stack with specified lifecycle.
     * String version for backward compatibility.
     *
     * @param test Test task to configure
     * @param stackName Name of the compose stack to use
     * @param lifecycle When to start/stop the stack: "class" or "method"
     * @deprecated Use the Lifecycle enum version for type-safety
     */
    @Deprecated
    void usesCompose(Test test, String stackName, String lifecycle) {
        // Convert string to enum and delegate
        usesCompose(test, stackName, Lifecycle.fromString(lifecycle))
    }
    
    /**
     * Get the path to the compose state file for a given stack
     * @param stackName Name of the compose stack
     * @return Provider of path to the state file that will contain runtime information
     */
    Provider<String> composeStateFileFor(String stackName) {
        def buildDirProvider = layout.buildDirectory
        def stateDirProvider = buildDirProvider.dir("compose-state")
        return stateDirProvider.map { dir ->
            dir.asFile.toPath().resolve("${stackName}-state.json").toString()
        }
    }
    
    private void configureClassLifecycle(Test test, String stackName, stackSpec) {
        // Class lifecycle uses test framework extension to manage compose per test class

        // Set comprehensive system properties from dockerTest DSL
        setComprehensiveSystemProperties(test, stackName, stackSpec, Lifecycle.CLASS)

        // Auto-wire task dependencies to ensure compose tasks run
        autoWireComposeDependencies(test, stackName)

        // Add listener to provide helpful hints if tests fail due to missing annotation
        addAnnotationHintListener(test, stackName, Lifecycle.CLASS)

        logger.info("Test '{}' configured for CLASS lifecycle from dockerTest DSL", test.name)
        logger.info("Spock: Use @ComposeUp (zero parameters)")
        logger.info("JUnit 5: Use @ExtendWith(DockerComposeClassExtension.class)")
    }
    
    private void configureMethodLifecycle(Test test, String stackName, stackSpec) {
        // Method lifecycle uses test framework extension to manage compose per test method
        // NOTE: For METHOD lifecycle, we do NOT auto-wire compose task dependencies because
        // the test framework extension (@ComposeUp or @ExtendWith) handles compose up/down
        // per test method. The Gradle composeUp/composeDown tasks are not used.

        // Set comprehensive system properties from dockerTest DSL
        setComprehensiveSystemProperties(test, stackName, stackSpec, Lifecycle.METHOD)

        // Add listener to provide helpful hints if tests fail due to missing annotation
        addAnnotationHintListener(test, stackName, Lifecycle.METHOD)

        logger.info("Test '{}' configured for METHOD lifecycle from dockerTest DSL", test.name)
        logger.info("Note: METHOD lifecycle - compose is managed by test framework, not Gradle tasks")
        logger.info("Spock: Use @ComposeUp (zero parameters)")
        logger.info("JUnit 5: Use @ExtendWith(DockerComposeMethodExtension.class)")
    }

    /**
     * Set comprehensive system properties from ComposeStackSpec for test framework extensions to consume
     * @param test Test task to configure
     * @param stackName Name of the compose stack
     * @param stackSpec ComposeStackSpec containing all configuration
     * @param lifecycle Lifecycle mode (Lifecycle.CLASS or Lifecycle.METHOD)
     */
    private void setComprehensiveSystemProperties(Test test, String stackName, stackSpec, Lifecycle lifecycle) {
        // Basic configuration
        test.systemProperty("docker.compose.stack", stackName)
        test.systemProperty("docker.compose.lifecycle", lifecycle.toPropertyValue())
        // Set both old and new property names for backward compatibility
        test.systemProperty("docker.compose.project", projectNameProvider.get())
        test.systemProperty("docker.compose.projectName", stackSpec.projectName.getOrElse(""))

        // Compose files - serialize as comma-separated paths
        def filePaths = stackSpec.files.collect { it.absolutePath }.join(',')
        test.systemProperty("docker.compose.files", filePaths)

        // Wait for healthy settings - check if Property is present
        def waitForHealthySpec = stackSpec.waitForHealthy.getOrNull()
        if (waitForHealthySpec) {
            def services = waitForHealthySpec.waitForServices.getOrElse([]).join(',')
            test.systemProperty("docker.compose.waitForHealthy.services", services)
            test.systemProperty("docker.compose.waitForHealthy.timeoutSeconds",
                waitForHealthySpec.timeoutSeconds.getOrElse(60).toString())
            test.systemProperty("docker.compose.waitForHealthy.pollSeconds",
                waitForHealthySpec.pollSeconds.getOrElse(2).toString())
        }

        // Wait for running settings - check if Property is present
        def waitForRunningSpec = stackSpec.waitForRunning.getOrNull()
        if (waitForRunningSpec) {
            def services = waitForRunningSpec.waitForServices.getOrElse([]).join(',')
            test.systemProperty("docker.compose.waitForRunning.services", services)
            test.systemProperty("docker.compose.waitForRunning.timeoutSeconds",
                waitForRunningSpec.timeoutSeconds.getOrElse(60).toString())
            test.systemProperty("docker.compose.waitForRunning.pollSeconds",
                waitForRunningSpec.pollSeconds.getOrElse(2).toString())
        }

        // State file path
        test.systemProperty("COMPOSE_STATE_FILE", composeStateFileFor(stackName).get())

        logger.debug("Set system properties for test '{}' from dockerTest stack '{}'", test.name, stackName)
    }

    /**
     * Auto-wire test task dependencies on compose lifecycle tasks
     *
     * This ensures compose containers are started before the test task runs and
     * stopped after the test task completes (even on failure).
     *
     * @param test Test task to wire dependencies for
     * @param stackName Name of the compose stack
     */
    private void autoWireComposeDependencies(Test test, String stackName) {
        String capitalizedStackName = stackName.capitalize()
        String composeUpTaskName = "composeUp${capitalizedStackName}"
        String composeDownTaskName = "composeDown${capitalizedStackName}"

        // Wire dependencies using task name strings (Gradle will resolve at execution time)
        // This follows the same pattern as configureSuiteLifecycle()
        test.dependsOn composeUpTaskName
        test.finalizedBy composeDownTaskName

        logger.info(
            "Auto-wired test task '${test.name}': " +
            "dependsOn ${composeUpTaskName}, finalizedBy ${composeDownTaskName}"
        )
    }

    /**
     * Add a test listener that provides helpful hints when tests fail due to missing annotations.
     *
     * <p>When tests fail with connection-related errors, this listener prints a hint suggesting
     * the user may have forgotten to add the required annotation (@ComposeUp for Spock,
     * @ExtendWith for JUnit 5).</p>
     *
     * @param test Test task to add listener to
     * @param stackName Name of the compose stack
     * @param lifecycle Lifecycle mode (Lifecycle.CLASS or Lifecycle.METHOD)
     */
    private void addAnnotationHintListener(Test test, String stackName, Lifecycle lifecycle) {
        test.addTestListener(new ComposeAnnotationHintListener(stackName, lifecycle.toPropertyValue()))
        logger.debug("Added annotation hint listener to test task '{}'", test.name)
    }
}