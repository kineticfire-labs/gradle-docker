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
        // Create configuration (reads system properties with annotation fallback)
        def config = createConfiguration(annotation, spec)

        // Validate final merged configuration
        validateConfiguration(config)

        // Get lifecycle from config (already resolved from system property or annotation)
        def lifecycle = config.lifecycle

        // Register appropriate interceptor based on lifecycle mode
        if (lifecycle == LifecycleMode.CLASS) {
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
     * Validates the final merged configuration.
     *
     * @param config the configuration map to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateConfiguration(Map<String, Object> config) {
        if (!config.stackName) {
            throw new IllegalArgumentException(
                "Stack name not configured for @ComposeUp test class '${config.className}'.\n\n" +
                "Configure the stack using one of these approaches:\n\n" +
                "Option 1 - Configure in build.gradle with usesCompose() (RECOMMENDED):\n" +
                "  tasks.named('integrationTest') {\n" +
                "      usesCompose(stack: 'myStack', lifecycle: 'class')\n" +
                "  }\n" +
                "  Then use: @ComposeUp  // zero parameters\n\n" +
                "Option 2 - Configure in annotation (standalone mode):\n" +
                "  @ComposeUp(stackName = 'myStack', composeFile = 'path/to/compose.yml')\n\n" +
                "For more information: docs/usage/usage-docker-orch.md"
            )
        }
        if (!config.composeFiles || (config.composeFiles as List).isEmpty()) {
            throw new IllegalArgumentException(
                "Compose file(s) not configured for @ComposeUp test class '${config.className}'.\n\n" +
                "Configure compose files using one of these approaches:\n\n" +
                "Option 1 - Configure in build.gradle (RECOMMENDED):\n" +
                "  dockerOrch {\n" +
                "      composeStacks {\n" +
                "          myStack { files.from('src/integrationTest/resources/compose/app.yml') }\n" +
                "      }\n" +
                "  }\n\n" +
                "Option 2 - Configure in annotation:\n" +
                "  @ComposeUp(stackName = 'myStack', composeFile = 'path/to/compose.yml')\n\n" +
                "For more information: docs/usage/usage-docker-orch.md"
            )
        }
        if (config.timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive")
        }
        if (config.pollSeconds <= 0) {
            throw new IllegalArgumentException("pollSeconds must be positive")
        }
    }

    /**
     * Creates configuration by reading system properties first, then annotation as fallback.
     * Detects conflicts when both sources specify the same parameter.
     *
     * @param annotation the ComposeUp annotation
     * @param spec the specification
     * @return configuration map
     * @throws IllegalStateException if both system property and annotation specify the same parameter
     */
    private Map<String, Object> createConfiguration(ComposeUp annotation, SpecInfo spec) {
        // Read stackName (system property or annotation)
        def stackName = readConfigValue(
            'docker.compose.stack',
            annotation.stackName(),
            'stackName',
            'Stack name'
        )

        // Read compose files (system property or annotation)
        def composeFiles = readComposeFiles(annotation)

        // Read lifecycle mode (system property can override annotation)
        def lifecycle = readLifecycleMode(annotation)

        // Read projectName (system property or annotation)
        def projectNameFromSysProp = systemPropertyService.getProperty('docker.compose.projectName', '')
        def projectNameBase = projectNameFromSysProp ?: (annotation.projectName() ?: stackName)

        // Read wait for healthy services
        def waitForHealthy = readConfigList(
            'docker.compose.waitForHealthy.services',
            annotation.waitForHealthy() as List<String>,
            'waitForHealthy'
        )

        // Read wait for running services
        def waitForRunning = readConfigList(
            'docker.compose.waitForRunning.services',
            annotation.waitForRunning() as List<String>,
            'waitForRunning'
        )

        // Read timeout seconds (check both specific and general)
        def timeoutSeconds = readTimeoutSeconds(annotation)

        // Read poll seconds (check both specific and general)
        def pollSeconds = readPollSeconds(annotation)

        return [
            stackName: stackName,
            composeFiles: composeFiles,
            lifecycle: lifecycle,
            projectNameBase: projectNameBase,
            waitForHealthy: waitForHealthy,
            waitForRunning: waitForRunning,
            timeoutSeconds: timeoutSeconds,
            pollSeconds: pollSeconds,
            className: spec.name ? spec.name.substring(spec.name.lastIndexOf('.') + 1) : 'UnknownSpec'
        ]
    }

    /**
     * Reads a configuration value from system property first, then annotation fallback.
     * Fails if BOTH sources provide non-empty values (conflict detection).
     *
     * @param systemPropertyKey the system property key to read
     * @param annotationValue the value from annotation
     * @param parameterName the parameter name (for error messages)
     * @param displayName the human-readable name (for error messages)
     * @return the resolved value (system property takes precedence if no conflict)
     * @throws IllegalStateException if both system property and annotation specify non-empty values
     */
    private String readConfigValue(String systemPropertyKey, String annotationValue,
                                   String parameterName, String displayName) {
        def sysPropValue = systemPropertyService.getProperty(systemPropertyKey, '')
        def hasSystemProperty = sysPropValue && !sysPropValue.isEmpty()
        def hasAnnotationValue = annotationValue && !annotationValue.isEmpty()

        // Conflict detection: both sources specify value
        if (hasSystemProperty && hasAnnotationValue) {
            throw new IllegalStateException(
                "Configuration conflict for ${displayName}: " +
                "specified in BOTH build.gradle (system property '${systemPropertyKey}' = '${sysPropValue}') " +
                "AND @ComposeUp annotation (${parameterName} = '${annotationValue}'). " +
                "Remove one to resolve conflict. " +
                "Recommended: configure in build.gradle only and use @ComposeUp with no parameters."
            )
        }

        // Return system property if present, otherwise annotation value
        return hasSystemProperty ? sysPropValue : annotationValue
    }

    /**
     * Reads a list configuration value from system property first, then annotation fallback.
     * System property format: comma-separated values.
     *
     * @param systemPropertyKey the system property key to read
     * @param annotationValue the list from annotation
     * @param parameterName the parameter name (for error messages)
     * @return the resolved list
     * @throws IllegalStateException if both system property and annotation specify non-empty values
     */
    private List<String> readConfigList(String systemPropertyKey, List<String> annotationValue,
                                        String parameterName) {
        def sysPropValue = systemPropertyService.getProperty(systemPropertyKey, '')
        def hasSystemProperty = sysPropValue && !sysPropValue.isEmpty()
        def hasAnnotationValue = annotationValue && !annotationValue.isEmpty()

        // Conflict detection: both sources specify value
        if (hasSystemProperty && hasAnnotationValue) {
            throw new IllegalStateException(
                "Configuration conflict for ${parameterName}: " +
                "specified in BOTH build.gradle (system property '${systemPropertyKey}' = '${sysPropValue}') " +
                "AND @ComposeUp annotation (${parameterName} = ${annotationValue}). " +
                "Remove one to resolve conflict. " +
                "Recommended: configure in build.gradle only and use @ComposeUp with no parameters."
            )
        }

        // Parse system property as comma-separated list if present
        if (hasSystemProperty) {
            return sysPropValue.split(',').collect { it.trim() }.findAll { !it.isEmpty() }
        }

        return annotationValue ?: []
    }

    /**
     * Reads compose files from system property or annotation.
     * Handles both composeFile (single) and composeFiles (array) annotation parameters.
     *
     * @param annotation the ComposeUp annotation
     * @return list of compose file paths
     * @throws IllegalStateException if conflicts detected
     */
    private List<String> readComposeFiles(ComposeUp annotation) {
        def sysPropValue = systemPropertyService.getProperty('docker.compose.files', '')
        def hasSystemProperty = sysPropValue && !sysPropValue.isEmpty()

        // Check annotation values
        def hasSingleFile = annotation.composeFile() && !annotation.composeFile().isEmpty()
        def hasMultipleFiles = annotation.composeFiles() && annotation.composeFiles().length > 0

        // Conflict detection: system property vs annotation
        if (hasSystemProperty && (hasSingleFile || hasMultipleFiles)) {
            def annotationSource = hasSingleFile ?
                "composeFile = '${annotation.composeFile()}'" :
                "composeFiles = ${annotation.composeFiles() as List}"
            throw new IllegalStateException(
                "Configuration conflict for compose files: " +
                "specified in BOTH build.gradle (system property 'docker.compose.files' = '${sysPropValue}') " +
                "AND @ComposeUp annotation (${annotationSource}). " +
                "Remove one to resolve conflict. " +
                "Recommended: configure in build.gradle only and use @ComposeUp with no parameters."
            )
        }

        // Parse system property as comma-separated list if present
        if (hasSystemProperty) {
            return sysPropValue.split(',').collect { it.trim() }.findAll { !it.isEmpty() }
        }

        // Use annotation values: prefer composeFiles array, fallback to single composeFile
        if (hasMultipleFiles) {
            return annotation.composeFiles() as List<String>
        }
        if (hasSingleFile) {
            return [annotation.composeFile()]
        }

        return []
    }

    /**
     * Reads lifecycle mode from system property or annotation.
     * System property can override annotation default.
     *
     * @param annotation the ComposeUp annotation
     * @return the lifecycle mode
     */
    private LifecycleMode readLifecycleMode(ComposeUp annotation) {
        def sysPropValue = systemPropertyService.getProperty('docker.compose.lifecycle', '')

        if (sysPropValue && !sysPropValue.isEmpty()) {
            // Parse system property value
            switch (sysPropValue.toLowerCase()) {
                case 'class':
                    return LifecycleMode.CLASS
                case 'method':
                    return LifecycleMode.METHOD
                default:
                    throw new IllegalArgumentException(
                        "Invalid lifecycle mode in system property 'docker.compose.lifecycle': '${sysPropValue}'. " +
                        "Must be 'class' or 'method'."
                    )
            }
        }

        // Use annotation value
        return annotation.lifecycle()
    }

    /**
     * Reads timeout seconds from system property or annotation.
     * Checks both specific (waitForHealthy/waitForRunning) and general timeout properties.
     *
     * @param annotation the ComposeUp annotation
     * @return timeout in seconds
     */
    private int readTimeoutSeconds(ComposeUp annotation) {
        // Check for specific timeout property (waitForHealthy or waitForRunning)
        def healthyTimeout = systemPropertyService.getProperty('docker.compose.waitForHealthy.timeoutSeconds', '')
        def runningTimeout = systemPropertyService.getProperty('docker.compose.waitForRunning.timeoutSeconds', '')

        // Use the first available timeout (prefer specific over general)
        def sysPropValue = healthyTimeout ?: runningTimeout

        if (sysPropValue && !sysPropValue.isEmpty()) {
            try {
                return Integer.parseInt(sysPropValue)
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Invalid timeout value in system property: '${sysPropValue}'. Must be a positive integer."
                )
            }
        }

        // Use annotation value
        return annotation.timeoutSeconds()
    }

    /**
     * Reads poll seconds from system property or annotation.
     * Checks both specific (waitForHealthy/waitForRunning) and general poll properties.
     *
     * @param annotation the ComposeUp annotation
     * @return poll interval in seconds
     */
    private int readPollSeconds(ComposeUp annotation) {
        // Check for specific poll property (waitForHealthy or waitForRunning)
        def healthyPoll = systemPropertyService.getProperty('docker.compose.waitForHealthy.pollSeconds', '')
        def runningPoll = systemPropertyService.getProperty('docker.compose.waitForRunning.pollSeconds', '')

        // Use the first available poll (prefer specific over general)
        def sysPropValue = healthyPoll ?: runningPoll

        if (sysPropValue && !sysPropValue.isEmpty()) {
            try {
                return Integer.parseInt(sysPropValue)
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Invalid poll value in system property: '${sysPropValue}'. Must be a positive integer."
                )
            }
        }

        // Use annotation value
        return annotation.pollSeconds()
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
