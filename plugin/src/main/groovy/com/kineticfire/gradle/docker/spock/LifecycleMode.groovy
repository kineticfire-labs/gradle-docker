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

/**
 * Lifecycle modes for Docker Compose orchestration in Spock tests.
 *
 * <p>Determines when containers start and stop during test execution:</p>
 * <ul>
 *   <li><b>CLASS</b> - Containers start once per test class (setupSpec/cleanupSpec)</li>
 *   <li><b>METHOD</b> - Containers start fresh for each test method (setup/cleanup)</li>
 * </ul>
 *
 * @see ComposeUp
 * @since 1.0.0
 */
enum LifecycleMode {
    /**
     * Containers start once before all test methods in a class (in setupSpec)
     * and remain running throughout all tests in the class. Containers stop
     * once after all test methods complete (in cleanupSpec).
     *
     * <p>Use CLASS lifecycle when:</p>
     * <ul>
     *   <li>Tests can share the same container environment</li>
     *   <li>Tests build on each other (workflow tests)</li>
     *   <li>Container startup time is expensive</li>
     *   <li>Tests perform read-only operations</li>
     * </ul>
     *
     * <p><b>Performance</b>: Fastest - containers start once</p>
     * <p><b>Isolation</b>: Low - tests share state</p>
     */
    CLASS,

    /**
     * Containers start fresh before each test method (in setup) and are
     * torn down after each test method completes (in cleanup).
     *
     * <p>Use METHOD lifecycle when:</p>
     * <ul>
     *   <li>Tests need complete isolation</li>
     *   <li>Tests modify database or application state</li>
     *   <li>Tests must be independent and idempotent</li>
     *   <li>Order independence is critical</li>
     * </ul>
     *
     * <p><b>Performance</b>: Slower - containers restart for each test</p>
     * <p><b>Isolation</b>: Maximum - each test gets fresh containers</p>
     */
    METHOD
}
