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
    
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String projectName = getProjectName(context);
        
        System.out.println("Starting Docker Compose stack '" + stackName + "' for class: " + 
                          context.getDisplayName());
        
        startComposeStack(stackName, projectName);
        waitForStackToBeReady(stackName, projectName);
    }
    
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String projectName = getProjectName(context);
        
        System.out.println("Stopping Docker Compose stack '" + stackName + "' after class: " + 
                          context.getDisplayName());
        
        stopComposeStack(stackName, projectName);
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
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}