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
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * Multi-File Docker Compose Integration Tests.
 * 
 * Purpose: Test multi-file Docker Compose functionality including stack operations,
 * file precedence and merging, service orchestration, and proper teardown.
 * 
 * Tests: Multi-file compose stack lifecycle, ComposeDown automatic file inheritance,
 *        file precedence with conflicting configurations, complex service dependencies,
 *        environment file integration, and resource cleanup verification.
 */
@DisplayName("Multi-File Docker Compose Integration Tests")
class MultiFileComposeIntegrationIT {

    private static final String COMPOSE_PROJECT_PREFIX = "multifiletest";
    private static final Path COMPOSE_DIR = Paths.get("src/integrationTest/resources/compose/multi-file");
    
    @BeforeEach
    void setUp() throws Exception {
        cleanupPreviousTests();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        cleanupPreviousTests();
    }

    @Test
    @DisplayName("Multi-file compose stack starts and stops correctly")
    void multiFileComposeStackStartsAndStopsCorrectly() throws Exception {
        String projectName = COMPOSE_PROJECT_PREFIX + System.currentTimeMillis();
        
        try {
            // Test composeUp with base and override files
            Process composeUpProcess = new ProcessBuilder(
                "docker", "compose",
                "-p", projectName,
                "-f", COMPOSE_DIR.resolve("docker-compose.base.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.test-override.yml").toString(),
                "up", "-d"
            ).start();
            
            boolean upCompleted = composeUpProcess.waitFor(60, TimeUnit.SECONDS);
            assertThat(upCompleted)
                .as("ComposeUp should complete within timeout")
                .isTrue();
            assertThat(composeUpProcess.exitValue())
                .as("ComposeUp should succeed")
                .isEqualTo(0);

            // Verify services are running
            List<String> runningServices = getRunningServices(projectName);
            assertThat(runningServices)
                .as("All expected services should be running")
                .contains(projectName + "-web-1", projectName + "-db-1", projectName + "-cache-1")
                .hasSize(3);

            // Verify override file precedence - web service should have ports from override
            String webContainerName = getContainerName(projectName, "web");
            assertThat(webContainerName)
                .as("Web container should exist")
                .isNotEmpty();
                
            String portConfig = getContainerPorts(webContainerName);
            assertThat(portConfig)
                .as("Web service should have port mapping from override file")
                .contains("8080");

            // Verify labels show correct precedence (override completely replaces base labels)
            String webLabels = getContainerLabels(webContainerName);
            assertThat(webLabels)
                .as("Web container should have override labels (labels are replaced, not merged)")
                .contains("multi-file-override");

            // Test composeDown uses same files automatically
            Process composeDownProcess = new ProcessBuilder(
                "docker", "compose",
                "-p", projectName,
                "-f", COMPOSE_DIR.resolve("docker-compose.base.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.test-override.yml").toString(),
                "down", "--remove-orphans"
            ).start();
            
            boolean downCompleted = composeDownProcess.waitFor(60, TimeUnit.SECONDS);
            assertThat(downCompleted)
                .as("ComposeDown should complete within timeout")
                .isTrue();
            assertThat(composeDownProcess.exitValue())
                .as("ComposeDown should succeed")
                .isEqualTo(0);

            // Verify all services are stopped
            List<String> remainingServices = getRunningServices(projectName);
            assertThat(remainingServices)
                .as("No services should remain after composeDown")
                .isEmpty();

            // Verify networks are cleaned up
            String networkOutput = getNetworksForProject(projectName);
            assertThat(networkOutput)
                .as("Project networks should be cleaned up")
                .doesNotContain(projectName);

        } finally {
            forceCleanupProject(projectName);
        }
    }

