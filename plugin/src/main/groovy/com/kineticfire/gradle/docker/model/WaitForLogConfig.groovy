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

package com.kineticfire.gradle.docker.model

import java.time.Duration
import java.util.regex.Pattern

/**
 * Immutable configuration for waiting for log patterns.
 *
 * <p>This class is created from WaitForLogSpec during task execution and contains
 * pre-compiled regex patterns for efficient matching.</p>
 */
class WaitForLogConfig {
    final String projectName
    final Map<String, List<Pattern>> servicePatterns
    final Map<String, List<Pattern>> rejectPatterns
    final Duration timeout
    final Duration pollInterval
    final boolean caseInsensitive
    final boolean verbose
    final Duration progressInterval

    WaitForLogConfig(
            String projectName,
            Map<String, List<Pattern>> servicePatterns,
            Map<String, List<Pattern>> rejectPatterns,
            Duration timeout,
            Duration pollInterval,
            boolean caseInsensitive,
            boolean verbose,
            Duration progressInterval) {
        this.projectName = Objects.requireNonNull(projectName, "Project name cannot be null")
        this.servicePatterns = Collections.unmodifiableMap(
            Objects.requireNonNull(servicePatterns, "Service patterns cannot be null")
        )
        this.rejectPatterns = Collections.unmodifiableMap(rejectPatterns ?: [:])
        this.timeout = timeout ?: Duration.ofSeconds(60)
        this.pollInterval = pollInterval ?: Duration.ofSeconds(2)
        this.caseInsensitive = caseInsensitive
        this.verbose = verbose
        this.progressInterval = progressInterval ?: Duration.ZERO
    }

    /**
     * Calculate total wait attempts based on timeout and poll interval.
     *
     * <p>Uses ceiling division to ensure the timeout period is fully covered.
     * For example, with timeout=61s and poll=2s, this returns 31 attempts
     * (not 30 from truncating integer division).</p>
     */
    int getTotalWaitAttempts() {
        return Math.max(1, (int) Math.ceil(timeout.toSeconds() / (double) pollInterval.toSeconds()))
    }

    /**
     * Get list of all services being monitored.
     */
    List<String> getServices() {
        return new ArrayList<>(servicePatterns.keySet())
    }

    /**
     * Check if progress interval logging is enabled.
     */
    boolean hasProgressInterval() {
        return progressInterval != null && progressInterval.toSeconds() > 0
    }

    @Override
    String toString() {
        return "WaitForLogConfig{projectName='${projectName}', services=${services}, " +
               "timeout=${timeout}, pollInterval=${pollInterval}, caseInsensitive=${caseInsensitive}}"
    }
}
