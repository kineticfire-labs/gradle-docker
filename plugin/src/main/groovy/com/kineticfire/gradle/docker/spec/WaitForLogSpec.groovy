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

package com.kineticfire.gradle.docker.spec

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * Specification for waiting on Docker service log patterns.
 *
 * <p>This class configures log-based readiness checks where each service maps to one or more
 * regex patterns that must appear in its logs before the service is considered ready.</p>
 *
 * <p>Note: {@code waitForServices} has no default convention and must be explicitly set.
 * Validation is performed in {@link ComposeStackSpec} when configuring the waitForLog block.</p>
 *
 * <p>Configuration cache compatibility: This class uses only Property API types and is
 * instantiated via ObjectFactory.newInstance(). No Project references are held.</p>
 */
class WaitForLogSpec {

    private final MapProperty<String, List<String>> waitForServices
    private final MapProperty<String, List<String>> rejectPatterns
    private final Property<Integer> timeoutSeconds
    private final Property<Integer> pollSeconds
    private final Property<Boolean> caseInsensitive
    private final Property<Boolean> verbose
    private final Property<Integer> progressIntervalSeconds

    @Inject
    @SuppressWarnings('unchecked')  // Required due to JVM type erasure with nested generics (List<String>)
    WaitForLogSpec(ObjectFactory objects) {
        // Create properties using ObjectFactory
        // Note: The raw List type is unavoidable due to Gradle API limitations.
        // The actual values will be List<String> at runtime.
        this.waitForServices = objects.mapProperty(String, List)
        this.rejectPatterns = objects.mapProperty(String, List)
        this.timeoutSeconds = objects.property(Integer)
        this.pollSeconds = objects.property(Integer)
        this.caseInsensitive = objects.property(Boolean)
        this.verbose = objects.property(Boolean)
        this.progressIntervalSeconds = objects.property(Integer)

        // Set conventions for optional properties
        // Note: waitForServices has no convention - must be explicitly set
        this.timeoutSeconds.convention(60)
        this.pollSeconds.convention(2)
        this.caseInsensitive.convention(false)
        this.verbose.convention(false)
        this.progressIntervalSeconds.convention(0)
        // Set explicit empty map convention for rejectPatterns
        // (MapProperty has no value unless explicitly set or given a convention)
        this.rejectPatterns.convention([:])
    }

    /**
     * Map of service names to list of regex patterns. ALL patterns in the list must match
     * (in any order) before the service is considered ready.
     *
     * <p>Required: Must be explicitly set with at least one service.</p>
     */
    MapProperty<String, List<String>> getWaitForServices() {
        return waitForServices
    }

    /**
     * Map of service names to list of reject patterns. If ANY pattern matches,
     * the wait fails immediately.
     *
     * <p>Optional: Services not in this map have no reject patterns.</p>
     */
    MapProperty<String, List<String>> getRejectPatterns() {
        return rejectPatterns
    }

    /**
     * Maximum seconds to wait for all patterns to match before failing.
     *
     * <p>Default: 60 seconds</p>
     */
    Property<Integer> getTimeoutSeconds() {
        return timeoutSeconds
    }

    /**
     * Poll interval in seconds for checking container logs.
     *
     * <p>Default: 2 seconds</p>
     */
    Property<Integer> getPollSeconds() {
        return pollSeconds
    }

    /**
     * When true, all pattern matching is case-insensitive.
     *
     * <p>Default: false</p>
     */
    Property<Boolean> getCaseInsensitive() {
        return caseInsensitive
    }

    /**
     * When true, logs detailed progress during polling.
     *
     * <p>Default: false</p>
     */
    Property<Boolean> getVerbose() {
        return verbose
    }

    /**
     * When > 0, logs a summary of pattern match status at this interval (in seconds).
     * Set to 0 to disable periodic progress logging.
     *
     * <p>Default: 0 (disabled)</p>
     */
    Property<Integer> getProgressIntervalSeconds() {
        return progressIntervalSeconds
    }
}