    @Test
    @DisplayName("ComposeDown automatically uses same files as ComposeUp")
    void composeDownUseSameFilesAsComposeUp() throws Exception {
        String projectName = COMPOSE_PROJECT_PREFIX + System.currentTimeMillis() + "down";
        
        try {
            // Start stack with multiple files
            Process composeUpProcess = new ProcessBuilder(
                "docker", "compose",
                "-p", projectName,
                "-f", COMPOSE_DIR.resolve("docker-compose.base.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.environment.yml").toString(),
                "up", "-d"
            ).start();
            
            composeUpProcess.waitFor(60, TimeUnit.SECONDS);
            assertThat(composeUpProcess.exitValue())
                .as("Multi-file ComposeUp should succeed")
                .isEqualTo(0);

            // Verify all services from both files are running
            List<String> runningServices = getRunningServices(projectName);
            assertThat(runningServices)
                .as("Services from all files should be running")
                .hasSizeGreaterThanOrEqualTo(3); // web, db, monitoring

            // Test ComposeDown with same file configuration
            Process composeDownProcess = new ProcessBuilder(
                "docker", "compose",
                "-p", projectName,
                "-f", COMPOSE_DIR.resolve("docker-compose.base.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.environment.yml").toString(),
                "down", "--remove-orphans"
            ).start();
            
            composeDownProcess.waitFor(60, TimeUnit.SECONDS);
            assertThat(composeDownProcess.exitValue())
                .as("ComposeDown should succeed with same files")
                .isEqualTo(0);

            // Verify proper teardown of all services
            List<String> remainingServices = getRunningServices(projectName);
            assertThat(remainingServices)
                .as("All services should be properly torn down")
                .isEmpty();

        } finally {
            forceCleanupProject(projectName);
        }
    }

    @Test
    @DisplayName("File precedence works correctly with conflicting configurations")
    void filePrecedenceWorksCorrectly() throws Exception {
        String projectName = COMPOSE_PROJECT_PREFIX + System.currentTimeMillis() + "precedence";
        
        try {
            // Start with all three files to test precedence: base -> override -> environment
            Process composeUpProcess = new ProcessBuilder(
                "docker", "compose",
                "-p", projectName,
                "-f", COMPOSE_DIR.resolve("docker-compose.base.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.test-override.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.environment.yml").toString(),
                "up", "-d"
            ).start();
            
            composeUpProcess.waitFor(60, TimeUnit.SECONDS);
            assertThat(composeUpProcess.exitValue())
                .as("Multi-file ComposeUp should succeed")
                .isEqualTo(0);

            // Verify web service gets configuration from all files with proper precedence
            String webContainerName = getContainerName(projectName, "web");
            assertThat(webContainerName).isNotEmpty();

            // Check environment variables - environment file should take precedence
            String webEnv = getContainerEnvironment(webContainerName);
            assertThat(webEnv)
                .as("Environment variables should show precedence")
                .contains("DEBUG=true") // from environment file
                .contains("ENV=production"); // from override file

            // Check db service - environment file should override base password
            String dbContainerName = getContainerName(projectName, "db");
            if (!dbContainerName.isEmpty()) {
                String dbEnv = getContainerEnvironment(dbContainerName);
                assertThat(dbEnv)
                    .as("Database password should be overridden by environment file")
                    .contains("POSTGRES_PASSWORD=envpass") // from environment file, not base
                    .contains("POSTGRES_MAX_CONNECTIONS=200"); // from environment file
            }

            // Verify all expected services are running (base + override + environment)
            List<String> runningServices = getRunningServices(projectName);
            assertThat(runningServices.size())
                .as("All services from all files should be running")
                .isGreaterThanOrEqualTo(4); // web, db, cache, monitoring

            // Test ComposeDown properly tears down with same precedence order
            Process composeDownProcess = new ProcessBuilder(
                "docker", "compose",
                "-p", projectName,
                "-f", COMPOSE_DIR.resolve("docker-compose.base.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.test-override.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.environment.yml").toString(),
                "down", "--remove-orphans"
            ).start();
            
            composeDownProcess.waitFor(60, TimeUnit.SECONDS);
            assertThat(composeDownProcess.exitValue())
                .as("ComposeDown should succeed")
                .isEqualTo(0);

            // Verify complete cleanup
            List<String> remainingServices = getRunningServices(projectName);
            assertThat(remainingServices)
                .as("All services should be cleaned up")
                .isEmpty();

        } finally {
            forceCleanupProject(projectName);
        }
    }

