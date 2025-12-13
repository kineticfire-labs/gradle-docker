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

import com.kineticfire.test.DockerComposeValidator
import com.kineticfire.test.StateFileValidator
import com.kineticfire.test.CleanupValidator
import spock.lang.Specification

/**
 * Verification Test: Log Capture Functionality
 *
 * INTERNAL TEST - Validates plugin log capture mechanics, not application behavior.
 *
 * This test validates that the dockerTest plugin correctly:
 * - Captures container logs to configured output files
 * - Respects tailLines configuration (limits number of lines)
 * - Supports service-specific log capture
 * - Creates log files at expected locations
 * - Captures startup logs, request logs, and multi-line logs
 * - Works with different log capture configurations
 *
 * For user-facing examples of testing applications, see examples/web-app/
 */
class LogsCapturePluginIT extends Specification {

    static String projectNameFull
    static Map stateDataFull
    static File logFileFull

    static String projectNameTail
    static Map stateDataTail
    static File logFileTail

    static String projectNameService
    static Map stateDataService
    static File logFileService

    def setupSpec() {
        // Read system properties set by Gradle for full log test
        projectNameFull = System.getProperty('COMPOSE_PROJECT_NAME_FULL')
        def stateFilePathFull = System.getProperty('COMPOSE_STATE_FILE_FULL')
        def logFilePathFull = System.getProperty('COMPOSE_LOG_FILE_FULL')

        // Read system properties set by Gradle for tail log test
        projectNameTail = System.getProperty('COMPOSE_PROJECT_NAME_TAIL')
        def stateFilePathTail = System.getProperty('COMPOSE_STATE_FILE_TAIL')
        def logFilePathTail = System.getProperty('COMPOSE_LOG_FILE_TAIL')

        // Read system properties set by Gradle for service log test
        projectNameService = System.getProperty('COMPOSE_PROJECT_NAME_SERVICE')
        def stateFilePathService = System.getProperty('COMPOSE_STATE_FILE_SERVICE')
        def logFilePathService = System.getProperty('COMPOSE_LOG_FILE_SERVICE')

        println "=== Verification: Logs Capture Plugin Mechanics ==="
        println "Full Log Test - Project: ${projectNameFull}, State: ${stateFilePathFull}, Log: ${logFilePathFull}"
        println "Tail Log Test - Project: ${projectNameTail}, State: ${stateFilePathTail}, Log: ${logFilePathTail}"
        println "Service Log Test - Project: ${projectNameService}, State: ${stateFilePathService}, Log: ${logFilePathService}"

        // Parse state files
        stateDataFull = StateFileValidator.parseStateFile(new File(stateFilePathFull))
        stateDataTail = StateFileValidator.parseStateFile(new File(stateFilePathTail))
        stateDataService = StateFileValidator.parseStateFile(new File(stateFilePathService))

        // Get log files
        logFileFull = new File(logFilePathFull)
        logFileTail = new File(logFilePathTail)
        logFileService = new File(logFilePathService)

        // Note: Containers are already stopped by composeDown (which captured logs)
        // We're verifying logs from natural startup + health checks, not triggered requests
    }

    def cleanupSpec() {
        // Force cleanup even if tests fail
        [projectNameFull, projectNameTail, projectNameService].each { projectName ->
            try {
                println "=== Forcing cleanup of Docker Compose stack: ${projectName} ==="
                def process = ['docker', 'compose', '-p', projectName, 'down', '-v'].execute()
                process.waitFor()
                if (process.exitValue() != 0) {
                    println "Warning: docker compose down returned ${process.exitValue()}"
                } else {
                    println "Successfully cleaned up compose stack"
                }
            } catch (Exception e) {
                println "Warning: Failed to cleanup Docker Compose stack: ${e.message}"
            }
        }
    }

    static void triggerLogMessages(Map stateData, String testType) {
        try {
            def appPort = StateFileValidator.getPublishedPort(stateData, 'logs-capture-app', 8080)

            // Trigger info log
            def infoUrl = new URL("http://localhost:${appPort}/log/info")
            infoUrl.openConnection().getInputStream().text

            // Trigger warn log
            def warnUrl = new URL("http://localhost:${appPort}/log/warn")
            warnUrl.openConnection().getInputStream().text

            // Trigger error log
            def errorUrl = new URL("http://localhost:${appPort}/log/error")
            errorUrl.openConnection().getInputStream().text

            // Trigger multi-line logs
            def multilineUrl = new URL("http://localhost:${appPort}/multiline")
            multilineUrl.openConnection().getInputStream().text

            println "Triggered log messages for ${testType} test"
        } catch (Exception e) {
            println "Warning: Could not trigger log messages: ${e.message}"
        }
    }

