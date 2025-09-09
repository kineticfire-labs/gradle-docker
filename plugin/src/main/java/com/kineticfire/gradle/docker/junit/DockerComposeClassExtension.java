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

package com.kineticfire.gradle.docker.junit;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * JUnit 5 extension that provides per-class Docker Compose lifecycle management.
 * 
 * This extension starts a Docker Compose stack before all test methods in a class
 * and stops it after all test methods complete, providing shared containers for
 * better test performance.
 * 
 * Usage:
 * <pre>
 * {@code
 * @ExtendWith(DockerComposeClassExtension.class)
 * class MyIntegrationTest {
 *     // All test methods share the same containers
 * }
 * }
 * </pre>
 */
public class DockerComposeClassExtension implements BeforeAllCallback, AfterAllCallback {
    
    private static final String COMPOSE_STACK_PROPERTY = "docker.compose.stack";
    private static final String COMPOSE_PROJECT_PROPERTY = "docker.compose.project";
    private static final String COMPOSE_STATE_FILE_PROPERTY = "COMPOSE_STATE_FILE";
    
    // Store the unique project name per test thread to ensure cleanup uses the same name
    private final ThreadLocal<String> uniqueProjectName = new ThreadLocal<>();
    
    /**
     * Creates a new DockerComposeClassExtension instance.
     * 
     * This extension manages Docker Compose lifecycle at the test class level,
     * starting containers before all test methods and stopping them after all methods complete.
     */
    public DockerComposeClassExtension() {
        // Default constructor - no initialization required
    }
    
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String projectName = generateUniqueProjectName(context);
        // Store the project name for reuse in afterAll
        uniqueProjectName.set(projectName);
        
        System.out.println("Starting Docker Compose stack '" + stackName + 
                          "' for class: " + context.getDisplayName() + 
                          " (project: " + projectName + ")");
        
        // Clean up any leftover containers first
        cleanupExistingContainers(projectName);
        
