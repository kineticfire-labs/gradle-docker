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

import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

/**
 * Test listener that detects potential missing Docker Compose annotation issues and provides helpful hints.
 *
 * <p>When tests fail with connection-related errors and {@code usesCompose()} is configured in build.gradle,
 * this listener prints a hint suggesting the user may have forgotten to add the required test framework
 * annotation ({@code @ComposeUp} for Spock, {@code @ExtendWith} for JUnit 5).</p>
 *
 * <p>The hint is printed only once per test run to avoid cluttering output.</p>
 *
 * @since 1.0.0
 */
class ComposeAnnotationHintListener implements TestListener {

    private final String stackName
    private final String lifecycle
    private boolean hintProvided = false

    /**
     * Creates a new listener for the specified stack and lifecycle.
     *
     * @param stackName the Docker Compose stack name configured in usesCompose()
     * @param lifecycle the lifecycle mode ("class" or "method")
     */
    ComposeAnnotationHintListener(String stackName, String lifecycle) {
        this.stackName = stackName
        this.lifecycle = lifecycle
    }

    @Override
    void beforeSuite(TestDescriptor suite) {
        // No action needed
    }

    @Override
    void afterSuite(TestDescriptor suite, TestResult result) {
        if (result.resultType == TestResult.ResultType.FAILURE && !hintProvided) {
            checkAndProvideHint(suite, result)
        }
    }

    @Override
    void beforeTest(TestDescriptor testDescriptor) {
        // No action needed
    }

    @Override
    void afterTest(TestDescriptor testDescriptor, TestResult result) {
        if (result.resultType == TestResult.ResultType.FAILURE && !hintProvided) {
            checkAndProvideHint(testDescriptor, result)
        }
    }

    private void checkAndProvideHint(TestDescriptor descriptor, TestResult result) {
        def exception = result.exception
        if (exception == null) {
            return
        }

        def message = exception.message ?: ''
        def causeMessage = exception.cause?.message ?: ''
        def fullMessage = message + ' ' + causeMessage

        // Heuristics for detecting missing annotation issues:
        // 1. Connection refused errors (containers not started)
        // 2. Timeout errors (containers not responding)
        // 3. References to state file not found
        // 4. Stack configuration errors from extensions
        boolean likelyMissingAnnotation = isConnectionError(fullMessage) ||
            isStateFileError(fullMessage) ||
            isStackConfigError(fullMessage)

        if (likelyMissingAnnotation) {
            String className = descriptor.className ?: 'UnknownClass'
            provideAnnotationHint(className)
            hintProvided = true
        }
    }

    private boolean isConnectionError(String message) {
        def lowerMessage = message.toLowerCase()
        return lowerMessage.contains('connection refused') ||
            lowerMessage.contains('connect timed out') ||
            lowerMessage.contains('no route to host') ||
            lowerMessage.contains('connection reset') ||
            lowerMessage.contains('connection closed') ||
            lowerMessage.contains('socket timeout') ||
            lowerMessage.contains('econnrefused')
    }

    private boolean isStateFileError(String message) {
        def lowerMessage = message.toLowerCase()
        return (lowerMessage.contains('compose_state_file') && lowerMessage.contains('null')) ||
            (lowerMessage.contains('state') && lowerMessage.contains('file') && lowerMessage.contains('not found'))
    }

    private boolean isStackConfigError(String message) {
        def lowerMessage = message.toLowerCase()
        return (lowerMessage.contains('stack') && lowerMessage.contains('not configured')) ||
            (lowerMessage.contains('compose') && lowerMessage.contains('not configured'))
    }

    private void provideAnnotationHint(String className) {
        String simpleClassName = extractSimpleClassName(className)
        String extensionClass = lifecycle == 'method' ?
            'DockerComposeMethodExtension' : 'DockerComposeClassExtension'

        String hint = buildHintMessage(className, simpleClassName, extensionClass)
        System.err.println(hint)
    }

    private String extractSimpleClassName(String className) {
        if (className.contains('.')) {
            return className.substring(className.lastIndexOf('.') + 1)
        }
        return className
    }

    private String buildHintMessage(String className, String simpleClassName, String extensionClass) {
        return """
================================================================================
HINT: Possible missing Docker Compose annotation

Test class '${className}' is configured to use Docker Compose stack '${stackName}'
with '${lifecycle}' lifecycle, but tests are failing with connection errors.

This often indicates the test class is missing the required annotation.
Add one of the following:

  Spock:
    @ComposeUp
    class ${simpleClassName} extends Specification { ... }

  JUnit 5:
    @ExtendWith(${extensionClass}.class)
    class ${simpleClassName} { ... }

The annotation enables the test framework extension that starts containers
before your tests run.

For more information: docs/usage/usage-docker-orch.md
================================================================================
""".stripIndent().trim()
    }

    /**
     * Returns whether a hint has been provided during this test run.
     *
     * @return true if a hint has been printed, false otherwise
     */
    boolean isHintProvided() {
        return hintProvided
    }

    /**
     * Resets the hint state. Primarily used for testing.
     */
    void resetHintState() {
        hintProvided = false
    }
}
