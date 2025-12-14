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
 * Defines the container lifecycle mode for Docker Compose test integration.
 *
 * This enum is shared between the {@code dockerProject} and {@code dockerTest} DSLs,
 * providing type-safe configuration of when containers should start/stop relative
 * to test execution.
 *
 * GRADLE 9/10 COMPATIBILITY NOTE: This enum is serialization-safe for configuration cache.
 */
enum Lifecycle {

    /**
     * Containers start once per test class and are reused across all test methods.
     *
     * Use this mode when:
     * - Tests don't modify shared state
     * - Container startup is expensive
     * - Tests are independent of container state
     *
     * Task flow: composeUp -> all tests in class -> composeDown
     */
    CLASS,

    /**
     * Containers restart for each individual test method.
     *
     * Use this mode when:
     * - Each test needs a fresh container state
     * - Tests modify shared state (database records, files, etc.)
     * - Test isolation is more important than performance
     *
     * Task flow: composeUp -> test method -> composeDown (repeated per method)
     */
    METHOD;

    /**
     * Parse a string value to a Lifecycle enum.
     *
     * @param value The string value to parse (case-insensitive)
     * @return The corresponding Lifecycle enum value
     * @throws IllegalArgumentException if the value is not recognized
     */
    static Lifecycle fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Lifecycle value cannot be null")
        }
        switch (value.toLowerCase()) {
            case 'class':
                return CLASS
            case 'method':
                return METHOD
            default:
                throw new IllegalArgumentException(
                    "Invalid lifecycle value '${value}'. Must be 'class' or 'method'."
                )
        }
    }

    /**
     * Returns the lowercase string representation suitable for system properties.
     *
     * @return The lowercase name of this lifecycle
     */
    String toPropertyValue() {
        return name().toLowerCase()
    }
}
