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

package com.kineticfire.gradle.docker.integration.appimage;

import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Docker operations using external Docker CLI verification.
 * 
 * Purpose: Test Docker operations by verifying the results through Docker CLI commands.
 * This indirectly tests the plugin's Docker service operations and improves coverage
 * of Docker image building, tagging, and management functionality.
 * 
 * Tests: Image existence, image properties, container operations through CLI verification
 * Coverage: Validates DockerServiceImpl operations through external verification
 */
class DockerOperationsIntegrationIT {
    
    private static final String TEST_IMAGE_NAME = "time-server";
    private static final String TEST_IMAGE_VERSION = "1.0.0";
    private static final String TEST_IMAGE_TAG = TEST_IMAGE_NAME + ":" + TEST_IMAGE_VERSION;
    
    @Test
    @DisplayName("Docker image is built with correct tags")
    void dockerImageIsBuiltWithCorrectTags() throws Exception {
        // Purpose: Verify that the plugin correctly built the Docker image with expected tags
        
        // Check if the image exists with the expected tag
        ProcessBuilder pb = new ProcessBuilder("docker", "images", TEST_IMAGE_NAME, "--format", "{{.Repository}}:{{.Tag}}");
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted)
            .as("Docker images command should complete")
            .isTrue();
        
        String output = readProcessOutput(process);
        
