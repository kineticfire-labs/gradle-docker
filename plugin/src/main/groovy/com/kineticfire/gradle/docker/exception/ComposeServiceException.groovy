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

/**
 * Exception thrown when Docker Compose service operations fail
 */
class ComposeServiceException extends RuntimeException {
    
    final ErrorType errorType
    final String suggestion
    
    enum ErrorType {
        COMPOSE_UNAVAILABLE("Docker Compose is not available. Please install Docker Compose v2."),
        COMPOSE_FILE_NOT_FOUND("Compose file not found. Check the file path."),
        SERVICE_START_FAILED("Service startup failed. Check compose configuration and dependencies."),
        SERVICE_STOP_FAILED("Service shutdown failed. Services may still be running."),
        SERVICE_TIMEOUT("Service did not reach desired state within timeout period."),
        PLATFORM_UNSUPPORTED("Docker Compose operations not supported on this platform."),
        LOGS_CAPTURE_FAILED("Failed to capture Docker Compose logs."),
        UNKNOWN("An unknown Docker Compose operation error occurred.")
        
        final String defaultSuggestion
        
        ErrorType(String defaultSuggestion) {
            this.defaultSuggestion = defaultSuggestion
        }
    }
    
    ComposeServiceException(String message, Throwable cause = null) {
        super(message, cause)
        this.errorType = ErrorType.UNKNOWN
        this.suggestion = ErrorType.UNKNOWN.defaultSuggestion
    }
    
    ComposeServiceException(ErrorType errorType, String message, Throwable cause = null) {
        super(message, cause)
        this.errorType = errorType
        this.suggestion = errorType.defaultSuggestion
    }
    
    ComposeServiceException(ErrorType errorType, String message, String suggestion, Throwable cause = null) {
        super(message, cause)
        this.errorType = errorType
        this.suggestion = suggestion ?: errorType.defaultSuggestion
    }
    
    /**
     * Get formatted error message with suggestion
     */
    String getFormattedMessage() {
        return "${message}\nSuggestion: ${suggestion}"
    }
    
    @Override
    String toString() {
        return "ComposeServiceException{errorType=${errorType}, message='${message}', suggestion='${suggestion}'}"
    }
}