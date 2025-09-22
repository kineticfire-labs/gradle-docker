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
 * Default implementation of TimeService using standard Java time APIs.
 * <p>
 * This implementation uses LocalDateTime.now() and Thread.sleep() for time operations.
 */
public class DefaultTimeService implements TimeService {

    /**
     * Creates a new DefaultTimeService instance.
     */
    public DefaultTimeService() {
        // Default constructor
    }

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}