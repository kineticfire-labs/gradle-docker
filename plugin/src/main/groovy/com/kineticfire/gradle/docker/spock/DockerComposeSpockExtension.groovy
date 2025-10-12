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

package com.kineticfire.gradle.docker.spock

import com.kineticfire.gradle.docker.junit.service.*
import com.kineticfire.gradle.docker.service.ComposeService
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.model.SpecInfo

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Spock extension that provides Docker Compose lifecycle management for integration tests.
 *
 * <p>This extension processes the {@link ComposeUp} annotation and manages Docker Compose
 * stacks with configurable lifecycle modes:</p>
 * <ul>
 *   <li><b>CLASS</b> - Containers start once per class (setupSpec/cleanupSpec)</li>
 *   <li><b>METHOD</b> - Containers start fresh for each test method (setup/cleanup)</li>
 * </ul>
 *
 * <p>The extension:</p>
 * <ul>
 *   <li>Generates unique Docker Compose project names to avoid conflicts</li>
 *   <li>Starts containers with {@code docker compose up}</li>
 *   <li>Waits for services to be healthy or running</li>
 *   <li>Generates JSON state files with container information</li>
 *   <li>Sets system property {@code COMPOSE_STATE_FILE} for tests to read</li>
 *   <li>Stops containers with {@code docker compose down}</li>
 *   <li>Cleans up all resources aggressively</li>
 * </ul>
 *
 * @see ComposeUp
 * @see LifecycleMode
 * @since 1.0.0
 */
class DockerComposeSpockExtension extends AbstractAnnotationDrivenExtension<ComposeUp> {

    // Service dependencies (can be injected for testing)
    private final ComposeService composeService
    private final ProcessExecutor processExecutor
    private final FileService fileService
    private final SystemPropertyService systemPropertyService
    private final TimeService timeService

    /**
     * Creates a new extension with default service implementations.
     */
    DockerComposeSpockExtension() {
        this(
            new JUnitComposeService(),
            new DefaultProcessExecutor(),
            new DefaultFileService(),
            new DefaultSystemPropertyService(),
            new DefaultTimeService()
        )
    }

    /**
     * Creates a new extension with custom service implementations (for testing).
     *
     * @param composeService the compose service for Docker Compose operations
     * @param processExecutor the process executor for running external commands
     * @param fileService the file service for file operations
     * @param systemPropertyService the system property service for property access
     * @param timeService the time service for time operations
     */
    DockerComposeSpockExtension(ComposeService composeService,
                                ProcessExecutor processExecutor,
                                FileService fileService,
                                SystemPropertyService systemPropertyService,
                                TimeService timeService) {
        this.composeService = composeService
        this.processExecutor = processExecutor
        this.fileService = fileService
        this.systemPropertyService = systemPropertyService
        this.timeService = timeService
    }

    /**
     * Processes the {@link ComposeUp} annotation on a specification (test class).
     *
     * <p>Registers the appropriate interceptor based on the lifecycle mode:</p>
     * <ul>
     *   <li>CLASS lifecycle → {@link ComposeClassInterceptor}</li>
     *   <li>METHOD lifecycle → {@link ComposeMethodInterceptor}</li>
     * </ul>
     *
     * @param annotation the ComposeUp annotation
     * @param spec the specification being processed
     */
    @Override
    void visitSpecAnnotation(ComposeUp annotation, SpecInfo spec) {
        // Validate annotation
        validateAnnotation(annotation)

        // Create configuration
        def config = createConfiguration(annotation, spec)

        // Register appropriate interceptor based on lifecycle mode
        if (annotation.lifecycle() == LifecycleMode.CLASS) {
            def interceptor = new ComposeClassInterceptor(
                config,
                composeService,
                processExecutor,
                fileService,
                systemPropertyService,
                timeService
            )
            spec.addSetupSpecInterceptor(interceptor)
            spec.addCleanupSpecInterceptor(interceptor)
        } else {
            def interceptor = new ComposeMethodInterceptor(
                config,
                composeService,
                processExecutor,
                fileService,
                systemPropertyService,
                timeService
            )
            spec.addSetupInterceptor(interceptor)
            spec.addCleanupInterceptor(interceptor)
        }
    }

    /**
     * Validates the {@link ComposeUp} annotation.
     *
     * @param annotation the annotation to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateAnnotation(ComposeUp annotation) {
        if (!annotation.stackName()) {
            throw new IllegalArgumentException("@ComposeUp stackName cannot be empty")
        }
        if (!annotation.composeFile()) {
            throw new IllegalArgumentException("@ComposeUp composeFile cannot be empty")
        }
        if (annotation.timeoutSeconds() <= 0) {
            throw new IllegalArgumentException("@ComposeUp timeoutSeconds must be positive")
        }
        if (annotation.pollSeconds() <= 0) {
            throw new IllegalArgumentException("@ComposeUp pollSeconds must be positive")
        }
    }

    /**
     * Creates configuration from annotation and spec.
     *
     * @param annotation the ComposeUp annotation
     * @param spec the specification
     * @return configuration map
     */
    private Map<String, Object> createConfiguration(ComposeUp annotation, SpecInfo spec) {
        return [
            stackName: annotation.stackName(),
            composeFile: annotation.composeFile(),
            lifecycle: annotation.lifecycle(),
            projectNameBase: annotation.projectName() ?: annotation.stackName(),
            waitForHealthy: annotation.waitForHealthy() as List<String>,
            waitForRunning: annotation.waitForRunning() as List<String>,
            timeoutSeconds: annotation.timeoutSeconds(),
            pollSeconds: annotation.pollSeconds(),
            className: spec.name ? spec.name.substring(spec.name.lastIndexOf('.') + 1) : 'UnknownSpec'
        ]
    }

    /**
     * Generates a unique Docker Compose project name.
     *
     * <p>Format: {@code <base>-<class>-<method>-<timestamp>}</p>
     * <p>The project name is sanitized to meet Docker Compose requirements:
     * lowercase alphanumeric with hyphens/underscores only.</p>
     *
     * @param baseProjectName the base project name
     * @param className the test class name
     * @param methodName the test method name (optional, for METHOD lifecycle)
     * @return unique, sanitized project name
     */
    static String generateUniqueProjectName(String baseProjectName, String className, String methodName = null) {
        def timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))

        def projectName = methodName ?
            "${baseProjectName}-${className}-${methodName}-${timestamp}" :
            "${baseProjectName}-${className}-${timestamp}"

        return sanitizeProjectName(projectName)
    }

    /**
     * Sanitizes a project name to meet Docker Compose requirements.
     *
     * <p>Docker Compose project names must:</p>
     * <ul>
     *   <li>Be lowercase</li>
     *   <li>Contain only alphanumeric characters, hyphens, and underscores</li>
     *   <li>Start with an alphanumeric character</li>
     * </ul>
     *
     * @param projectName the project name to sanitize
     * @return sanitized project name
     */
    static String sanitizeProjectName(String projectName) {
        // Convert to lowercase and replace invalid characters with hyphens
        String sanitized = projectName.toLowerCase()
            .replaceAll(/[^a-z0-9\-_]/, "-")  // Replace invalid chars with hyphens
            .replaceAll(/-+/, "-")             // Replace multiple hyphens with single
            .replaceAll(/^-/, "")              // Remove leading hyphens
            .replaceAll(/-$/, "")              // Remove trailing hyphens

        // Ensure it starts with alphanumeric character
        if (sanitized.length() > 0 && !Character.isLetterOrDigit(sanitized.charAt(0))) {
            sanitized = "test-" + sanitized
        }

        return sanitized.isEmpty() ? "test-project" : sanitized
    }
}
