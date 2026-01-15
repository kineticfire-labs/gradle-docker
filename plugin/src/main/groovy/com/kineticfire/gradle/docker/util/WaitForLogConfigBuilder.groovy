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

package com.kineticfire.gradle.docker.util

import com.kineticfire.gradle.docker.model.WaitForLogConfig
import org.gradle.api.GradleException

import java.time.Duration
import java.util.regex.Pattern

/**
 * Builder for constructing WaitForLogConfig from task properties.
 *
 * <p>This class performs validation and pattern compilation, converting from
 * DSL property values to an immutable runtime configuration.</p>
 *
 * <p>This is Phase 2 validation (execution time) - validates regex syntax and
 * re-validates structure. Phase 1 validation (configuration time) occurs in
 * ComposeStackSpec.validateWaitForLogSpec().</p>
 */
class WaitForLogConfigBuilder {

    private WaitForLogConfigBuilder() {
        // Utility class
    }

    /**
     * Build WaitForLogConfig from task property values.
     *
     * <p>This method validates inputs and compiles regex patterns.</p>
     *
     * @param projectName Compose project name
     * @param waitForServices Map of service -> pattern strings
     * @param rejectPatterns Map of service -> reject pattern strings (may be null)
     * @param timeoutSeconds Timeout in seconds
     * @param pollSeconds Poll interval in seconds
     * @param caseInsensitive Whether to use case-insensitive matching
     * @param verbose Whether to enable verbose logging
     * @param progressIntervalSeconds Progress logging interval (0 = disabled)
     * @return Configured WaitForLogConfig
     * @throws GradleException if validation fails
     */
    static WaitForLogConfig build(
            String projectName,
            Map<String, List<String>> waitForServices,
            Map<String, List<String>> rejectPatterns,
            int timeoutSeconds,
            int pollSeconds,
            boolean caseInsensitive,
            boolean verbose,
            int progressIntervalSeconds) {

        // Validate waitForServices (defensive - should be caught at config time)
        if (waitForServices == null || waitForServices.isEmpty()) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block: 'waitForServices' cannot be empty.\n" +
                "At least one service with patterns must be specified.\n\n" +
                "Example:\n" +
                "    waitForLog {\n" +
                "        waitForServices.set([\n" +
                "            'app': ['Started Application']\n" +
                "        ])\n" +
                "    }"
            )
        }

        // Validate timeout and poll values are positive
        if (timeoutSeconds <= 0) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block: 'timeoutSeconds' must be positive, " +
                "got: ${timeoutSeconds}"
            )
        }
        if (pollSeconds <= 0) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block: 'pollSeconds' must be positive, " +
                "got: ${pollSeconds}"
            )
        }

        // Warn if pollSeconds > timeoutSeconds (only one poll attempt will occur)
        if (pollSeconds > timeoutSeconds) {
            System.err.println("[waitForLog] WARNING: pollSeconds (${pollSeconds}) > timeoutSeconds " +
                "(${timeoutSeconds}). Only one poll attempt will be made before timeout. Consider " +
                "increasing timeoutSeconds or decreasing pollSeconds.")
        }

        // Validate each service has at least one pattern
        waitForServices.each { serviceName, patterns ->
            if (patterns == null || patterns.isEmpty()) {
                throw new GradleException(
                    "Configuration error in 'waitForLog' block: Pattern list for service " +
                    "'${serviceName}' cannot be empty.\n" +
                    "Each service must have at least one pattern to match.\n\n" +
                    "Example:\n" +
                    "    waitForServices.set([\n" +
                    "        '${serviceName}': ['Started Application']  // At least one pattern required\n" +
                    "    ])"
                )
            }
        }

        // Compile patterns (validates regex syntax)
        Map<String, List<Pattern>> compiledPatterns
        Map<String, List<Pattern>> compiledRejectPatterns

        try {
            compiledPatterns = LogPatternMatcher.compilePatterns(waitForServices, caseInsensitive)
        } catch (IllegalArgumentException e) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block: ${e.message}"
            )
        }

        try {
            compiledRejectPatterns = rejectPatterns != null
                ? LogPatternMatcher.compilePatterns(rejectPatterns, caseInsensitive)
                : [:]
        } catch (IllegalArgumentException e) {
            throw new GradleException(
                "Configuration error in 'waitForLog' block (rejectPatterns): ${e.message}"
            )
        }

        // Warn about orphaned reject patterns (services in rejectPatterns but not in waitForServices)
        // This is a warning, not an error, because it may be intentional in some cases
        if (rejectPatterns != null) {
            def orphanedServices = rejectPatterns.keySet() - waitForServices.keySet()
            if (!orphanedServices.isEmpty()) {
                System.err.println("[waitForLog] WARNING: rejectPatterns contains services not in " +
                    "waitForServices: ${orphanedServices}. These reject patterns will never be checked.")
            }
        }

        return new WaitForLogConfig(
            projectName,
            compiledPatterns,
            compiledRejectPatterns,
            Duration.ofSeconds(timeoutSeconds),
            Duration.ofSeconds(pollSeconds),
            caseInsensitive,
            verbose,
            Duration.ofSeconds(progressIntervalSeconds)
        )
    }
}
