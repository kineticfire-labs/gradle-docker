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

package com.kineticfire.gradle.docker.junit.service;

import java.time.LocalDateTime;

/**
 * Service interface for time operations.
 * <p>
 * This interface abstracts time operations to enable testing and dependency injection.
 * It provides methods for getting the current time and sleeping for specified durations.
 */
public interface TimeService {

    /**
     * Gets the current date and time.
     *
     * @return the current date and time
     */
    LocalDateTime now();

    /**
     * Causes the currently executing thread to sleep for the specified number of milliseconds.
     *
     * @param millis the length of time to sleep in milliseconds
     * @throws InterruptedException if any thread has interrupted the current thread
     */
    void sleep(long millis) throws InterruptedException;
}