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

import com.kineticfire.gradle.docker.junit.service.*;
import com.kineticfire.gradle.docker.model.*;
import com.kineticfire.gradle.docker.model.WaitForLogConfig;
import com.kineticfire.gradle.docker.service.ComposeService;
import com.kineticfire.gradle.docker.util.WaitForLogConfigBuilder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String COMPOSE_FILES_PROPERTY = "docker.compose.files";  // Changed from "file" to "files" (plural) to match usesCompose()
    private static final String COMPOSE_WAIT_SERVICES_PROPERTY = "docker.compose.waitServices";
    private static final String COMPOSE_STATE_FILE_PROPERTY = "COMPOSE_STATE_FILE";

    // Wait for running settings
    private static final String WAIT_FOR_RUNNING_SERVICES = "docker.compose.waitForRunning.services";
    private static final String WAIT_FOR_RUNNING_TIMEOUT = "docker.compose.waitForRunning.timeoutSeconds";
    private static final String WAIT_FOR_RUNNING_POLL = "docker.compose.waitForRunning.pollSeconds";

    // Wait for healthy settings
    private static final String WAIT_FOR_HEALTHY_SERVICES = "docker.compose.waitForHealthy.services";
    private static final String WAIT_FOR_HEALTHY_TIMEOUT = "docker.compose.waitForHealthy.timeoutSeconds";
    private static final String WAIT_FOR_HEALTHY_POLL = "docker.compose.waitForHealthy.pollSeconds";

    // Wait for log settings
    private static final String WAIT_FOR_LOG_SERVICES = "docker.compose.waitForLog.services";
    private static final String WAIT_FOR_LOG_REJECT_PATTERNS = "docker.compose.waitForLog.rejectPatterns";
    private static final String WAIT_FOR_LOG_TIMEOUT = "docker.compose.waitForLog.timeoutSeconds";
    private static final String WAIT_FOR_LOG_POLL = "docker.compose.waitForLog.pollSeconds";
    private static final String WAIT_FOR_LOG_CASE_INSENSITIVE = "docker.compose.waitForLog.caseInsensitive";
    private static final String WAIT_FOR_LOG_VERBOSE = "docker.compose.waitForLog.verbose";
    private static final String WAIT_FOR_LOG_PROGRESS_INTERVAL = "docker.compose.waitForLog.progressIntervalSeconds";

    // Store the unique project name per test thread to ensure cleanup uses the same name
    private final ThreadLocal<String> uniqueProjectName = new ThreadLocal<>();
    // Store the ComposeState from upStack for state file generation
    private final ThreadLocal<ComposeState> composeState = new ThreadLocal<>();

    // Service dependencies
    private final ComposeService composeService;
    private final ProcessExecutor processExecutor;
    private final FileService fileService;
    private final SystemPropertyService systemPropertyService;
    private final TimeService timeService;

    /**
     * Creates a new DockerComposeMethodExtension instance.
     *
     * This extension manages Docker Compose lifecycle at the test method level,
     * starting fresh containers before each test method and stopping them after each method completes.
     */
    public DockerComposeMethodExtension() {
        this(new JUnitComposeService(), new DefaultProcessExecutor(), new DefaultFileService(),
             new DefaultSystemPropertyService(), new DefaultTimeService());
    }

    /**
     * Creates a new DockerComposeMethodExtension instance with custom services.
     *
     * This constructor allows dependency injection for testing purposes.
     *
     * @param composeService the compose service for Docker Compose operations
     * @param processExecutor the process executor for running external commands
     * @param fileService the file service for file operations
     * @param systemPropertyService the system property service for property access
     * @param timeService the time service for time operations
     */
    public DockerComposeMethodExtension(ComposeService composeService, ProcessExecutor processExecutor,
                                       FileService fileService, SystemPropertyService systemPropertyService,
                                       TimeService timeService) {
        this.composeService = composeService;
        this.processExecutor = processExecutor;
        this.fileService = fileService;
        this.systemPropertyService = systemPropertyService;
        this.timeService = timeService;
    }
    
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String projectName = generateUniqueProjectName(context);
        // Store the project name for reuse in afterEach
        uniqueProjectName.set(projectName);
        
        System.out.println("Starting Docker Compose stack '" + stackName + 
                          "' for method: " + context.getDisplayName() + 
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
    public void afterEach(ExtensionContext context) throws Exception {
        String stackName = getStackName(context);
        String storedProjectName = uniqueProjectName.get();
        if (storedProjectName == null) {
            // Fallback if somehow the project name wasn't stored
            storedProjectName = generateUniqueProjectName(context);
        }
        
        System.out.println("Stopping Docker Compose stack '" + stackName + 
                          "' after method: " + context.getDisplayName() + 
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

        // Clear the ThreadLocals to prevent memory leaks
        uniqueProjectName.remove();
        composeState.remove();
    }
    
    private String getStackName(ExtensionContext context) {
        String stackName = systemPropertyService.getProperty(COMPOSE_STACK_PROPERTY);
        if (stackName == null || stackName.isEmpty()) {
            String className = context.getTestClass().map(Class::getName).orElse("unknown");
            throw new IllegalStateException(
                "Docker Compose stack name not configured for test class '" + className + "'.\n\n" +
                "This extension requires configuration from one of these sources:\n\n" +
                "Option 1 - Configure in build.gradle with usesCompose() (RECOMMENDED):\n" +
                "  tasks.named('integrationTest') {\n" +
                "      usesCompose(stack: 'myStack', lifecycle: 'method')\n" +
                "  }\n" +
                "  Then use: @ExtendWith(DockerComposeMethodExtension.class) // no parameters\n\n" +
                "Option 2 - Configure entirely in annotation (standalone mode):\n" +
                "  This extension does not support standalone annotation configuration.\n" +
                "  Use usesCompose() in build.gradle instead.\n\n" +
                "For more information: docs/usage/usage-docker-orch.md"
            );
        }
        return stackName;
    }

    private String getProjectName(ExtensionContext context) {
        String projectName = systemPropertyService.getProperty(COMPOSE_PROJECT_PROPERTY);
        if (projectName == null || projectName.isEmpty()) {
            // Fallback to test class name if project name not configured
            return context.getTestClass().map(Class::getSimpleName).orElse("test");
        }
        return projectName;
    }
    
    private String generateUniqueProjectName(ExtensionContext context) {
        String baseProjectName = getProjectName(context);

        // Create unique project name for this method to avoid conflicts
        // Docker Compose project names must be lowercase alphanumeric with hyphens/underscores
        String className = context.getTestClass().map(Class::getSimpleName).orElse("unknown");
        String methodName = context.getTestMethod().map(m -> m.getName()).orElse("unknown");
        String timestamp = timeService.now().format(DateTimeFormatter.ofPattern("HHmmss"));

        // Convert to valid Docker Compose project name format
        String projectName = baseProjectName + "-" + className + "-" + methodName + "-" + timestamp;
        return sanitizeProjectName(projectName);
    }
    
    private String sanitizeProjectName(String projectName) {
        // Convert to lowercase and replace invalid characters with hyphens
        String sanitized = projectName.toLowerCase()
            .replaceAll(/[^a-z0-9\-_]/, "-")  // Replace invalid chars with hyphens
            .replaceAll(/-+/, "-")             // Replace multiple hyphens with single
            .replaceAll(/^-/, "")              // Remove leading hyphens
            .replaceAll(/-$/, "");             // Remove trailing hyphens

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
            ProcessExecutor.ProcessResult result = processExecutor.executeWithTimeout(15, TimeUnit.SECONDS,
                "bash", "-c",
                "docker ps -aq --filter name=" + uniqueProjectName + " | xargs -r docker rm -f");
        } catch (Exception e) {
            System.err.println("Warning: Force container cleanup failed: " + e.getMessage());
        }

        // Stop and remove containers by project name pattern using compose label
        try {
            ProcessExecutor.ProcessResult result = processExecutor.execute(
                "docker", "container", "prune", "-f", "--filter", "label=com.docker.compose.project=" + uniqueProjectName
            );
        } catch (Exception e) {
            System.err.println("Warning: Container prune failed: " + e.getMessage());
        }

        // Clean up containers using integration test ports (8081, 8082, 8083, 8084, 8085)
        String[] ports = ["8081", "8082", "8083", "8084", "8085"];
        for (String port : ports) {
            try {
                ProcessExecutor.ProcessResult result = processExecutor.executeWithTimeout(10, TimeUnit.SECONDS,
                    "bash", "-c",
                    "docker ps -q --filter publish=" + port + " | xargs -r docker stop && " +
                    "docker ps -aq --filter publish=" + port + " | xargs -r docker rm");
            } catch (Exception e) {
                System.err.println("Warning: Port " + port + " cleanup failed: " + e.getMessage());
            }
        }
    }
    
    private void startComposeStack(String stackName, String uniqueProjectName) throws Exception {
        // Use ComposeService to start the stack
        // Read compose file paths from system property (comma-separated list)
        String composeFilesProperty = systemPropertyService.getProperty(COMPOSE_FILES_PROPERTY);
        if (composeFilesProperty == null || composeFilesProperty.isEmpty()) {
            throw new IllegalStateException(
                "Compose files not configured. " +
                "Ensure test task is configured with usesCompose and docker.compose.files system property is set."
            );
        }

        // Parse comma-separated file paths and convert to Path objects
        List<Path> composeFiles = Arrays.stream(composeFilesProperty.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(fileService::resolve)
            .collect(java.util.stream.Collectors.toList());

        if (composeFiles.isEmpty()) {
            throw new IllegalStateException("No valid compose files found in: " + composeFilesProperty);
        }

        // Verify all compose files exist
        for (Path composeFile : composeFiles) {
            if (!fileService.exists(composeFile)) {
                throw new IllegalStateException("Compose file not found: " + composeFile);
            }
        }

        // Create ComposeConfig
        ComposeConfig config = new ComposeConfig(
            composeFiles,           // composeFiles (can be multiple)
            uniqueProjectName,      // projectName
            stackName               // stackName
        );

        // Start the stack and store the state
        ComposeState state = composeService.upStack(config).get();
        composeState.set(state);

        System.out.println("Successfully started compose stack '" + stackName +
                          "' with project '" + uniqueProjectName + "'");
    }
    
    private void stopComposeStack(String stackName, String uniqueProjectName) throws Exception {
        // Use ComposeService to stop the stack
        try {
            composeService.downStack(uniqueProjectName).get();
            System.out.println("Successfully stopped compose stack '" + stackName +
                              "' with project '" + uniqueProjectName + "'");
        } catch (Exception e) {
            // For cleanup, log the error but don't throw exception
            System.err.println("Warning: Failed to cleanly stop compose stack '" + stackName +
                             "' with project '" + uniqueProjectName + "': " + e.getMessage());
        }
    }
    
    /**
     * Wait for services to reach desired state based on DSL configuration.
     *
     * <p>Execution order: waitForRunning -> waitForHealthy -> waitForLog</p>
     * <p>This method supports full lifecycle (METHOD) for all wait blocks.</p>
     */
    private void waitForStackToBeReady(String stackName, String uniqueProjectName) throws Exception {
        System.out.println("Waiting for containers to become ready...");

        // Step 1: Wait for RUNNING services (if configured)
        performWaitForRunning(uniqueProjectName);

        // Step 2: Wait for HEALTHY services (if configured)
        performWaitForHealthy(uniqueProjectName);

        // Step 3: Wait for log patterns (if configured)
        performWaitForLog(uniqueProjectName);

        System.out.println("All configured wait conditions satisfied for stack '" + stackName + "'");
    }

    /**
     * Wait for services to reach RUNNING state.
     */
    private void performWaitForRunning(String projectName) throws Exception {
        String servicesProperty = systemPropertyService.getProperty(WAIT_FOR_RUNNING_SERVICES);
        if (servicesProperty == null || servicesProperty.isEmpty()) {
            return;
        }

        List<String> services = Arrays.asList(servicesProperty.split(","));
        int timeoutSeconds = parseIntProperty(WAIT_FOR_RUNNING_TIMEOUT, 60);
        int pollSeconds = parseIntProperty(WAIT_FOR_RUNNING_POLL, 2);

        System.out.println("Waiting for services to be RUNNING: " + services);

        WaitConfig waitConfig = new WaitConfig(
            projectName,
            services,
            Duration.ofSeconds(timeoutSeconds),
            Duration.ofSeconds(pollSeconds),
            ServiceStatus.RUNNING
        );

        composeService.waitForServices(waitConfig).get();
        System.out.println("All services are RUNNING");
    }

    /**
     * Wait for services to reach HEALTHY state.
     */
    private void performWaitForHealthy(String projectName) throws Exception {
        String servicesProperty = systemPropertyService.getProperty(WAIT_FOR_HEALTHY_SERVICES);
        if (servicesProperty == null || servicesProperty.isEmpty()) {
            return;
        }

        List<String> services = Arrays.asList(servicesProperty.split(","));
        int timeoutSeconds = parseIntProperty(WAIT_FOR_HEALTHY_TIMEOUT, 60);
        int pollSeconds = parseIntProperty(WAIT_FOR_HEALTHY_POLL, 2);

        System.out.println("Waiting for services to be HEALTHY: " + services);

        WaitConfig waitConfig = new WaitConfig(
            projectName,
            services,
            Duration.ofSeconds(timeoutSeconds),
            Duration.ofSeconds(pollSeconds),
            ServiceStatus.HEALTHY
        );

        composeService.waitForServices(waitConfig).get();
        System.out.println("All services are HEALTHY");
    }

    /**
     * Wait for log patterns to appear in service logs.
     */
    private void performWaitForLog(String projectName) throws Exception {
        String servicesJson = systemPropertyService.getProperty(WAIT_FOR_LOG_SERVICES);
        if (servicesJson == null || servicesJson.isEmpty() || servicesJson.equals("{}")) {
            return;
        }

        // Parse JSON configuration
        Map<String, List<String>> services = parseJsonMapProperty(servicesJson);
        if (services.isEmpty()) {
            return;
        }

        Map<String, List<String>> rejectPatterns = parseJsonMapProperty(
            systemPropertyService.getProperty(WAIT_FOR_LOG_REJECT_PATTERNS)
        );

        int timeoutSeconds = parseIntProperty(WAIT_FOR_LOG_TIMEOUT, 60);
        int pollSeconds = parseIntProperty(WAIT_FOR_LOG_POLL, 2);
        boolean caseInsensitive = parseBooleanProperty(WAIT_FOR_LOG_CASE_INSENSITIVE, false);
        boolean verbose = parseBooleanProperty(WAIT_FOR_LOG_VERBOSE, false);
        int progressIntervalSeconds = parseIntProperty(WAIT_FOR_LOG_PROGRESS_INTERVAL, 0);

        System.out.println("Waiting for log patterns in services: " + services.keySet());

        // Build WaitForLogConfig using the builder
        WaitForLogConfig config = WaitForLogConfigBuilder.build(
            projectName,
            services,
            rejectPatterns,
            timeoutSeconds,
            pollSeconds,
            caseInsensitive,
            verbose,
            progressIntervalSeconds
        );

        composeService.waitForLogPatterns(config).get();
        System.out.println("All log patterns matched");
    }

    /**
     * Parse an integer system property with a default value.
     */
    private int parseIntProperty(String propertyName, int defaultValue) {
        String value = systemPropertyService.getProperty(propertyName);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid integer for " + propertyName + ": " + value +
                              ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Parse a boolean system property with a default value.
     */
    private boolean parseBooleanProperty(String propertyName, boolean defaultValue) {
        String value = systemPropertyService.getProperty(propertyName);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Parse a JSON map system property into Map<String, List<String>>.
     *
     * @param json JSON string in format: {"key1": ["val1", "val2"], "key2": ["val3"]}
     * @return Parsed map, or empty map if json is null/empty/invalid
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<String>> parseJsonMapProperty(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            // Use Groovy's JsonSlurper for parsing
            Object parsed = new groovy.json.JsonSlurper().parseText(json);
            if (parsed instanceof Map) {
                Map<String, Object> rawMap = (Map<String, Object>) parsed;
                Map<String, List<String>> result = new HashMap<>();
                for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                    if (entry.getValue() instanceof List) {
                        List<String> values = new ArrayList<>();
                        for (Object item : (List<?>) entry.getValue()) {
                            values.add(String.valueOf(item));
                        }
                        result.put(entry.getKey(), values);
                    }
                }
                return result;
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse JSON map: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    
    private void generateStateFile(String stackName, String uniqueProjectName,
                                   ExtensionContext context) throws IOException {
        // Generate a JSON state file using the ComposeState from upStack
        Path buildDir = fileService.resolve("build");
        Path stateDir = buildDir.resolve("compose-state");
        fileService.createDirectories(stateDir);

        String className = context.getTestClass().map(Class::getSimpleName).orElse("unknown");
        String methodName = context.getTestMethod().map(m -> m.getName()).orElse("unknown");
        Path stateFile = stateDir.resolve(stackName + "-" + className + "-" + methodName + "-state.json");

        // Get the stored state from upStack
        ComposeState state = composeState.get();
        if (state == null) {
            System.err.println("Warning: No ComposeState available for state file generation");
            return;
        }

        // Build JSON manually (could use Jackson or Gson in production)
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"stackName\": \"").append(stackName).append("\",\n");
        json.append("  \"projectName\": \"").append(uniqueProjectName).append("\",\n");
        json.append("  \"lifecycle\": \"method\",\n");
        json.append("  \"testClass\": \"").append(className).append("\",\n");
        json.append("  \"testMethod\": \"").append(methodName).append("\",\n");
        json.append("  \"timestamp\": \"").append(timeService.now()).append("\",\n");
        json.append("  \"services\": {\n");

        List<String> serviceEntries = new ArrayList<>();
        for (java.util.Map.Entry<String, ServiceInfo> entry : state.getServices().entrySet()) {
            String serviceName = entry.getKey();
            ServiceInfo serviceInfo = entry.getValue();

            StringBuilder serviceJson = new StringBuilder();
            serviceJson.append("    \"").append(serviceName).append("\": {\n");
            serviceJson.append("      \"containerId\": \"").append(serviceInfo.getContainerId()).append("\",\n");
            serviceJson.append("      \"containerName\": \"").append(serviceInfo.getContainerName()).append("\",\n");
            serviceJson.append("      \"state\": \"").append(serviceInfo.getState()).append("\",\n");
            serviceJson.append("      \"publishedPorts\": [");

            // Add port mappings
            List<String> portEntries = new ArrayList<>();
            for (PortMapping port : serviceInfo.getPublishedPorts()) {
                StringBuilder portJson = new StringBuilder();
                portJson.append("{");
                portJson.append("\"container\": ").append(port.getContainerPort()).append(", ");
                portJson.append("\"host\": ").append(port.getHostPort()).append(", ");
                portJson.append("\"protocol\": \"").append(port.getProtocol()).append("\"");
                portJson.append("}");
                portEntries.add(portJson.toString());
            }

            serviceJson.append(String.join(", ", portEntries));
            serviceJson.append("]\n");
            serviceJson.append("    }");
            serviceEntries.add(serviceJson.toString());
        }

        json.append(String.join(",\n", serviceEntries));
        json.append("\n");
        json.append("  }\n");
        json.append("}");

        fileService.writeString(stateFile, json.toString());

        // Set system property so tests can find the state file
        systemPropertyService.setProperty(COMPOSE_STATE_FILE_PROPERTY, stateFile.toString());
    }
    
    private void cleanupStateFile(String stackName, String uniqueProjectName) {
        try {
            Path buildDir = fileService.resolve("build");
            Path stateDir = buildDir.resolve("compose-state");

            // Clean up state files that match this method's pattern
            if (fileService.exists(stateDir)) {
                fileService.list(stateDir)
                     .filter(path -> path.getFileName().toString().startsWith(stackName + "-") &&
                                   path.getFileName().toString().contains(uniqueProjectName.substring(uniqueProjectName.lastIndexOf("-"))))
                     .forEach(path -> {
                         try {
                             fileService.delete(path);
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
            ProcessExecutor.ProcessResult listResult = processExecutor.execute(
                "docker", "ps", "-aq", "--filter", "name=" + uniqueProjectName
            );

            String containerIds = listResult.getOutput();
            if (!containerIds.trim().isEmpty()) {
                System.out.println("Found containers to remove: " + containerIds.trim().replace("\n", ", "));

                String[] containerIdArray = containerIds.trim().split("\n");
                for (String containerId : containerIdArray) {
                    if (!containerId.trim().isEmpty()) {
                        ProcessExecutor.ProcessResult removeResult = processExecutor.execute(
                            "docker", "rm", "-f", containerId.trim()
                        );

                        if (removeResult.getExitCode() != 0) {
                            System.err.println("Failed to remove container " + containerId.trim() + ": " + removeResult.getOutput());
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
            ProcessExecutor.ProcessResult labelListResult = processExecutor.execute(
                "docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + uniqueProjectName
            );

            String labelContainerIds = labelListResult.getOutput();
            if (!labelContainerIds.trim().isEmpty()) {
                System.out.println("Found containers by compose label: " + labelContainerIds.trim().replace("\n", ", "));

                String[] containerIdArray = labelContainerIds.trim().split("\n");
                for (String containerId : containerIdArray) {
                    if (!containerId.trim().isEmpty()) {
                        ProcessExecutor.ProcessResult removeResult = processExecutor.execute(
                            "docker", "rm", "-f", containerId.trim()
                        );

                        if (removeResult.getExitCode() != 0) {
                            System.err.println("Failed to remove container by label " + containerId.trim() + ": " + removeResult.getOutput());
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