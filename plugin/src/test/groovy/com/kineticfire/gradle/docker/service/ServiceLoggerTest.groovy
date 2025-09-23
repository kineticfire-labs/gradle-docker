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

import spock.lang.Specification

/**
 * Unit tests for ServiceLogger interface and implementations
 */
class ServiceLoggerTest extends Specification {

    def "DefaultServiceLogger creates logger for given class"() {
        when:
        def serviceLogger = new DefaultServiceLogger(ServiceLoggerTest.class)

        then:
        serviceLogger != null
    }

    def "DefaultServiceLogger delegates all log methods without throwing exceptions"() {
        given:
        def serviceLogger = new DefaultServiceLogger(ServiceLoggerTest.class)
        def exception = new RuntimeException("Test exception")

        when:
        serviceLogger.info("Info message")
        serviceLogger.debug("Debug message")
        serviceLogger.warn("Warning message")
        serviceLogger.error("Error message")
        serviceLogger.error("Error with exception", exception)

        then:
        noExceptionThrown()
    }

    def "TestServiceLogger captures log messages"() {
        given:
        def testLogger = new TestServiceLogger()

        when:
        testLogger.info("Info message")
        testLogger.debug("Debug message")
        testLogger.warn("Warning message")
        testLogger.error("Error message")
        testLogger.error("Error with exception", new RuntimeException("test"))

        then:
        testLogger.logMessages.size() == 5
        testLogger.logMessages[0] == "INFO: Info message"
        testLogger.logMessages[1] == "DEBUG: Debug message"
        testLogger.logMessages[2] == "WARN: Warning message"
        testLogger.logMessages[3] == "ERROR: Error message"
        testLogger.logMessages[4] == "ERROR: Error with exception (java.lang.RuntimeException: test)"
    }

    /**
     * Test implementation of ServiceLogger for testing purposes
     */
    static class TestServiceLogger implements ServiceLogger {
        List<String> logMessages = []

        @Override
        void info(String message) {
            logMessages << "INFO: ${message}"
        }

        @Override
        void debug(String message) {
            logMessages << "DEBUG: ${message}"
        }

        @Override
        void error(String message, Throwable throwable) {
            logMessages << "ERROR: ${message} (${throwable?.toString()})"
        }

        @Override
        void error(String message) {
            logMessages << "ERROR: ${message}"
        }

        @Override
        void warn(String message) {
            logMessages << "WARN: ${message}"
        }
    }
}