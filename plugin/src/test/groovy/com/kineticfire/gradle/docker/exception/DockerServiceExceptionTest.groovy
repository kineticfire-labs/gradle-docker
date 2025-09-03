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
 * Unit tests for DockerServiceException
 */
class DockerServiceExceptionTest extends Specification {

    def "constructor with errorType and message creates exception with default suggestion"() {
        given:
        def errorType = DockerServiceException.ErrorType.DAEMON_UNAVAILABLE
        def message = "Docker daemon connection failed"

        when:
        def exception = new DockerServiceException(errorType, message)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == errorType.defaultSuggestion
        exception.cause == null
    }

    def "constructor with errorType, message, and cause creates exception with default suggestion"() {
        given:
        def errorType = DockerServiceException.ErrorType.NETWORK_ERROR
        def message = "Connection timeout"
        def cause = new IOException("Connection refused")

        when:
        def exception = new DockerServiceException(errorType, message, cause)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == errorType.defaultSuggestion
        exception.cause == cause
    }

    def "constructor with errorType, message, custom suggestion, and cause creates exception with custom suggestion"() {
        given:
        def errorType = DockerServiceException.ErrorType.BUILD_FAILED
        def message = "Build process failed"
        def customSuggestion = "Check your custom Dockerfile syntax"
        def cause = new RuntimeException("Syntax error")

        when:
        def exception = new DockerServiceException(errorType, message, customSuggestion, cause)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == customSuggestion
        exception.cause == cause
    }

    def "constructor with errorType, message, null suggestion, and cause uses default suggestion"() {
        given:
        def errorType = DockerServiceException.ErrorType.TAG_FAILED
        def message = "Tag operation failed"
        String nullSuggestion = null
        def cause = new RuntimeException("Invalid tag format")

        when:
        def exception = new DockerServiceException(errorType, message, nullSuggestion, cause)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == errorType.defaultSuggestion
        exception.cause == cause
    }

    def "constructor with errorType, message, empty suggestion, and cause uses default suggestion"() {
        given:
        def errorType = DockerServiceException.ErrorType.PUSH_FAILED
        def message = "Push operation failed"
        def emptySuggestion = ""
        def cause = new RuntimeException("Authentication error")

        when:
        def exception = new DockerServiceException(errorType, message, emptySuggestion, cause)

        then:
        exception.message == message
        exception.errorType == errorType
        exception.suggestion == errorType.defaultSuggestion
        exception.cause == cause
    }

    def "getFormattedMessage returns message with suggestion"() {
        given:
        def errorType = DockerServiceException.ErrorType.IMAGE_NOT_FOUND
        def message = "Image alpine:latest not found"
        def exception = new DockerServiceException(errorType, message)

        when:
        def formattedMessage = exception.getFormattedMessage()

        then:
        formattedMessage == "${message}\nSuggestion: ${errorType.defaultSuggestion}"
    }

    def "getFormattedMessage returns message with custom suggestion"() {
        given:
        def errorType = DockerServiceException.ErrorType.SAVE_FAILED
        def message = "Cannot save image to disk"
        def customSuggestion = "Free up disk space and try again"
        def exception = new DockerServiceException(errorType, message, customSuggestion)

        when:
        def formattedMessage = exception.getFormattedMessage()

        then:
        formattedMessage == "${message}\nSuggestion: ${customSuggestion}"
    }

    def "toString returns formatted string representation"() {
        given:
        def errorType = DockerServiceException.ErrorType.AUTHENTICATION_FAILED
        def message = "Registry login failed"
        def exception = new DockerServiceException(errorType, message)

        when:
        def toStringResult = exception.toString()

        then:
        toStringResult == "DockerServiceException{errorType=${errorType}, message='${message}', suggestion='${errorType.defaultSuggestion}'}"
    }

    def "toString returns formatted string representation with custom suggestion"() {
        given:
        def errorType = DockerServiceException.ErrorType.PULL_FAILED
        def message = "Image pull failed"
        def customSuggestion = "Check your network connection"
        def exception = new DockerServiceException(errorType, message, customSuggestion)

        when:
        def toStringResult = exception.toString()

        then:
        toStringResult == "DockerServiceException{errorType=${errorType}, message='${message}', suggestion='${customSuggestion}'}"
    }

    def "all ErrorType enum values have non-null default suggestions"() {
        expect:
        DockerServiceException.ErrorType.values().each { errorType ->
            assert errorType.defaultSuggestion != null
            assert !errorType.defaultSuggestion.isEmpty()
        }
    }

    def "ErrorType enum contains expected error types"() {
        given:
        def expectedErrorTypes = [
            'DAEMON_UNAVAILABLE',
            'NETWORK_ERROR', 
            'AUTHENTICATION_FAILED',
            'IMAGE_NOT_FOUND',
            'BUILD_FAILED',
            'TAG_FAILED',
            'PUSH_FAILED',
            'PULL_FAILED',
            'SAVE_FAILED',
            'UNKNOWN'
        ]

        when:
        def actualErrorTypes = DockerServiceException.ErrorType.values().collect { it.name() }

        then:
        actualErrorTypes.containsAll(expectedErrorTypes)
        expectedErrorTypes.containsAll(actualErrorTypes)
    }

    def "ErrorType default suggestions are appropriate"() {
        expect:
        DockerServiceException.ErrorType.DAEMON_UNAVAILABLE.defaultSuggestion.contains("Docker daemon")
        DockerServiceException.ErrorType.NETWORK_ERROR.defaultSuggestion.contains("Network")
        DockerServiceException.ErrorType.AUTHENTICATION_FAILED.defaultSuggestion.contains("credentials")
        DockerServiceException.ErrorType.IMAGE_NOT_FOUND.defaultSuggestion.contains("image name")
        DockerServiceException.ErrorType.BUILD_FAILED.defaultSuggestion.contains("Dockerfile")
        DockerServiceException.ErrorType.TAG_FAILED.defaultSuggestion.contains("tag format")
        DockerServiceException.ErrorType.PUSH_FAILED.defaultSuggestion.contains("registry")
        DockerServiceException.ErrorType.PULL_FAILED.defaultSuggestion.contains("image reference")
        DockerServiceException.ErrorType.SAVE_FAILED.defaultSuggestion.contains("permissions")
        DockerServiceException.ErrorType.UNKNOWN.defaultSuggestion.contains("unknown")
    }

    def "exception maintains RuntimeException inheritance"() {
        given:
        def exception = new DockerServiceException(DockerServiceException.ErrorType.UNKNOWN, "Test message")

        expect:
        exception instanceof RuntimeException
        exception instanceof Exception
        exception instanceof Throwable
    }
}