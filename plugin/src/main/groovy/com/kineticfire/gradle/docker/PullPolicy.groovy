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

package com.kineticfire.gradle.docker

/**
 * Defines the image pull policy for Docker operations.
 *
 * This enum is shared between the {@code dockerProject} and {@code docker} DSLs,
 * providing type-safe configuration of when images should be pulled from registries.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: This enum is serialization-safe for configuration cache.
 */
enum PullPolicy {

    /**
     * Never pull the image from the registry.
     *
     * The build will fail if the image is not available locally.
     * This is the safest default as it prevents unexpected network operations
     * and ensures builds use exactly the expected local image.
     *
     * Use this mode when:
     * - You've already pulled the image manually
     * - You want builds to fail if the expected image isn't present
     * - You're in an air-gapped or offline environment
     */
    NEVER,

    /**
     * Pull the image only if it's not present locally.
     *
     * If the image exists locally, it will be used as-is (even if a newer
     * version exists in the registry). If the image doesn't exist locally,
     * it will be pulled.
     *
     * Use this mode when:
     * - You want convenience without always pulling
     * - Network availability is unreliable
     * - You want to minimize build time but need fallback
     *
     * Note: This replaces the deprecated {@code pullIfMissing(true)} method.
     */
    IF_MISSING,

    /**
     * Always pull the image from the registry.
     *
     * The image will be pulled even if it exists locally. This ensures
     * you always have the latest version but requires network access
     * and increases build time.
     *
     * Use this mode when:
     * - You need to ensure the latest image version is used
     * - You're using mutable tags like 'latest'
     * - You're building in CI/CD where reproducibility requires fresh pulls
     */
    ALWAYS;

    /**
     * Parse a string value to a PullPolicy enum.
     *
     * @param value The string value to parse (case-insensitive)
     * @return The corresponding PullPolicy enum value
     * @throws IllegalArgumentException if the value is not recognized
     */
    static PullPolicy fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("PullPolicy value cannot be null")
        }
        switch (value.toLowerCase()) {
            case 'never':
                return NEVER
            case 'if_missing':
            case 'ifmissing':
                return IF_MISSING
            case 'always':
                return ALWAYS
            default:
                throw new IllegalArgumentException(
                    "Invalid pull policy value '${value}'. " +
                    "Must be 'never', 'if_missing', or 'always'."
                )
        }
    }

    /**
     * Convert a boolean pullIfMissing value to PullPolicy.
     *
     * This is used for backward compatibility with the deprecated pullIfMissing method.
     *
     * @param pullIfMissing The boolean value from the deprecated API
     * @return NEVER if false, IF_MISSING if true
     */
    static PullPolicy fromPullIfMissing(boolean pullIfMissing) {
        return pullIfMissing ? IF_MISSING : NEVER
    }

    /**
     * Returns the lowercase string representation suitable for configuration.
     *
     * @return The lowercase name of this policy with underscore replaced by hyphen
     */
    String toPropertyValue() {
        return name().toLowerCase().replace('_', '-')
    }
}
