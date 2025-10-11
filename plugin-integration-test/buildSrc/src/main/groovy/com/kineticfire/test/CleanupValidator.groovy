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

package com.kineticfire.test

import groovy.transform.CompileStatic

/**
 * Validator for cleanup operations
 * Ensures no Docker resources leak after tests
 */
@CompileStatic
class CleanupValidator {

    /**
     * Find lingering containers for a project
     */
    static List<String> findLingeringContainers(String projectName) {
        return DockerComposeValidator.findAllContainers(projectName)
    }

    /**
     * Find lingering networks for a project
     */
    static List<String> findLingeringNetworks(String projectName) {
        return DockerComposeValidator.findAllNetworks(projectName)
    }

    /**
     * Assert no resource leaks
     */
    static void assertNoLeaks(String projectName) {
        DockerComposeValidator.assertNoLeaks(projectName)
    }

    /**
     * Force cleanup of all resources
     */
    static void forceCleanup(String projectName) {
        DockerComposeValidator.forceCleanup(projectName)
    }

    /**
     * Verify cleanup after test
     */
    static void verifyCleanup(String projectName, boolean forceCleanupOnFailure = true) {
        try {
            assertNoLeaks(projectName)
        } catch (AssertionError e) {
            if (forceCleanupOnFailure) {
                System.err.println("Cleanup verification failed, forcing cleanup...")
                forceCleanup(projectName)
            }
            throw e
        }
    }
}
