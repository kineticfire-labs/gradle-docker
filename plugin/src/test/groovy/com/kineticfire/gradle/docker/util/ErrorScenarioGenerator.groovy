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

package com.kineticfire.gradle.docker.util

import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.exception.NotFoundException
import com.kineticfire.gradle.docker.exception.DockerServiceException
import com.kineticfire.gradle.docker.exception.ComposeServiceException

/**
 * Generates predefined Docker and Compose error conditions for comprehensive testing.
 * Includes network errors, authentication failures, permission issues, and resource constraints.
 */
class ErrorScenarioGenerator {
    
    /**
     * Docker daemon and connection errors
     */
    static class Connection {
        
        static DockerException daemonUnavailable() {
            return new DockerException("Cannot connect to the Docker daemon at unix:///var/run/docker.sock", 500)
        }
        
        static DockerException connectionTimeout() {
            return new DockerException("Read timeout", 500)
        }
        
        static DockerException permissionDenied() {
            return new DockerException("permission denied while trying to connect to Docker daemon socket", 403)
        }
        
        static DockerServiceException dockerServiceUnavailable() {
            return new DockerServiceException(
                DockerServiceException.ErrorType.DAEMON_UNAVAILABLE,
                "Docker daemon is not running or not accessible",
                "Ensure Docker is installed and running, and user has appropriate permissions"
            )
        }
    }
    
    /**
     * Build-related errors
     */
    static class Build {
        
        static DockerException dockerfileNotFound() {
            return new DockerException("Cannot locate specified Dockerfile", 400)
        }
        
        static DockerException dockerfileSyntaxError() {
            return new DockerException("Dockerfile parse error: Unknown instruction: INVALID", 400)
        }
        
        static DockerException baseImageNotFound() {
            return new DockerException("pull access denied for nonexistent, repository does not exist", 404)
        }
        
        static DockerException buildContextTooLarge() {
            return new DockerException("build context exceeds maximum allowed size", 413)
        }
        
        static DockerException insufficientDiskSpace() {
            return new DockerException("no space left on device", 500)
        }
        
        static DockerServiceException buildFailed(String details = "Build failed") {
            return new DockerServiceException(
                DockerServiceException.ErrorType.BUILD_FAILED,
                "Docker build failed: $details",
                "Check Dockerfile syntax and build context"
            )
        }
    }
    
    /**
     * Registry and authentication errors
     */
    static class Registry {
        
        static DockerException authenticationFailed() {
            return new DockerException("authentication required", 401)
        }
        
        static DockerException authorizationDenied() {
            return new DockerException("requested access to the resource is denied", 403)
        }
        
        static DockerException registryUnavailable() {
            return new DockerException("registry unreachable", 503)
        }
        
        static DockerException rateLimitExceeded() {
            return new DockerException("rate limit exceeded", 429)
        }
        
        static NotFoundException imageNotFound(String imageRef = "nonexistent:latest") {
            return new NotFoundException("No such image: $imageRef")
        }
        
        static DockerServiceException pushFailed(String details = "Push failed") {
            return new DockerServiceException(
                DockerServiceException.ErrorType.PUSH_FAILED,
                "Image push failed: $details",
                "Check registry accessibility and credentials"
            )
        }
        
        static DockerServiceException pullFailed(String details = "Pull failed") {
            return new DockerServiceException(
                DockerServiceException.ErrorType.PULL_FAILED,
                "Image pull failed: $details",
                "Check image reference and registry accessibility"
            )
        }
    }
    
    /**
     * Tag operation errors
     */
    static class Tag {
        
        static DockerException invalidTagFormat() {
            return new DockerException("invalid tag format", 400)
        }
        
        static DockerException sourceImageNotFound(String imageId = "sha256:nonexistent") {
            return new DockerException("No such image: $imageId", 404)
        }
        
        static DockerServiceException tagFailed(String details = "Tag operation failed") {
            return new DockerServiceException(
                DockerServiceException.ErrorType.TAG_FAILED,
                "Tag operation failed: $details",
                "Verify source image exists and tag format is correct"
            )
        }
    }
    
    /**
     * Save operation errors
     */
    static class Save {
        
        static DockerException imageNotFoundForSave(String imageId = "sha256:nonexistent") {
            return new DockerException("No such image: $imageId", 404)
        }
        
        static DockerException savePermissionDenied() {
            return new DockerException("permission denied", 403)
        }
        
        static DockerException saveInsufficientSpace() {
            return new DockerException("no space left on device", 500)
        }
        
        static DockerServiceException saveFailed(String details = "Save operation failed") {
            return new DockerServiceException(
                DockerServiceException.ErrorType.SAVE_FAILED,
                "Image save failed: $details",
                "Check output directory permissions and disk space"
            )
        }
    }
    