        assertThat(output)
            .as("Built image should be present with correct tag")
            .contains(TEST_IMAGE_TAG)
            .contains(TEST_IMAGE_NAME + ":latest");
    }
    
    @Test
    @DisplayName("Docker image has correct build-time labels")
    void dockerImageHasCorrectBuildTimeLabels() throws Exception {
        // Purpose: Verify that build arguments and labels are correctly applied
        
        ProcessBuilder pb = new ProcessBuilder("docker", "inspect", TEST_IMAGE_TAG, "--format", "{{.Config.Labels}}");
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted).isTrue();
        
        String output = readProcessOutput(process);
        
        assertThat(output)
            .as("Image should have version label")
            .contains("version:" + TEST_IMAGE_VERSION);
            
        assertThat(output)
            .as("Image should have build-time label")
            .contains("build-time:");
    }
    
    @Test  
    @DisplayName("Docker image can be run successfully")
    void dockerImageCanBeRunSuccessfully() throws Exception {
        // Purpose: Test that the built image is functional
        
        // Start a container from the image
        String containerName = "test-time-server-" + System.currentTimeMillis();
        
        try {
            ProcessBuilder startPb = new ProcessBuilder(
                "docker", "run", "-d", 
                "--name", containerName,
                "-p", "9999:8080", // Use different port to avoid conflicts
                TEST_IMAGE_TAG
            );
            Process startProcess = startPb.start();
            
            boolean startCompleted = startProcess.waitFor(30, TimeUnit.SECONDS);
            assertThat(startCompleted).isTrue();
            assertThat(startProcess.exitValue()).isEqualTo(0);
            
            // Wait for container to be ready
            Thread.sleep(5000);
            
            // Check container status
            ProcessBuilder statusPb = new ProcessBuilder(
                "docker", "ps", "--filter", "name=" + containerName, 
                "--format", "{{.Status}}"
            );
            Process statusProcess = statusPb.start();
            statusProcess.waitFor(10, TimeUnit.SECONDS);
            
            String statusOutput = readProcessOutput(statusProcess);
            assertThat(statusOutput)
                .as("Container should be running")
                .containsIgnoringCase("up");
                
        } finally {
            // Cleanup: Stop and remove the container
            cleanupContainer(containerName);
        }
    }
    
    @Test
    @DisplayName("Docker image has expected entry point configuration")
    void dockerImageHasExpectedEntryPointConfiguration() throws Exception {
        // Purpose: Verify image configuration is correct
        
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "inspect", TEST_IMAGE_TAG,
            "--format", "{{.Config.Entrypoint}}"
        );
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted).isTrue();
        
        String output = readProcessOutput(process);
        
        assertThat(output)
            .as("Image should have Java entrypoint")
            .contains("java")
            .contains("-jar");
    }
    
    @Test
    @DisplayName("Docker image has correct working directory")
    void dockerImageHasCorrectWorkingDirectory() throws Exception {
        // Purpose: Verify Dockerfile WORKDIR instruction was applied
        
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "inspect", TEST_IMAGE_TAG,
            "--format", "{{.Config.WorkingDir}}"
        );
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted).isTrue();
        
        String output = readProcessOutput(process).trim();
        
        assertThat(output)
            .as("Image should have correct working directory")
            .isEqualTo("/app");
    }
    
    @Test
    @DisplayName("Docker image has expected exposed ports")
    void dockerImageHasExpectedExposedPorts() throws Exception {
        // Purpose: Verify port configuration
        
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "inspect", TEST_IMAGE_TAG,
            "--format", "{{.Config.ExposedPorts}}"
        );
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted).isTrue();
        
        String output = readProcessOutput(process);
        
        assertThat(output)
            .as("Image should expose port 8080")
            .contains("8080/tcp");
    }
    
    @Test
    @DisplayName("Docker image size is reasonable")
    void dockerImageSizeIsReasonable() throws Exception {
        // Purpose: Verify image build produces reasonable size (not bloated)
        
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "images", TEST_IMAGE_TAG,
            "--format", "{{.Size}}"
        );
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted).isTrue();
        
        String sizeOutput = readProcessOutput(process).trim();
        
        // Size should be present and not empty
        assertThat(sizeOutput)
            .as("Image should have reportable size")
            .isNotEmpty()
            .doesNotContain("0B"); // Should not be zero bytes
        
        // Basic sanity check - shouldn't be unreasonably large
        assertThat(sizeOutput)
            .as("Image size should be reasonable (not multi-GB)")
            .doesNotContain("GB"); // Shouldn't be multiple gigabytes
    }
    
    @Test
    @DisplayName("Docker image layers are optimized")
    void dockerImageLayersAreOptimized() throws Exception {
        // Purpose: Verify image has reasonable layer count (build optimization)
        
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "history", TEST_IMAGE_TAG,
            "--format", "{{.ID}}"
        );
        Process process = pb.start();
        
        boolean processCompleted = process.waitFor(30, TimeUnit.SECONDS);
        assertThat(processCompleted).isTrue();
        
        String[] layers = readProcessOutput(process).trim().split("\n");
        
        assertThat(layers.length)
            .as("Image should have reasonable number of layers")
            .isGreaterThan(0)
            .isLessThan(50); // Shouldn't have excessive layers
    }
    
    @Test
    @DisplayName("Docker container health check works correctly")
    void dockerContainerHealthCheckWorksCorrectly() throws Exception {
        // Purpose: Test health check functionality of the container
        
        String containerName = "test-health-" + System.currentTimeMillis();
        
        try {
            // Start container
            ProcessBuilder startPb = new ProcessBuilder(
                "docker", "run", "-d",
                "--name", containerName,
                "-p", "9998:8080", // Different port
                TEST_IMAGE_TAG
            );
            Process startProcess = startPb.start();
            startProcess.waitFor(30, TimeUnit.SECONDS);
            assertThat(startProcess.exitValue()).isEqualTo(0);
            
            // Wait for health checks to run
            Thread.sleep(15000); // Health check start_period is 10s + some buffer
            
            // Check health status
            ProcessBuilder healthPb = new ProcessBuilder(
                "docker", "inspect", containerName,
                "--format", "{{.State.Health.Status}}"
            );
            Process healthProcess = healthPb.start();
            healthProcess.waitFor(10, TimeUnit.SECONDS);
            
            String healthStatus = readProcessOutput(healthProcess).trim();
            
            // Health status should be healthy or starting (depending on timing)
            assertThat(healthStatus)
                .as("Container should have health status")
                .isIn("healthy", "starting");
                
        } finally {
            cleanupContainer(containerName);
        }
    }
    
    // Helper Methods
    
    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
    
    private void cleanupContainer(String containerName) {
        try {
            // Stop container
            ProcessBuilder stopPb = new ProcessBuilder("docker", "stop", containerName);
            Process stopProcess = stopPb.start();
            stopProcess.waitFor(10, TimeUnit.SECONDS);
            
            // Remove container
            ProcessBuilder removePb = new ProcessBuilder("docker", "rm", containerName);
            Process removeProcess = removePb.start();
            removeProcess.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore cleanup errors
            System.err.println("Warning: Failed to cleanup container " + containerName + ": " + e.getMessage());
        }
    }
}