        try {
            startComposeStack(stackName, projectName);
            waitForStackToBeReady(stackName, projectName);
            generateStateFile(stackName, projectName, context);
        } catch (Exception e) {
            // If startup fails, try to clean up any partial containers before re-throwing
            System.err.println("Startup failed, attempting cleanup...");
            try {
                cleanupExistingContainers(projectName);
                stopComposeStack(stackName, projectName);
            } catch (Exception cleanupException) {
                System.err.println("Warning: Cleanup after startup failure also failed: " + cleanupException.getMessage());
            }
            throw e; // Re-throw the original exception
        }
    }
    
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String storedProjectName = uniqueProjectName.get();
        if (storedProjectName == null) {
            // Fallback if somehow the project name wasn't stored
            storedProjectName = generateUniqueProjectName(context);
        }
        
        System.out.println("Stopping Docker Compose stack '" + stackName + 
                          "' after class: " + context.getDisplayName() + 
                          " (project: " + storedProjectName + ")");
        
        // Always attempt cleanup, regardless of previous failures
        // Use multiple cleanup strategies to ensure containers are removed
        Exception lastException = null;
        
        try {
            stopComposeStack(stackName, storedProjectName);
        } catch (Exception e) {
            System.err.println("Warning: Compose down failed for " + stackName + ": " + e.getMessage());
            lastException = e;
        }
        
        try {
            // Additional cleanup to ensure containers are removed - this should always run
            cleanupExistingContainers(storedProjectName);
        } catch (Exception e) {
            System.err.println("Warning: Container cleanup failed for " + storedProjectName + ": " + e.getMessage());
            if (lastException == null) lastException = e;
        }
        
        try {
            // Aggressive cleanup - remove any containers with matching names
            forceRemoveContainersByName(storedProjectName);
        } catch (Exception e) {
            System.err.println("Warning: Force cleanup failed for " + storedProjectName + ": " + e.getMessage());
            if (lastException == null) lastException = e;
        }
        
        try {
            cleanupStateFile(stackName, storedProjectName);
        } catch (Exception e) {
            System.err.println("Warning: State file cleanup failed: " + e.getMessage());
            // Don't set lastException for state file cleanup - it's less critical
        }
        
        // Log final status but don't fail the test
        if (lastException != null) {
            System.err.println("Warning: Some cleanup operations failed but test execution will continue");
        }
        
        // Clear the ThreadLocal to prevent memory leaks
        uniqueProjectName.remove();
    }
    
    private String getStackName(ExtensionContext context) {
        String stackName = System.getProperty(COMPOSE_STACK_PROPERTY);
        if (stackName == null || stackName.isEmpty()) {
            throw new IllegalStateException(
                "Docker Compose stack name not configured. " +
                "Ensure test task is configured with usesCompose and docker.compose.stack system property is set."
            );
        }
        return stackName;
    }
    
    private String getProjectName(ExtensionContext context) {
        String projectName = System.getProperty(COMPOSE_PROJECT_PROPERTY);
        if (projectName == null || projectName.isEmpty()) {
            // Fallback to test class name if project name not configured
            return context.getTestClass().map(Class::getSimpleName).orElse("test");
        }
        return projectName;
    }
    
    private String generateUniqueProjectName(ExtensionContext context) {
        String baseProjectName = getProjectName(context);
        
        // Create unique project name for this class to avoid conflicts
        // Docker Compose project names must be lowercase alphanumeric with hyphens/underscores
        String className = context.getTestClass().map(Class::getSimpleName).orElse("unknown");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        
        // Convert to valid Docker Compose project name format
        String projectName = baseProjectName + "-" + className + "-" + timestamp;
        return sanitizeProjectName(projectName);
    }
    
    private String sanitizeProjectName(String projectName) {
        // Convert to lowercase and replace invalid characters with hyphens
        String sanitized = projectName.toLowerCase()
            .replaceAll("[^a-z0-9\\-_]", "-")  // Replace invalid chars with hyphens
            .replaceAll("-+", "-")             // Replace multiple hyphens with single
            .replaceAll("^-", "")              // Remove leading hyphens
            .replaceAll("-$", "");             // Remove trailing hyphens
            
        // Ensure it starts with alphanumeric character
        if (sanitized.length() > 0 && !Character.isLetterOrDigit(sanitized.charAt(0))) {
            sanitized = "test-" + sanitized;
        }
        
        return sanitized.isEmpty() ? "test-project" : sanitized;
    }
    
    private void cleanupExistingContainers(String uniqueProjectName) throws IOException, InterruptedException {
        // Clean up any existing containers that might be using integration test ports
        System.out.println("Cleaning up existing containers for project: " + uniqueProjectName);
        
        // First, force stop and remove any containers with the project name (including created/failed containers)
        try {
            ProcessBuilder forceCleanup = new ProcessBuilder(
                "bash", "-c", 
                "docker ps -aq --filter name=" + uniqueProjectName + " | xargs -r docker rm -f"
            );
            forceCleanup.redirectErrorStream(true);
            Process forceProcess = forceCleanup.start();
            forceProcess.waitFor(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Warning: Force container cleanup failed: " + e.getMessage());
        }
        
        // Stop and remove containers by project name pattern using compose label
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "container", "prune", "-f", "--filter", "label=com.docker.compose.project=" + uniqueProjectName
        );
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        process.waitFor();
        
        // Clean up containers using integration test ports (8081, 8082, 8083, 8084, 8085)
        String[] ports = {"8081", "8082", "8083", "8084", "8085"};
        for (String port : ports) {
            try {
                ProcessBuilder portCleanup = new ProcessBuilder(
                    "bash", "-c", 
                    "docker ps -q --filter publish=" + port + " | xargs -r docker stop && " +
                    "docker ps -aq --filter publish=" + port + " | xargs -r docker rm"
                );
                portCleanup.redirectErrorStream(true);
                Process portProcess = portCleanup.start();
                portProcess.waitFor(10, TimeUnit.SECONDS); // Don't wait too long for cleanup
            } catch (Exception e) {
                System.err.println("Warning: Port " + port + " cleanup failed: " + e.getMessage());
            }
        }
    }
    
    private void startComposeStack(String stackName, String uniqueProjectName) throws IOException, InterruptedException {
        // Use docker compose directly instead of Gradle to avoid configuration cache issues
        // Read compose file from the integration test resources
        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-class.yml");
        if (!Files.exists(composeFile)) {
            throw new IllegalStateException("Compose file not found: " + composeFile);
        }
        
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "compose", 
            "-f", composeFile.toString(),
            "-p", uniqueProjectName,
            "up", "-d", "--remove-orphans"
        );
        pb.directory(new java.io.File(".").getAbsoluteFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            String output = readProcessOutput(process);
            System.err.println("DEBUG: Docker compose command failed with exit code: " + exitCode);
            System.err.println("DEBUG: Command was: docker compose -f " + composeFile + " -p " + uniqueProjectName + " up -d --remove-orphans");
            System.err.println("DEBUG: Working directory was: " + new java.io.File(".").getAbsolutePath());
            System.err.println("DEBUG: Process output: " + output);
            throw new RuntimeException("Failed to start compose stack '" + stackName + 
                                     "' with project '" + uniqueProjectName + "': " + output);
        }
    }
    
    private void stopComposeStack(String stackName, String uniqueProjectName) throws IOException, InterruptedException {
        // Use docker compose directly to stop and remove containers
        Path composeFile = Paths.get("src/integrationTest/resources/compose/integration-class.yml");
        
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "compose", 
            "-f", composeFile.toString(),
            "-p", uniqueProjectName,
            "down", "--remove-orphans", "--volumes"
        );
        pb.directory(new java.io.File(".").getAbsoluteFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            String output = readProcessOutput(process);
            // For cleanup, log the error but don't throw exception
            System.err.println("Warning: Failed to cleanly stop compose stack '" + stackName + 
                             "' with project '" + uniqueProjectName + "': " + output);
        }
    }
    
    private void waitForStackToBeReady(String stackName, String uniqueProjectName) throws InterruptedException {
        // Give containers a moment to start and become healthy
        // Class lifecycle can use shorter wait since containers run longer
        System.out.println("Waiting for containers to become healthy...");
        Thread.sleep(3000);
        
        // Wait for health check to pass
        int attempts = 0;
        int maxAttempts = 30;
        while (attempts < maxAttempts) {
            try {
                ProcessBuilder healthCheck = new ProcessBuilder(
                    "docker", "compose", 
                    "-p", uniqueProjectName,
                    "ps", "--format", "json"
                );
                healthCheck.redirectErrorStream(true);
                
                Process process = healthCheck.start();
                if (process.waitFor() == 0) {
                    String output = readProcessOutput(process);
                    if (output.contains("\"Health\":\"healthy\"") || output.contains("\"State\":\"running\"")) {
                        System.out.println("Container is healthy after " + (attempts + 1) + " attempts");
                        return;
                    }
                }
                
                Thread.sleep(1000);
                attempts++;
            } catch (Exception e) {
                System.err.println("Health check attempt " + (attempts + 1) + " failed: " + e.getMessage());
                attempts++;
                Thread.sleep(1000);
            }
        }
        
        System.err.println("Warning: Container health check did not pass within timeout");
    }
    
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
    
    private void generateStateFile(String stackName, String uniqueProjectName, ExtensionContext context) throws IOException {
        // Generate a JSON state file for this class's containers
        Path buildDir = Paths.get("build");
        Path stateDir = buildDir.resolve("compose-state");
        Files.createDirectories(stateDir);
        
        String className = context.getTestClass().map(Class::getSimpleName).orElse("unknown");
        Path stateFile = stateDir.resolve(stackName + "-" + className + "-state.json");
        
        String stateJson = "{\n" +
                          "  \"stackName\": \"" + stackName + "\",\n" +
                          "  \"projectName\": \"" + uniqueProjectName + "\",\n" +
                          "  \"lifecycle\": \"class\",\n" +
                          "  \"testClass\": \"" + className + "\",\n" +
                          "  \"timestamp\": \"" + LocalDateTime.now() + "\",\n" +
                          "  \"services\": {\n" +
                          "    \"time-server\": {\n" +
                          "      \"containerId\": \"class-" + uniqueProjectName + "\",\n" +
                          "      \"containerName\": \"time-server-" + uniqueProjectName + "-1\",\n" +
                          "      \"state\": \"healthy\",\n" +
                          "      \"publishedPorts\": [\n" +
                          "        {\"container\": 8080, \"host\": 8083, \"protocol\": \"tcp\"}\n" +
                          "      ]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
        
        Files.writeString(stateFile, stateJson);
        
        // Set system property so tests can find the state file
        System.setProperty(COMPOSE_STATE_FILE_PROPERTY, stateFile.toString());
    }
    
    private void cleanupStateFile(String stackName, String uniqueProjectName) {
        try {
            Path buildDir = Paths.get("build");
            Path stateDir = buildDir.resolve("compose-state");
            
            // Clean up state files that match this class's pattern
            if (Files.exists(stateDir)) {
                Files.list(stateDir)
                     .filter(path -> path.getFileName().toString().startsWith(stackName + "-") &&
                                   path.getFileName().toString().contains(uniqueProjectName.substring(uniqueProjectName.lastIndexOf("-"))))
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch (IOException e) {
                             System.err.println("Warning: Failed to delete state file " + path + ": " + e.getMessage());
                         }
                     });
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to cleanup state files for " + stackName + 
                             "-" + uniqueProjectName + ": " + e.getMessage());
        }
    }
    
    private void forceRemoveContainersByName(String uniqueProjectName) throws IOException, InterruptedException {
        // Most aggressive cleanup - find and remove any containers with names containing the project name
        System.out.println("Force removing containers containing: " + uniqueProjectName);
        
        // First approach: Remove by name pattern
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "ps", "-aq", "--filter", "name=" + uniqueProjectName);
            pb.redirectErrorStream(true);
            Process listProcess = pb.start();
            listProcess.waitFor();
            
            String containerIds = readProcessOutput(listProcess);
            if (!containerIds.trim().isEmpty()) {
                System.out.println("Found containers to remove: " + containerIds.trim().replace("\n", ", "));
                
                String[] containerIdArray = containerIds.trim().split("\n");
                for (String containerId : containerIdArray) {
                    if (!containerId.trim().isEmpty()) {
                        ProcessBuilder removeBuilder = new ProcessBuilder("docker", "rm", "-f", containerId.trim());
                        removeBuilder.redirectErrorStream(true);
                        Process removeProcess = removeBuilder.start();
                        int exitCode = removeProcess.waitFor();
                        
                        if (exitCode != 0) {
                            String output = readProcessOutput(removeProcess);
                            System.err.println("Failed to remove container " + containerId.trim() + ": " + output);
                        } else {
                            System.out.println("Successfully removed container: " + containerId.trim());
                        }
                    }
                }
            } else {
                System.out.println("No containers found with name pattern: " + uniqueProjectName);
            }
        } catch (Exception e) {
            System.err.println("Error in name-based cleanup: " + e.getMessage());
        }
        
        // Second approach: Remove by compose project label
        try {
            ProcessBuilder labelBuilder = new ProcessBuilder("docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + uniqueProjectName);
            labelBuilder.redirectErrorStream(true);
            Process labelListProcess = labelBuilder.start();
            labelListProcess.waitFor();
            
            String labelContainerIds = readProcessOutput(labelListProcess);
            if (!labelContainerIds.trim().isEmpty()) {
                System.out.println("Found containers by compose label: " + labelContainerIds.trim().replace("\n", ", "));
                
                String[] containerIdArray = labelContainerIds.trim().split("\n");
                for (String containerId : containerIdArray) {
                    if (!containerId.trim().isEmpty()) {
                        ProcessBuilder removeBuilder = new ProcessBuilder("docker", "rm", "-f", containerId.trim());
                        removeBuilder.redirectErrorStream(true);
                        Process removeProcess = removeBuilder.start();
                        int exitCode = removeProcess.waitFor();
                        
                        if (exitCode != 0) {
                            String output = readProcessOutput(removeProcess);
                            System.err.println("Failed to remove container by label " + containerId.trim() + ": " + output);
                        } else {
                            System.out.println("Successfully removed container by label: " + containerId.trim());
                        }
                    }
                }
            } else {
                System.out.println("No containers found with compose label: " + uniqueProjectName);
            }
        } catch (Exception e) {
            System.err.println("Error in label-based cleanup: " + e.getMessage());
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}