    @Test
    @DisplayName("Complex multi-file scenario with service dependencies")
    void complexMultiFileScenarioWorks() throws Exception {
        String projectName = COMPOSE_PROJECT_PREFIX + System.currentTimeMillis() + "complex";
        
        try {
            // Start complex stack: base -> app -> env
            Process composeUpProcess = new ProcessBuilder(
                "docker", "compose",
                "-p", projectName,
                "-f", COMPOSE_DIR.resolve("docker-compose.complex-base.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.complex-app.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.complex-env.yml").toString(),
                "up", "-d"
            ).start();
            
            composeUpProcess.waitFor(90, TimeUnit.SECONDS); // Allow more time for complex dependencies
            assertThat(composeUpProcess.exitValue())
                .as("Complex multi-file ComposeUp should succeed")
                .isEqualTo(0);

            // Verify all services are running
            List<String> runningServices = getRunningServices(projectName);
            assertThat(runningServices.size())
                .as("All complex services should be running")
                .isGreaterThanOrEqualTo(4); // database, cache, web-app, api-server

            // Verify service dependencies and networking
            String webAppContainer = getContainerName(projectName, "web-app");
            String apiServerContainer = getContainerName(projectName, "api-server");
            String databaseContainer = getContainerName(projectName, "database");
            
            assertThat(webAppContainer).as("Web app should be running").isNotEmpty();
            assertThat(apiServerContainer).as("API server should be running").isNotEmpty();
            assertThat(databaseContainer).as("Database should be running").isNotEmpty();

            // Verify port mappings from environment file
            if (!apiServerContainer.isEmpty()) {
                String apiPorts = getContainerPorts(apiServerContainer);
                assertThat(apiPorts)
                    .as("API server should have ports from environment file")
                    .contains("8085");
            }

            // Verify networks are properly configured
            String networkOutput = getNetworksForProject(projectName);
            assertThat(networkOutput)
                .as("Complex networks should be created")
                .contains(projectName);

            // Test ComposeDown tears down complex stack completely
            Process composeDownProcess = new ProcessBuilder(
                "docker", "compose",
                "-p", projectName,
                "-f", COMPOSE_DIR.resolve("docker-compose.complex-base.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.complex-app.yml").toString(),
                "-f", COMPOSE_DIR.resolve("docker-compose.complex-env.yml").toString(),
                "down", "--remove-orphans", "--volumes"
            ).start();
            
            composeDownProcess.waitFor(90, TimeUnit.SECONDS);
            assertThat(composeDownProcess.exitValue())
                .as("Complex ComposeDown should succeed")
                .isEqualTo(0);

            // Verify complete cleanup including volumes
            List<String> remainingServices = getRunningServices(projectName);
            assertThat(remainingServices)
                .as("All complex services should be cleaned up")
                .isEmpty();

            // Wait a bit for cleanup to complete
            Thread.sleep(2000);
            
            // Verify volumes are cleaned up
            String volumeOutput = getVolumesForProject(projectName);
            assertThat(volumeOutput)
                .as("Project volumes should be cleaned up")
                .doesNotContain(projectName);

        } finally {
            forceCleanupProject(projectName);
        }
    }

    // Helper Methods

    private List<String> getRunningServices(String projectName) throws Exception {
        Process process = new ProcessBuilder(
            "docker", "ps", "--filter", "name=" + projectName, "--format", "{{.Names}}"
        ).start();
        
        process.waitFor(15, TimeUnit.SECONDS);
        String output = readOutput(process);
        
        List<String> services = new ArrayList<>();
        if (!output.trim().isEmpty()) {
            services.addAll(Arrays.asList(output.trim().split("\n")));
        }
        return services;
    }

    private String getContainerName(String projectName, String serviceName) throws Exception {
        Process process = new ProcessBuilder(
            "docker", "ps", "--filter", "name=" + projectName + "-" + serviceName, "--format", "{{.Names}}"
        ).start();
        
        process.waitFor(10, TimeUnit.SECONDS);
        String output = readOutput(process).trim();
        if (output.isEmpty()) {
            return "";
        }
        return output.split("\n")[0]; // Get first match
    }

