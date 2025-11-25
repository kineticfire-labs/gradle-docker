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

package com.kineticfire.gradle.docker.workflow

/**
 * Data class representing the result of test execution
 *
 * Captures test outcomes for use in conditional workflow logic.
 */
class TestResult implements Serializable {

    private static final long serialVersionUID = 1L

    final boolean success
    final int executed
    final int upToDate
    final int skipped
    final int failureCount
    final int totalCount

    TestResult(
        boolean success,
        int executed,
        int upToDate,
        int skipped,
        int failureCount,
        int totalCount
    ) {
        this.success = success
        this.executed = executed
        this.upToDate = upToDate
        this.skipped = skipped
        this.failureCount = failureCount
        this.totalCount = totalCount
    }

    boolean isSuccess() {
        return success
    }

    int getFailureCount() {
        return failureCount
    }

    @Override
    String toString() {
        return "TestResult[success=${success}, executed=${executed}, upToDate=${upToDate}, " +
               "skipped=${skipped}, failureCount=${failureCount}, totalCount=${totalCount}]"
    }
}
