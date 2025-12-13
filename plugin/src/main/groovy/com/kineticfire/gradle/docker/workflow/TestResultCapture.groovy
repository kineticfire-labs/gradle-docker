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

import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.testing.Test

/**
 * Captures test results from a Gradle test task
 *
 * Supports multiple result sources:
 * 1. JUnit XML reports (preferred)
 * 2. TestNG XML reports
 * 3. Task state fallback (when XML reports unavailable)
 */
class TestResultCapture {

    private static final Logger LOGGER = Logging.getLogger(TestResultCapture)

    /**
     * Capture test results from a task after successful execution
     *
     * @param task The test task that was executed
     * @return TestResult capturing the test outcomes
     */
    TestResult captureFromTask(Task task) {
        LOGGER.info("Capturing test results from task: {}", task.name)

        // Try to capture from JUnit XML reports first
        if (task instanceof Test) {
            def junitResult = captureFromJUnitXml((Test) task)
            if (junitResult != null) {
                LOGGER.info("Captured results from JUnit XML: {}", junitResult)
                return junitResult
            }
        }

        // Fallback to task state
        return captureFromTaskState(task)
    }

    /**
     * Capture a failure result when test task throws an exception
     *
     * @param task The test task that failed
     * @param exception The exception that was thrown
     * @return TestResult indicating failure
     */
    TestResult captureFailure(Task task, Exception exception) {
        LOGGER.info("Capturing failure result for task: {} with exception: {}", task.name, exception.message)

        // Try to capture actual counts from JUnit XML if available
        if (task instanceof Test) {
            def junitResult = captureFromJUnitXml((Test) task)
            if (junitResult != null) {
                LOGGER.info("Captured failure results from JUnit XML: {}", junitResult)
                return junitResult
            }
        }

        // Fallback to a failure result with minimal info
        return new TestResult(
            false,  // success
            0,      // executed
            0,      // upToDate
            0,      // skipped
            1,      // failureCount (at least 1 failure)
            0       // totalCount
        )
    }

    /**
     * Capture results from JUnit XML reports
     *
     * @param testTask The test task
     * @return TestResult if XML reports found, null otherwise
     */
    TestResult captureFromJUnitXml(Test testTask) {
        def reportsDir = findJUnitReportsDir(testTask)
        if (reportsDir == null || !reportsDir.exists()) {
            LOGGER.debug("JUnit reports directory not found for task: {}", testTask.name)
            return null
        }

        def xmlFiles = reportsDir.listFiles()?.findAll { it.name.endsWith('.xml') }
        if (xmlFiles == null || xmlFiles.isEmpty()) {
            LOGGER.debug("No JUnit XML files found in: {}", reportsDir)
            return null
        }

        return parseJUnitXmlFiles(xmlFiles)
    }

    /**
     * Find the JUnit XML reports directory for a test task
     *
     * CONFIGURATION CACHE NOTE: We cannot access testTask.project at execution time.
     * Instead, we use the Test task's reports configuration which is already available.
     */
    File findJUnitReportsDir(Test testTask) {
        // Try the reports.junitXml.outputLocation first (configuration-cache safe)
        try {
            def junitXmlDir = testTask.reports.junitXml.outputLocation.asFile.orNull
            if (junitXmlDir?.exists()) {
                return junitXmlDir
            }
        } catch (Exception e) {
            LOGGER.debug("Could not access junitXml output location: {}", e.message)
        }

        // Fallback: derive build directory from the junitXml destination
        // This avoids accessing testTask.project at execution time
        try {
            def junitXmlDir = testTask.reports.junitXml.outputLocation.asFile.orNull
            if (junitXmlDir != null) {
                // junitXml is typically in build/test-results/{taskName}
                // So build dir is parent.parent of that
                def buildDir = junitXmlDir.parentFile?.parentFile
                if (buildDir != null) {
                    def standardDir = new File(buildDir, "test-results/${testTask.name}")
                    if (standardDir.exists()) {
                        return standardDir
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not derive build directory: {}", e.message)
        }

        return null
    }

    /**
     * Parse JUnit XML files and aggregate results
     */
    TestResult parseJUnitXmlFiles(List<File> xmlFiles) {
        int totalTests = 0
        int totalFailures = 0
        int totalErrors = 0
        int totalSkipped = 0

        xmlFiles.each { file ->
            try {
                def parsed = parseJUnitXmlFile(file)
                totalTests += parsed.tests
                totalFailures += parsed.failures
                totalErrors += parsed.errors
                totalSkipped += parsed.skipped
            } catch (Exception e) {
                LOGGER.warn("Failed to parse JUnit XML file {}: {}", file.name, e.message)
            }
        }

        int failureCount = totalFailures + totalErrors
        boolean success = failureCount == 0

        return new TestResult(
            success,
            totalTests - totalSkipped,  // executed
            0,                           // upToDate (not tracked in JUnit XML)
            totalSkipped,
            failureCount,
            totalTests
        )
    }

    /**
     * Parse a single JUnit XML file
     *
     * @return Map with tests, failures, errors, skipped counts
     */
    Map<String, Integer> parseJUnitXmlFile(File xmlFile) {
        def xml = new XmlSlurper().parse(xmlFile)

        // JUnit XML format: <testsuite tests="X" failures="Y" errors="Z" skipped="W">
        int tests = safeParseInt(xml.@tests?.toString(), 0)
        int failures = safeParseInt(xml.@failures?.toString(), 0)
        int errors = safeParseInt(xml.@errors?.toString(), 0)
        int skipped = safeParseInt(xml.@skipped?.toString(), 0)

        return [
            tests: tests,
            failures: failures,
            errors: errors,
            skipped: skipped
        ]
    }

    /**
     * Safely parse an integer from a string
     */
    int safeParseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue
        }
        try {
            return Integer.parseInt(value)
        } catch (NumberFormatException e) {
            return defaultValue
        }
    }

    /**
     * Capture results from task state (fallback)
     *
     * @param task The task to inspect
     * @return TestResult based on task state
     */
    TestResult captureFromTaskState(Task task) {
        // Since we're executing tasks programmatically via actions, state may not be available
        // Assume success if we got here without exception
        LOGGER.debug("Using task state fallback for task: {}", task.name)

        return new TestResult(
            true,   // success (task completed without exception)
            1,      // executed (at least the task ran)
            0,      // upToDate
            0,      // skipped
            0,      // failureCount
            1       // totalCount
        )
    }

    /**
     * Create a successful result with default counts
     * Used when no detailed information is available
     */
    TestResult createSuccessResult() {
        return new TestResult(true, 1, 0, 0, 0, 1)
    }

    /**
     * Create a failed result with default counts
     * Used when no detailed information is available
     */
    TestResult createFailureResult() {
        return new TestResult(false, 0, 0, 0, 1, 1)
    }
}