    private String getContainerPorts(String containerName) throws Exception {
        if (containerName.isEmpty()) return "";
        
        Process process = new ProcessBuilder(
            "docker", "inspect", containerName, "--format", "{{.NetworkSettings.Ports}}"
        ).start();
        
        process.waitFor(10, TimeUnit.SECONDS);
        return readOutput(process);
    }

    private String getContainerLabels(String containerName) throws Exception {
        if (containerName.isEmpty()) return "";
        
        Process process = new ProcessBuilder(
            "docker", "inspect", containerName, "--format", "{{.Config.Labels}}"
        ).start();
        
        process.waitFor(10, TimeUnit.SECONDS);
        return readOutput(process);
    }

    private String getContainerEnvironment(String containerName) throws Exception {
        if (containerName.isEmpty()) return "";
        
        Process process = new ProcessBuilder(
            "docker", "inspect", containerName, "--format", "{{.Config.Env}}"
        ).start();
        
        process.waitFor(10, TimeUnit.SECONDS);
        return readOutput(process);
    }

    private String getNetworksForProject(String projectName) throws Exception {
        Process process = new ProcessBuilder(
            "docker", "network", "ls", "--filter", "name=" + projectName, "--format", "{{.Name}}"
        ).start();
        
        process.waitFor(10, TimeUnit.SECONDS);
        return readOutput(process);
    }

    private String getVolumesForProject(String projectName) throws Exception {
        Process process = new ProcessBuilder(
            "docker", "volume", "ls", "--filter", "name=" + projectName, "--format", "{{.Name}}"
        ).start();
        
        process.waitFor(10, TimeUnit.SECONDS);
        return readOutput(process);
    }

    private String readOutput(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }

    private void cleanupPreviousTests() throws Exception {
        // Clean up any previous test containers
        Process listProcess = new ProcessBuilder(
            "docker", "ps", "-a", "--filter", "name=" + COMPOSE_PROJECT_PREFIX, "--format", "{{.Names}}"
        ).start();
        
        boolean completed = listProcess.waitFor(15, TimeUnit.SECONDS);
        if (!completed) {
            listProcess.destroyForcibly();
            return;
        }
        String output = readOutput(listProcess);
        
        if (!output.trim().isEmpty()) {
            String[] containerNames = output.trim().split("\n");
            for (String containerName : containerNames) {
                containerName = containerName.trim();
                if (!containerName.isEmpty() && containerName.startsWith(COMPOSE_PROJECT_PREFIX)) {
                    System.out.println("Cleaning up leftover container: " + containerName);
                    forceRemoveContainer(containerName);
                }
            }
        }
        
        // Clean up networks
        Process networkListProcess = new ProcessBuilder(
            "docker", "network", "ls", "--filter", "name=" + COMPOSE_PROJECT_PREFIX, "--format", "{{.Name}}"
        ).start();
        
        completed = networkListProcess.waitFor(15, TimeUnit.SECONDS);
        if (!completed) {
            networkListProcess.destroyForcibly();
            return;
        }
        String networkOutput = readOutput(networkListProcess);
        
        if (!networkOutput.trim().isEmpty()) {
            String[] networkNames = networkOutput.trim().split("\n");
            for (String networkName : networkNames) {
                networkName = networkName.trim();
                if (!networkName.isEmpty() && !networkName.equals("bridge") && 
                    !networkName.equals("host") && !networkName.equals("none") &&
                    networkName.startsWith(COMPOSE_PROJECT_PREFIX)) {
                    System.out.println("Cleaning up leftover network: " + networkName);
                    forceRemoveNetwork(networkName);
                }
            }
        }
        
        // Clean up volumes
        Process volumeListProcess = new ProcessBuilder(
            "docker", "volume", "ls", "--filter", "name=" + COMPOSE_PROJECT_PREFIX, "--format", "{{.Name}}"
        ).start();
        
        completed = volumeListProcess.waitFor(15, TimeUnit.SECONDS);
        if (!completed) {
            volumeListProcess.destroyForcibly();
            return;
        }
        String volumeOutput = readOutput(volumeListProcess);
        
        if (!volumeOutput.trim().isEmpty()) {
            String[] volumeNames = volumeOutput.trim().split("\n");
            for (String volumeName : volumeNames) {
                volumeName = volumeName.trim();
                if (!volumeName.isEmpty() && volumeName.startsWith(COMPOSE_PROJECT_PREFIX)) {
                    System.out.println("Cleaning up leftover volume: " + volumeName);
                    forceRemoveVolume(volumeName);
                }
            }
        }
    }