    // ========== Full Log Capture Tests ==========

    def "plugin should generate valid state file for full log test"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateDataFull, 'logsCaptureTestFull', projectNameFull)

        and: "service is present"
        def serviceNames = StateFileValidator.getServiceNames(stateDataFull)
        serviceNames.contains('logs-capture-app')
        serviceNames.size() == 1
    }

    // Note: Container health checks are skipped because containers are stopped
    // before tests run (logs are captured during composeDown)

    def "plugin should create log file for full log capture"() {
        expect: "log file exists"
        logFileFull.exists()

        and: "log file is not empty"
        logFileFull.length() > 0

        and: "log file is readable"
        logFileFull.canRead()
    }

    def "full log file should contain startup messages"() {
        when: "we read the log file"
        def logContent = logFileFull.text

        then: "log contains application startup message"
        logContent.contains("Starting Logs Capture Test Application")

        and: "log contains initialization messages"
        logContent.contains("Application initialized successfully")
        logContent.contains("Log capture verification test is ready")
    }

    def "full log file should contain warning and error messages"() {
        when: "we read the log file"
        def logContent = logFileFull.text

        then: "log contains warning message"
        logContent.contains("This is a warning message for testing")

        and: "log contains error message"
        logContent.contains("This is an error message for testing")
    }

    def "full log file should contain multi-line test messages"() {
        when: "we read the log file"
        def logContent = logFileFull.text

        then: "log contains multi-line test markers"
        logContent.contains("Multi-line log test - Line 1")
        logContent.contains("Multi-line log test - Line 5")
    }

    def "full log file should contain application logs"() {
        when: "we read the log file"
        def logContent = logFileFull.text

        then: "log contains initialization messages"
        logContent.contains("Application initialized successfully")
        logContent.contains("Log capture verification test is ready")

        and: "log contains warning and error messages"
        logContent.contains("This is a warning message for testing")
        logContent.contains("This is an error message for testing")

        and: "log contains multi-line test markers"
        logContent.contains("Multi-line log test - Line 1")
        logContent.contains("Multi-line log test - Line 5")
    }

    // ========== Tail Log Capture Tests ==========

    def "plugin should generate valid state file for tail log test"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateDataTail, 'logsCaptureTestTail', projectNameTail)
    }


    def "plugin should create log file for tail log capture"() {
        expect: "log file exists"
        logFileTail.exists()

        and: "log file is not empty"
        logFileTail.length() > 0
    }

    def "tail log file should respect tailLines configuration"() {
        when: "we read the log file and count lines"
        def logLines = logFileTail.readLines()
        def lineCount = logLines.size()

        then: "log file contains at most 20 lines (tailLines = 20)"
        lineCount <= 20

        and: "log file contains recent logs (last lines)"
        // Should contain recent triggered messages, not necessarily startup
        logLines.any { it.contains("Test log message") || it.contains("Multi-line") }
    }

    // ========== Service-Specific Log Capture Tests ==========

    def "plugin should generate valid state file for service log test"() {
        expect: "state file has required fields"
        StateFileValidator.assertValidStructure(stateDataService, 'logsCaptureTestService', projectNameService)
    }


    def "plugin should create log file for service-specific log capture"() {
        expect: "log file exists"
        logFileService.exists()

        and: "log file is not empty"
        logFileService.length() > 0
    }

    def "service-specific log file should contain logs from specified service"() {
        when: "we read the log file"
        def logContent = logFileService.text

        then: "log contains application messages (from logs-capture-app service)"
        logContent.contains("Logs Capture Test Application") ||
            logContent.contains("Application initialized") ||
            logContent.contains("Test log message")
    }

    def "service-specific log file should respect tailLines configuration"() {
        when: "we read the log file and count lines"
        def logLines = logFileService.readLines()
        def lineCount = logLines.size()

        then: "log file contains at most 100 lines (tailLines = 100)"
        lineCount <= 100
    }

    // ========== Log File Comparison Tests ==========

    def "full log should be longer than tail log"() {
        when: "we count lines in both files"
        def fullLogLines = logFileFull.readLines().size()
        def tailLogLines = logFileTail.readLines().size()

        then: "full log has more lines than tail log"
        fullLogLines > tailLogLines

        and: "tail log respects the 20 line limit"
        tailLogLines <= 20
    }

    def "all log files should be accessible and valid"() {
        expect: "all log files exist and are readable"
        logFileFull.exists() && logFileFull.canRead() && logFileFull.length() > 0
        logFileTail.exists() && logFileTail.canRead() && logFileTail.length() > 0
        logFileService.exists() && logFileService.canRead() && logFileService.length() > 0
    }
}
