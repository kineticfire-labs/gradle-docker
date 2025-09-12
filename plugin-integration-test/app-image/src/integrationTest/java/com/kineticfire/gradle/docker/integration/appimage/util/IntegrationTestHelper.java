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

package com.kineticfire.gradle.docker.integration.appimage.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class providing common functionality for integration tests.
 * 
 * Purpose: Centralize common Docker and Gradle operations used across integration tests
 * to reduce code duplication and provide consistent behavior. This includes Docker CLI
 * operations, Gradle task execution, container management, and test utilities.
 * 
 * Features:
 * - Docker image and container verification
 * - Gradle task execution helpers
 * - Container lifecycle management
 * - Registry interaction utilities
 * - Test resource cleanup
 * - Process output handling
 */
public class IntegrationTestHelper {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Docker Image Operations
    
    public static boolean verifyDockerImageExists(String imageTag) {
        try {
            Process process = new ProcessBuilder("docker", "images", imageTag, "--format", "{{.Repository}}:{{.Tag}}")
                .start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            if (process.exitValue() == 0) {
                String output = readProcessOutput(process);
                return output.trim().contains(imageTag);
            }
        } catch (Exception e) {
            System.err.println("Error verifying image existence: " + e.getMessage());
        }
        return false;
    }
    
    public static String inspectDockerImage(String imageTag) {
        try {
            Process process = new ProcessBuilder("docker", "inspect", imageTag)
                .start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            if (process.exitValue() == 0) {
                return readProcessOutput(process);
            }
        } catch (Exception e) {
            System.err.println("Error inspecting image: " + e.getMessage());
        }
        return "";
    }
    
    public static String getImageId(String imageTag) {
        try {
            Process process = new ProcessBuilder("docker", "inspect", imageTag, "--format", "{{.Id}}")
                .start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            if (process.exitValue() == 0) {
                return readProcessOutput(process).trim();
            }
        } catch (Exception e) {
            System.err.println("Error getting image ID: " + e.getMessage());
        }
        return "";
    }
    
    public static String getImageWorkingDirectory(String imageTag) {
        try {
            Process process = new ProcessBuilder("docker", "inspect", imageTag, "--format", "{{.Config.WorkingDir}}")
                .start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            if (process.exitValue() == 0) {
                return readProcessOutput(process).trim();
            }
        } catch (Exception e) {
            System.err.println("Error getting working directory: " + e.getMessage());
        }
        return "";
    }
    
    // Container Operations
    
    public static List<String> getRunningContainersForProject(String projectName) {
        return getContainersForProject(projectName, false);
    }
    
    public static List<String> getAllContainersForProject(String projectName) {
        return getContainersForProject(projectName, true);
    }
    
    private static List<String> getContainersForProject(String projectName, boolean includeAll) {
        try {
            List<String> command = new ArrayList<>();
            command.add("docker");
            command.add("ps");
            if (includeAll) {
                command.add("-a");
            }
            command.add("--filter");
            command.add("label=com.docker.compose.project=" + projectName);
            command.add("--format");
            command.add("{{.Names}}");
            
            Process process = new ProcessBuilder(command).start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            if (process.exitValue() == 0) {
                String output = readProcessOutput(process);
                List<String> containers = new ArrayList<>();
                for (String line : output.split("\n")) {
                    String containerName = line.trim();
                    if (!containerName.isEmpty()) {
                        containers.add(containerName);
                    }
                }
                return containers;
            }
        } catch (Exception e) {
            System.err.println("Error getting containers for project: " + e.getMessage());
        }
        return new ArrayList<>();
    }
    
    public static String findContainerByService(List<String> containers, String serviceName) {
        return containers.stream()
            .filter(container -> container.contains(serviceName))
            .findFirst()
            .orElse(null);
    }
    
    public static String getContainerHealthStatus(String containerName) {
        try {
            Process process = new ProcessBuilder("docker", "inspect", containerName,
                "--format", "{{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}}")
                .start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            if (process.exitValue() == 0) {
                return readProcessOutput(process).trim();
            }
        } catch (Exception e) {
            System.err.println("Error getting container health status: " + e.getMessage());
        }
        return "unknown";
    }
    
    public static String getContainerPublishedPort(String containerName, String containerPort) {
        try {
            Process process = new ProcessBuilder("docker", "port", containerName, containerPort)
                .start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            if (process.exitValue() == 0) {
                return readProcessOutput(process).trim();
            }
        } catch (Exception e) {
            System.err.println("Error getting container port: " + e.getMessage());
        }
        return "";
    }
    
    public static String getContainerEnvironment(String containerName) {
        try {
            Process process = new ProcessBuilder("docker", "inspect", containerName,
                "--format", "{{json .Config.Env}}")
                .start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            if (process.exitValue() == 0) {
                String output = readProcessOutput(process);
                JsonNode envArray = objectMapper.readTree(output);
                StringBuilder envVars = new StringBuilder();
                for (JsonNode env : envArray) {
                    envVars.append(env.asText()).append(" ");
                }
                return envVars.toString();
            }
        } catch (Exception e) {
            System.err.println("Error getting container environment: " + e.getMessage());
        }
        return "";
    }
    