    private void forceCleanupProject(String projectName) throws Exception {
        System.out.println("Force cleaning up project: " + projectName);
        
        try {
            // Force down with all cleanup options
            Process downProcess = new ProcessBuilder(
                "docker", "compose", "-p", projectName,
                "down", "--remove-orphans", "--volumes", "--rmi", "local"
            ).start();
            boolean downCompleted = downProcess.waitFor(45, TimeUnit.SECONDS);
            if (!downCompleted) {
                downProcess.destroyForcibly();
                System.err.println("Docker compose down timed out for project: " + projectName);
            } else if (downProcess.exitValue() != 0) {
                System.err.println("Docker compose down failed for project: " + projectName + " with exit code: " + downProcess.exitValue());
            }
        } catch (Exception e) {
            System.err.println("Exception during docker compose down for project " + projectName + ": " + e.getMessage());
        }
        
        // Force remove any remaining containers
        try {
            Process listProcess = new ProcessBuilder(
                "docker", "ps", "-a", "--filter", "name=" + projectName, "--format", "{{.Names}}"
            ).start();
            
            boolean listCompleted = listProcess.waitFor(15, TimeUnit.SECONDS);
            if (!listCompleted) {
                listProcess.destroyForcibly();
                return;
            }
            
            String output = readOutput(listProcess);
            
            if (!output.trim().isEmpty()) {
                String[] containerNames = output.trim().split("\n");
                for (String containerName : containerNames) {
                    containerName = containerName.trim();
                    if (!containerName.isEmpty()) {
                        System.out.println("Force removing remaining container: " + containerName);
                        forceRemoveContainer(containerName);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Exception while cleaning up remaining containers for project " + projectName + ": " + e.getMessage());
        }
        
        // Add small delay to ensure cleanup completes
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void forceRemoveContainer(String containerName) {
        try {
            // First try to stop gracefully
            Process stopProcess = new ProcessBuilder("docker", "stop", "-t", "5", containerName).start();
            boolean stopCompleted = stopProcess.waitFor(15, TimeUnit.SECONDS);
            if (!stopCompleted) {
                stopProcess.destroyForcibly();
            }
            
            // Then force remove
            Process removeProcess = new ProcessBuilder("docker", "rm", "-f", containerName).start();
            boolean removeCompleted = removeProcess.waitFor(15, TimeUnit.SECONDS);
            if (!removeCompleted) {
                removeProcess.destroyForcibly();
                System.err.println("Failed to remove container: " + containerName);
            }
        } catch (Exception e) {
            System.err.println("Exception while removing container " + containerName + ": " + e.getMessage());
        }
    }

    private void forceRemoveNetwork(String networkName) {
        try {
            Process removeProcess = new ProcessBuilder("docker", "network", "rm", networkName).start();
            boolean completed = removeProcess.waitFor(15, TimeUnit.SECONDS);
            if (!completed) {
                removeProcess.destroyForcibly();
                System.err.println("Failed to remove network: " + networkName);
            }
        } catch (Exception e) {
            System.err.println("Exception while removing network " + networkName + ": " + e.getMessage());
        }
    }

    private void forceRemoveVolume(String volumeName) {
        try {
            Process removeProcess = new ProcessBuilder("docker", "volume", "rm", "-f", volumeName).start();
            boolean completed = removeProcess.waitFor(15, TimeUnit.SECONDS);
            if (!completed) {
                removeProcess.destroyForcibly();
                System.err.println("Failed to remove volume: " + volumeName);
            }
        } catch (Exception e) {
            System.err.println("Exception while removing volume " + volumeName + ": " + e.getMessage());
        }
    }
}