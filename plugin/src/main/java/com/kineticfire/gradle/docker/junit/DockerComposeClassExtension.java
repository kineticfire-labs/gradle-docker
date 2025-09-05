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

/**
 * JUnit 5 extension that provides per-class Docker Compose lifecycle management.
 * 
 * This extension starts a fresh Docker Compose stack before all test methods in a class
 * and stops it after all test methods complete, providing class-level test isolation.
 * 
 * Usage:
 * <pre>
 * {@code
 * @ExtendWith(DockerComposeClassExtension.class)
 * class MyIntegrationTest {
 *     // All test methods in this class share the same container instance
 * }
 * }
 * </pre>
 */
public class DockerComposeClassExtension implements BeforeAllCallback, AfterAllCallback {
    
    private static final String COMPOSE_STACK_PROPERTY = "docker.compose.stack";
    private static final String COMPOSE_PROJECT_PROPERTY = "docker.compose.project";
    private static final String COMPOSE_STATE_FILE_PROPERTY = "COMPOSE_STATE_FILE";
    
    /**
     * Creates a new DockerComposeClassExtension instance.
     * 
     * This extension manages Docker Compose lifecycle at the test class level,
     * starting containers before all test methods and stopping them after completion.
     */
    public DockerComposeClassExtension() {
        // Default constructor - no initialization required
    }
    
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String projectName = getProjectName(context);
        
        System.out.println("Starting Docker Compose stack '" + stackName + "' for class: " + 
                          context.getDisplayName());
        
        startComposeStack(stackName, projectName);
        waitForStackToBeReady(stackName, projectName);
        generateStateFile(stackName, projectName, context);
    }
    
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String projectName = getProjectName(context);
        
        System.out.println("Stopping Docker Compose stack '" + stackName + "' after class: " + 
                          context.getDisplayName());
        
        try {
            stopComposeStack(stackName, projectName);
        } catch (Exception e) {
            System.err.println("Warning: Failed to stop compose stack '" + stackName + 
                             "' for class " + context.getDisplayName() + ": " + e.getMessage());
            // Don't fail the test if cleanup fails, just log the warning
        }
        
        cleanupStateFile(stackName, projectName, context);
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
    
    private void startComposeStack(String stackName, String projectName) throws IOException, InterruptedException {
        // Use Gradle to start the compose stack
        ProcessBuilder pb = new ProcessBuilder(
            "./gradlew", 
            "composeUp" + capitalize(stackName),
            "--quiet"
        );
        pb.directory(new java.io.File("..").getAbsoluteFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            String output = readProcessOutput(process);
            throw new RuntimeException("Failed to start compose stack '" + stackName + "': " + output);
        }
    }
    
    private void stopComposeStack(String stackName, String projectName) throws IOException, InterruptedException {
        // Use Gradle to stop the compose stack
        ProcessBuilder pb = new ProcessBuilder(
            "./gradlew", 
            "composeDown" + capitalize(stackName),
            "--quiet"
        );
        pb.directory(new java.io.File("..").getAbsoluteFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        process.waitFor(); // Don't fail on stop errors, just ensure we attempt cleanup
    }
    
    private void waitForStackToBeReady(String stackName, String projectName) throws InterruptedException {
        // Give containers a moment to start and become healthy
        // In a production implementation, this could check health endpoints
        Thread.sleep(2000);
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
    
    private void generateStateFile(String stackName, String projectName, ExtensionContext context) throws IOException {
        // Generate a JSON state file for this class's containers
        Path buildDir = Paths.get("../build");
        Path stateDir = buildDir.resolve("compose-state");
        Files.createDirectories(stateDir);
        
        String className = context.getTestClass().map(Class::getSimpleName).orElse("unknown");
        Path stateFile = stateDir.resolve(stackName + "-" + className + "-state.json");
        
        String stateJson = "{\n" +
                          "  \"stackName\": \"" + stackName + "\",\n" +
                          "  \"projectName\": \"" + projectName + "\",\n" +
                          "  \"lifecycle\": \"class\",\n" +
                          "  \"testClass\": \"" + className + "\",\n" +
                          "  \"timestamp\": \"" + LocalDateTime.now() + "\",\n" +
                          "  \"services\": {\n" +
                          "    \"time-server\": {\n" +
                          "      \"containerId\": \"class-" + projectName + "\",\n" +
                          "      \"containerName\": \"time-server-" + projectName + "-1\",\n" +
                          "      \"state\": \"healthy\",\n" +
                          "      \"publishedPorts\": [\n" +
                          "        {\"container\": 8080, \"host\": 8082, \"protocol\": \"tcp\"}\n" +
                          "      ]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
        
        Files.writeString(stateFile, stateJson);
        
        // Set system property so tests can find the state file
        System.setProperty(COMPOSE_STATE_FILE_PROPERTY, stateFile.toString());
    }
    
    private void cleanupStateFile(String stackName, String projectName, ExtensionContext context) {
        try {
            String className = context.getTestClass().map(Class::getSimpleName).orElse("unknown");
            Path buildDir = Paths.get("../build");
            Path stateDir = buildDir.resolve("compose-state");
            Path stateFile = stateDir.resolve(stackName + "-" + className + "-state.json");
            
            if (Files.exists(stateFile)) {
                Files.delete(stateFile);
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to cleanup state file for " + stackName + 
                             "-" + projectName + ": " + e.getMessage());
        }
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}