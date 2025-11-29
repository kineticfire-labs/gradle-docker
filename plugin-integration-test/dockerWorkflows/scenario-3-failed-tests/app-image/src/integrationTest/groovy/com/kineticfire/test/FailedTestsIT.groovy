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

import spock.lang.Specification

/**
 * Integration tests that are DESIGNED TO FAIL.
 *
 * This test class is part of scenario-3-failed-tests, which verifies the failure path
 * in dockerWorkflows pipelines. When these tests fail:
 *
 * 1. The pipeline should fail
 * 2. onTestSuccess should NOT execute (no 'tested' or 'verified' tags applied)
 * 3. Cleanup (composeDown) should still run
 * 4. The base image should still exist (build step completed before failure)
 *
 * IMPORTANT: These tests intentionally fail. The wrapper task (verifyFailedPipeline)
 * runs the pipeline and validates that failure behavior is correct.
 */
class FailedTestsIT extends Specification {

    /**
     * This test intentionally fails to trigger the failure path.
     *
     * The purpose is to verify that when integration tests fail:
     * - The pipeline task fails
     * - The onTestSuccess block is NOT executed
     * - Cleanup still runs via finalizedBy
     */
    def "intentionally failing test - verifies failure path"() {
        expect:
        // This assertion intentionally fails
        1 == 2
    }

    /**
     * Second intentionally failing test to ensure multiple failures are handled.
     */
    def "another intentionally failing test"() {
        when:
        def result = "failure"

        then:
        // This assertion intentionally fails
        result == "success"
    }
}