    public static String getContainerNetworkInfo(String containerName) {
        try {
            Process process = new ProcessBuilder("docker", "inspect", containerName,
                "--format", "{{json .NetworkSettings.Networks}}")
                .start();
            process.waitFor(30, TimeUnit.SECONDS);
            
            if (process.exitValue() == 0) {
                return readProcessOutput(process);
            }
        } catch (Exception e) {
            System.err.println("Error getting container network info: " + e.getMessage());
        }
        return "";
    }
    
    // Registry Operations
    
    public static boolean verifyImageInRegistry(String registryHost, String imageName, String tag) {
        try {
            Thread.sleep(3000); // Allow registry time to process
            
            Process curlProcess = new ProcessBuilder("curl", "-f", "-s",
                String.format("http://%s/v2/%s/tags/list", registryHost, imageName))
                .start();
            curlProcess.waitFor(30, TimeUnit.SECONDS);
            
            if (curlProcess.exitValue() == 0) {
                String output = readProcessOutput(curlProcess);
                return output.contains(tag);
            }
        } catch (Exception e) {
            System.err.println("Error verifying image in registry: " + e.getMessage());
        }
        return false;
    }
    
    public static String pullImageFromRegistry(String fullImageTag) {
        try {
            Process pullProcess = new ProcessBuilder("docker", "pull", fullImageTag)
                .start();
            pullProcess.waitFor(60, TimeUnit.SECONDS);
            
            if (pullProcess.exitValue() == 0) {
                return getImageId(fullImageTag);
            }
        } catch (Exception e) {
            System.err.println("Error pulling image from registry: " + e.getMessage());
        }
        return "";
    }
    
    // Gradle Task Execution
    
    public static BuildResult executeGradleTask(Path projectDir, String taskName, String... args) {
        List<String> allArgs = new ArrayList<>();
        allArgs.add(taskName);
        allArgs.add("--no-daemon");
        allArgs.add("--stacktrace");
        allArgs.addAll(List.of(args));
        
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(allArgs)
            .withPluginClasspath()
            .forwardOutput()
            .build();
    }
    
    public static BuildResult executeGradleTaskExpectingFailure(Path projectDir, String taskName, String... args) {
        List<String> allArgs = new ArrayList<>();
        allArgs.add(taskName);
        allArgs.add("--no-daemon");
        allArgs.add("--stacktrace");
        allArgs.addAll(List.of(args));
        
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(allArgs)
            .withPluginClasspath()
            .buildAndFail();
    }
    
    // System Checks
    
    public static boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info").start();
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    public static void assumeTestRegistryRunning(String registryHost) {
        try {
            Process checkProcess = new ProcessBuilder("curl", "-f", "-s",
                "http://" + registryHost + "/v2/")
                .start();
            checkProcess.waitFor(10, TimeUnit.SECONDS);
            
            if (checkProcess.exitValue() != 0) {
                throw new AssertionError("Test registry must be running at " + registryHost);
            }
        } catch (Exception e) {
            throw new AssertionError("Could not verify test registry: " + e.getMessage());
        }
    }
    
    // Resource Cleanup
    
    public static void cleanupImages(String... imageTags) {
        for (String imageTag : imageTags) {
            executeDockerCommand("docker", "rmi", imageTag);
        }
    }
    
    public static void cleanupProjectContainers(String projectName) {
        try {
            List<String> containers = getAllContainersForProject(projectName);
            
            for (String container : containers) {
                executeDockerCommand("docker", "stop", container);
                executeDockerCommand("docker", "rm", container);
            }
            
            // Clean up networks
            executeDockerCommand("docker", "network", "prune", "-f",
                "--filter", "label=com.docker.compose.project=" + projectName);
                
        } catch (Exception e) {
            System.err.println("Error during project cleanup: " + e.getMessage());
        }
    }
    
    public static void executeDockerCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            process.waitFor(30, TimeUnit.SECONDS);
            // Don't throw on non-zero exit - cleanup commands may fail if resources don't exist
        } catch (Exception e) {
            // Ignore docker command errors during cleanup
        }
    }
    
    // Utility Methods
    
    public static String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
    
    public static void waitForCondition(Runnable condition, long timeoutMillis, long checkIntervalMillis) 
            throws InterruptedException {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                condition.run();
                return; // Condition met
            } catch (Exception e) {
                // Condition not met, continue waiting
                Thread.sleep(checkIntervalMillis);
            }
        }
        
        throw new AssertionError("Condition not met within timeout: " + timeoutMillis + "ms");
    }
    
    public static String extractNetworkFromInfo(String networkInfo) {
        try {
            JsonNode networks = objectMapper.readTree(networkInfo);
            return networks.fieldNames().next(); // Get first network name
        } catch (Exception e) {
            return "";
        }
    }
    
    // Performance and Timing
    
    public static class Timer {
        private final long startTime;
        
        public Timer() {
            this.startTime = System.currentTimeMillis();
        }
        
        public long elapsedMillis() {
            return System.currentTimeMillis() - startTime;
        }
        
        public boolean hasExceeded(long timeoutMillis) {
            return elapsedMillis() > timeoutMillis;
        }
    }
}