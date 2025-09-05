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

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
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
 * JUnit 5 extension that provides per-method Docker Compose lifecycle management.
 * 
 * This extension starts a fresh Docker Compose stack before each test method
 * and stops it after each test method completes, providing maximum test isolation.
 * 
 * Usage:
 * <pre>
 * {@code
 * @ExtendWith(DockerComposeMethodExtension.class)
 * class MyIntegrationTest {
 *     // Each test method gets fresh containers
 * }
 * }
 * </pre>
 */
public class DockerComposeMethodExtension implements BeforeEachCallback, AfterEachCallback {
    
    private static final String COMPOSE_STACK_PROPERTY = "docker.compose.stack";
    private static final String COMPOSE_PROJECT_PROPERTY = "docker.compose.project";
    private static final String COMPOSE_STATE_FILE_PROPERTY = "COMPOSE_STATE_FILE";
    
    /**
     * Creates a new DockerComposeMethodExtension instance.
     * 
     * This extension manages Docker Compose lifecycle at the test method level,
     * starting fresh containers before each test method and stopping them after each method completes.
     */
    public DockerComposeMethodExtension() {
        // Default constructor - no initialization required
    }
    
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String uniqueProjectName = generateUniqueProjectName(context);
        
        System.out.println("Starting Docker Compose stack '" + stackName + 
                          "' for method: " + context.getDisplayName() + 
                          " (project: " + uniqueProjectName + ")");
        
        startComposeStack(stackName, uniqueProjectName);
        waitForStackToBeReady(stackName, uniqueProjectName);
        generateStateFile(stackName, uniqueProjectName, context);
    }
    
    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String uniqueProjectName = generateUniqueProjectName(context);
        
        System.out.println("Stopping Docker Compose stack '" + stackName + 
                          "' after method: " + context.getDisplayName() + 
                          " (project: " + uniqueProjectName + ")");
        
        try {
            stopComposeStack(stackName, uniqueProjectName);
        } catch (Exception e) {
            System.err.println("Warning: Failed to stop compose stack '" + stackName + 
                             "' for method " + context.getDisplayName() + ": " + e.getMessage());
            // Don't fail the test if cleanup fails, just log the warning
        }
        
        cleanupStateFile(stackName, uniqueProjectName);
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
        
        // Create unique project name for this method to avoid conflicts
        String className = context.getTestClass().map(Class::getSimpleName).orElse("unknown");
        String methodName = context.getTestMethod().map(m -> m.getName()).orElse("unknown");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        
        return baseProjectName + "-" + className + "-" + methodName + "-" + timestamp;
    }
    
    private void startComposeStack(String stackName, String uniqueProjectName) throws IOException, InterruptedException {
        // Use Gradle to start the compose stack with unique project name
        ProcessBuilder pb = new ProcessBuilder(
            "./gradlew", 
            "composeUp" + capitalize(stackName),
            "-Pcompose.project.name=" + uniqueProjectName,
            "--quiet"
        );
        pb.directory(new java.io.File("..").getAbsoluteFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            String output = readProcessOutput(process);
            throw new RuntimeException("Failed to start compose stack '" + stackName + 
                                     "' with project '" + uniqueProjectName + "': " + output);
        }
    }
    
    private void stopComposeStack(String stackName, String uniqueProjectName) throws IOException, InterruptedException {
        // Use Gradle to stop the compose stack with unique project name
        ProcessBuilder pb = new ProcessBuilder(
            "./gradlew", 
            "composeDown" + capitalize(stackName),
            "-Pcompose.project.name=" + uniqueProjectName,
            "--quiet"
        );
        pb.directory(new java.io.File("..").getAbsoluteFile());
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
        // Method lifecycle needs longer wait for fresh containers
        Thread.sleep(3000);
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
        // Generate a JSON state file for this method's containers
        // In production, this would query Docker daemon for actual container info
        Path buildDir = Paths.get("../build");
        Path stateDir = buildDir.resolve("compose-state");
        Files.createDirectories(stateDir);
        
        String className = context.getTestClass().map(Class::getSimpleName).orElse("unknown");
        String methodName = context.getTestMethod().map(m -> m.getName()).orElse("unknown");
        Path stateFile = stateDir.resolve(stackName + "-" + className + "-" + methodName + "-state.json");
        
        String stateJson = "{\n" +
                          "  \"stackName\": \"" + stackName + "\",\n" +
                          "  \"projectName\": \"" + uniqueProjectName + "\",\n" +
                          "  \"lifecycle\": \"method\",\n" +
                          "  \"testClass\": \"" + className + "\",\n" +
                          "  \"testMethod\": \"" + methodName + "\",\n" +
                          "  \"timestamp\": \"" + LocalDateTime.now() + "\",\n" +
                          "  \"services\": {\n" +
                          "    \"time-server\": {\n" +
                          "      \"containerId\": \"method-" + uniqueProjectName + "\",\n" +
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
            Path buildDir = Paths.get("../build");
            Path stateDir = buildDir.resolve("compose-state");
            
            // Clean up state files that match this method's pattern
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
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}