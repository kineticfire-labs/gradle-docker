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

package com.kineticfire.gradle.docker.exception

import spock.lang.Specification

/**
 * Unit tests for ComposeServiceException
 */
class ComposeServiceExceptionTest extends Specification {

    def "constructor with message creates exception with UNKNOWN errorType and default suggestion"() {
        given:
        def message = "Compose operation failed"

        when:
        def exception = new ComposeServiceException(message)

        then:
        exception.message == message
        exception.errorType == ComposeServiceException.ErrorType.UNKNOWN
        exception.suggestion == ComposeServiceException.ErrorType.UNKNOWN.defaultSuggestion
        exception.cause == null
    }

    def "constructor with message and cause creates exception with UNKNOWN errorType and default suggestion"() {
        given:
        def message = "Compose startup failed"
        def cause = new IOException("File not found")

        when:
        def exception = new ComposeServiceException(message, cause)

        then:
        exception.message == message
        exception.errorType == ComposeServiceException.ErrorType.UNKNOWN
        exception.suggestion == ComposeServiceException.ErrorType.UNKNOWN.defaultSuggestion
        exception.cause == cause
    }

    def "constructor with errorType and message creates exception with default suggestion"() {
        given:
        def errorType = ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE
        def message = "Docker Compose not found"

        when:
        def exception = new ComposeServiceException(errorType, message)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == errorType.defaultSuggestion
        exception.cause == null
    }

    def "constructor with errorType, message, and cause creates exception with default suggestion"() {
        given:
        def errorType = ComposeServiceException.ErrorType.SERVICE_START_FAILED
        def message = "Service web failed to start"
        def cause = new RuntimeException("Port already in use")

        when:
        def exception = new ComposeServiceException(errorType, message, cause)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == errorType.defaultSuggestion
        exception.cause == cause
    }

    def "constructor with errorType, message, custom suggestion, and cause creates exception with custom suggestion"() {
        given:
        def errorType = ComposeServiceException.ErrorType.COMPOSE_FILE_NOT_FOUND
        def message = "Cannot locate docker-compose.yml"
        def customSuggestion = "Create a docker-compose.yml file in your project root"
        def cause = new FileNotFoundException("docker-compose.yml")

        when:
        def exception = new ComposeServiceException(errorType, message, customSuggestion, cause)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == customSuggestion
        exception.cause == cause
    }

    def "constructor with errorType, message, null suggestion, and cause uses default suggestion"() {
        given:
        def errorType = ComposeServiceException.ErrorType.SERVICE_STOP_FAILED
        def message = "Service shutdown failed"
        String nullSuggestion = null
        def cause = new RuntimeException("Process not responding")

        when:
        def exception = new ComposeServiceException(errorType, message, nullSuggestion, cause)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == errorType.defaultSuggestion
        exception.cause == cause
    }

    def "constructor with errorType, message, empty suggestion, and cause uses default suggestion"() {
        given:
        def errorType = ComposeServiceException.ErrorType.SERVICE_TIMEOUT
        def message = "Service did not start within timeout"
        def emptySuggestion = ""
        def cause = new RuntimeException("Timeout expired")

        when:
        def exception = new ComposeServiceException(errorType, message, emptySuggestion, cause)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == errorType.defaultSuggestion
        exception.cause == cause
    }

    def "getFormattedMessage returns message with suggestion"() {
        given:
        def errorType = ComposeServiceException.ErrorType.PLATFORM_UNSUPPORTED
        def message = "Compose not supported on this OS"
        def exception = new ComposeServiceException(errorType, message)

        when:
        def formattedMessage = exception.getFormattedMessage()

        then:
        formattedMessage == "${message}\nSuggestion: ${errorType.defaultSuggestion}"
    }

    def "getFormattedMessage returns message with custom suggestion"() {
        given:
        def errorType = ComposeServiceException.ErrorType.SERVICE_START_FAILED
        def message = "Database service failed to start"
        def customSuggestion = "Check database configuration and ensure port 5432 is available"
        def exception = new ComposeServiceException(errorType, message, customSuggestion)

        when:
        def formattedMessage = exception.getFormattedMessage()

        then:
        formattedMessage == "${message}\nSuggestion: ${customSuggestion}"
    }

    def "toString returns formatted string representation"() {
        given:
        def errorType = ComposeServiceException.ErrorType.COMPOSE_FILE_NOT_FOUND
        def message = "Compose file missing"
        def exception = new ComposeServiceException(errorType, message)

        when:
        def toStringResult = exception.toString()

        then:
        toStringResult == "ComposeServiceException{errorType=${errorType}, message='${message}', suggestion='${errorType.defaultSuggestion}'}"
    }

    def "toString returns formatted string representation with custom suggestion"() {
        given:
        def errorType = ComposeServiceException.ErrorType.SERVICE_TIMEOUT
        def message = "Service startup timeout"
        def customSuggestion = "Increase the timeout value in your configuration"
        def exception = new ComposeServiceException(errorType, message, customSuggestion)

        when:
        def toStringResult = exception.toString()

        then:
        toStringResult == "ComposeServiceException{errorType=${errorType}, message='${message}', suggestion='${customSuggestion}'}"
    }

    def "all ErrorType enum values have non-null default suggestions"() {
        expect:
        ComposeServiceException.ErrorType.values().each { errorType ->
            assert errorType.defaultSuggestion != null
            assert !errorType.defaultSuggestion.isEmpty()
        }
    }

    def "ErrorType enum contains expected error types"() {
        given:
        def expectedErrorTypes = [
            'COMPOSE_UNAVAILABLE',
            'COMPOSE_FILE_NOT_FOUND',
            'SERVICE_START_FAILED',
            'SERVICE_STOP_FAILED',
            'SERVICE_TIMEOUT',
            'PLATFORM_UNSUPPORTED',
            'LOGS_CAPTURE_FAILED',
            'UNKNOWN'
        ]

        when:
        def actualErrorTypes = ComposeServiceException.ErrorType.values().collect { it.name() }

        then:
        actualErrorTypes.containsAll(expectedErrorTypes)
        expectedErrorTypes.containsAll(actualErrorTypes)
    }

    def "ErrorType default suggestions are appropriate"() {
        expect:
        ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE.defaultSuggestion.contains("Docker Compose")
        ComposeServiceException.ErrorType.COMPOSE_FILE_NOT_FOUND.defaultSuggestion.contains("file path")
        ComposeServiceException.ErrorType.SERVICE_START_FAILED.defaultSuggestion.contains("compose configuration")
        ComposeServiceException.ErrorType.SERVICE_STOP_FAILED.defaultSuggestion.contains("Services may still be running")
        ComposeServiceException.ErrorType.SERVICE_TIMEOUT.defaultSuggestion.contains("timeout period")
        ComposeServiceException.ErrorType.PLATFORM_UNSUPPORTED.defaultSuggestion.contains("not supported")
        ComposeServiceException.ErrorType.UNKNOWN.defaultSuggestion.contains("unknown")
    }

    def "exception maintains RuntimeException inheritance"() {
        given:
        def exception = new ComposeServiceException("Test message")

        expect:
        exception instanceof RuntimeException
        exception instanceof Exception
        exception instanceof Throwable
    }
}