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
 * Exception thrown when Docker service operations fail
 */
class DockerServiceException extends RuntimeException {
    
    final ErrorType errorType
    final String suggestion
    
    enum ErrorType {
        DAEMON_UNAVAILABLE("Docker daemon is not running. Please start Docker and try again."),
        NETWORK_ERROR("Network connectivity issue. Check your internet connection and proxy settings."),
        AUTHENTICATION_FAILED("Authentication failed. Verify your registry credentials."),
        IMAGE_NOT_FOUND("Image not found. Check the image name and tag."),
        BUILD_FAILED("Docker build failed. Check your Dockerfile and build context."),
        TAG_FAILED("Image tagging failed. Verify the image exists and tag format is correct."),
        PUSH_FAILED("Image push failed. Check registry accessibility and credentials."),
        PULL_FAILED("Image pull failed. Check image reference and registry accessibility."),
        SAVE_FAILED("Image save failed. Check output directory permissions and disk space."),
        UNKNOWN("An unknown Docker operation error occurred.")
        
        final String defaultSuggestion
        
        ErrorType(String defaultSuggestion) {
            this.defaultSuggestion = defaultSuggestion
        }
    }
    
    DockerServiceException(ErrorType errorType, String message, Throwable cause = null) {
        super(message, cause)
        this.errorType = errorType
        this.suggestion = errorType.defaultSuggestion
    }
    
    DockerServiceException(ErrorType errorType, String message, String suggestion, Throwable cause = null) {
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
        return "DockerServiceException{errorType=${errorType}, message='${message}', suggestion='${suggestion}'}"
    }
}