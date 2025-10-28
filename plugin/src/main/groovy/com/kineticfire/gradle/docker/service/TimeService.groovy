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

package com.kineticfire.gradle.docker.service

/**
 * Interface for time-related operations.
 * Enables fast testing by allowing mock implementations.
 * Implementations must be Serializable for Gradle configuration cache compatibility.
 */
interface TimeService extends Serializable {

    /**
     * Get the current time in milliseconds since epoch
     *
     * @return Current time in milliseconds
     */
    long currentTimeMillis()

    /**
     * Sleep for the specified number of milliseconds
     *
     * @param millis Number of milliseconds to sleep
     * @throws InterruptedException if the sleep is interrupted
     */
    void sleep(long millis)
}

/**
 * Default implementation using System time.
 * Keep this class minimal - it's the boundary to the system clock.
 * Configuration cache compatible (Serializable).
 */
class SystemTimeService implements TimeService {

    @Override
    long currentTimeMillis() {
        return System.currentTimeMillis()
    }

    @Override
    void sleep(long millis) {
        Thread.sleep(millis)
    }
}