    /**
     * Compose service errors
     */
    static class Compose {
        
        static ComposeServiceException composeUnavailable() {
            return new ComposeServiceException(
                ComposeServiceException.ErrorType.COMPOSE_UNAVAILABLE,
                "Docker Compose is not available",
                "Install Docker Compose or Docker Desktop"
            )
        }
        
        static ComposeServiceException invalidComposeFile() {
            return new ComposeServiceException(
                ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                "Invalid compose file format",
                "Check YAML syntax and compose schema version"
            )
        }
        
        static ComposeServiceException serviceStartFailed(String serviceName = "web") {
            return new ComposeServiceException(
                ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                "Failed to start service: $serviceName",
                "Check service configuration and dependencies"
            )
        }
        
        static ComposeServiceException serviceStopFailed(String serviceName = "web") {
            return new ComposeServiceException(
                ComposeServiceException.ErrorType.SERVICE_STOP_FAILED,
                "Failed to stop service: $serviceName",
                "Check if service is running and accessible"
            )
        }
        
        static ComposeServiceException serviceTimeout(String serviceName = "web") {
            return new ComposeServiceException(
                ComposeServiceException.ErrorType.SERVICE_TIMEOUT,
                "Timeout waiting for service: $serviceName",
                "Increase timeout or check service health configuration"
            )
        }
        
        static ComposeServiceException networkConflict() {
            return new ComposeServiceException(
                ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                "Network conflict: address already in use",
                "Check for port conflicts or use different ports"
            )
        }
        
        static ComposeServiceException volumePermissionError() {
            return new ComposeServiceException(
                ComposeServiceException.ErrorType.SERVICE_START_FAILED,
                "Volume mount failed: permission denied",
                "Check volume permissions and ownership"
            )
        }
        
        static ComposeServiceException logsCaptureError() {
            return new ComposeServiceException(
                ComposeServiceException.ErrorType.LOGS_CAPTURE_FAILED,
                "Failed to capture logs",
                "Check if services are running and project exists"
            )
        }
    }
    
    /**
     * Network-related errors
     */
    static class Network {
        
        static DockerException networkTimeout() {
            return new DockerException("network timeout", 408)
        }
        
        static DockerException connectionRefused() {
            return new DockerException("connection refused", 500)
        }
        
        static DockerException dnsResolutionFailed() {
            return new DockerException("DNS resolution failed", 500)
        }
        
        static DockerException sslHandshakeFailed() {
            return new DockerException("SSL handshake failed", 500)
        }
    }
    
    /**
     * Resource constraint errors
     */
    static class Resources {
        
        static DockerException outOfMemory() {
            return new DockerException("container killed due to memory limit", 500)
        }
        
        static DockerException diskSpaceExceeded() {
            return new DockerException("no space left on device", 500)
        }
        
        static DockerException tooManyOpenFiles() {
            return new DockerException("too many open files", 500)
        }
        
        static DockerException cpuThrottled() {
            return new DockerException("CPU usage limit exceeded", 500)
        }
    }
    
    /**
     * Platform-specific errors
     */
    static class Platform {
        
        static DockerException unsupportedArchitecture() {
            return new DockerException("image architecture not supported", 400)
        }
        
        static DockerException windowsContainerError() {
            return new DockerException("Windows containers not supported on this platform", 400)
        }
        
        static DockerException selinuxViolation() {
            return new DockerException("SELinux policy violation", 403)
        }
    }
    
    /**
     * Generate a random error from a category
     */
    static Exception randomConnectionError() {
        def errors = [
            Connection.daemonUnavailable(),
            Connection.connectionTimeout(),
            Connection.permissionDenied()
        ]
        return errors[new Random().nextInt(errors.size())]
    }
    
    static Exception randomBuildError() {
        def errors = [
            Build.dockerfileNotFound(),
            Build.dockerfileSyntaxError(),
            Build.baseImageNotFound(),
            Build.insufficientDiskSpace()
        ]
        return errors[new Random().nextInt(errors.size())]
    }
    
    static Exception randomRegistryError() {
        def errors = [
            Registry.authenticationFailed(),
            Registry.authorizationDenied(),
            Registry.registryUnavailable(),
            Registry.rateLimitExceeded()
        ]
        return errors[new Random().nextInt(errors.size())]
    }
    
    static Exception randomComposeError() {
        def errors = [
            Compose.composeUnavailable(),
            Compose.serviceStartFailed(),
            Compose.serviceTimeout(),
            Compose.networkConflict()
        ]
        return errors[new Random().nextInt(errors.size())]
    }
}