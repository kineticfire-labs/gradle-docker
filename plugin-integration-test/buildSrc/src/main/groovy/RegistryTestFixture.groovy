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

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Registry Test Fixture for managing Docker registry containers during integration testing.
 *
 * Provides robust container lifecycle management with multiple cleanup layers:
 * - Container tracking for proper cleanup
 * - Port allocation management
 * - Volume and network cleanup
 * - Emergency cleanup for orphaned resources
 * - Health monitoring and recovery
 *
 * This fixture ensures no registry containers are left running after tests,
 * even in failure scenarios.
 */
class RegistryTestFixture {

    private static final Logger logger = Logging.getLogger(RegistryTestFixture)
    private static final String REGISTRY_LABEL = "gradle-docker-test-registry"
    private static final String SESSION_LABEL = "gradle-docker-test-session"

    private Map<String, String> runningContainers = [:]
    private Map<String, Integer> allocatedPorts = [:]
    private Set<String> createdVolumes = []
    private Set<String> createdNetworks = []
    private String sessionId = UUID.randomUUID().toString()

    /**
     * Configuration for a test registry
     */
    static class RegistryConfig {
        String name
        int port
        boolean requiresAuth = false
        String username = null
        String password = null
        Map<String, String> extraLabels = [:]

        RegistryConfig(String name, int port) {
            this.name = name
            this.port = port
        }

        RegistryConfig withAuth(String username, String password) {
            this.requiresAuth = true
            this.username = username
            this.password = password
            return this
        }

        RegistryConfig withLabels(Map<String, String> labels) {
            this.extraLabels.putAll(labels)
            return this
        }
    }

    /**
     * Start test registries with robust cleanup registration
     */
    Map<String, RegistryInfo> startTestRegistries(List<RegistryConfig> configs) {
        Map<String, RegistryInfo> registries = [:]

        try {
            for (RegistryConfig config : configs) {
                logger.lifecycle("Starting test registry: ${config.name} on port ${config.port}")

                // Allocate and track port
                allocatedPorts[config.name] = config.port

                // Start container with proper labeling for cleanup
                String containerId = startRegistryContainer(config)
                runningContainers[config.name] = containerId

                // Wait for registry to be ready
                waitForRegistryHealth(config.name, config.port)

                registries[config.name] = new RegistryInfo(
                    name: config.name,
                    port: config.port,
                    containerId: containerId,
                    requiresAuth: config.requiresAuth,
                    username: config.username,
                    password: config.password
                )

                logger.lifecycle("✓ Test registry started: ${config.name} (${containerId})")
            }

            // Register immediate cleanup hooks
            registerCleanupHooks()

            return registries

        } catch (Exception e) {
            logger.error("Failed to start test registries, performing emergency cleanup", e)
            emergencyCleanup()
            throw new RuntimeException("Registry startup failed: ${e.message}", e)
        }
    }

    /**
     * Start a single registry container with proper labeling
     */
    private String startRegistryContainer(RegistryConfig config) {
        def labels = [
            (REGISTRY_LABEL): "true",
            (SESSION_LABEL): sessionId,
            "registry-name": config.name,
            "test-port": config.port.toString()
        ]
        labels.putAll(config.extraLabels)

        // Build docker run command
        List<String> command = [
            'docker', 'run', '-d',
            '--name', "test-registry-${config.name}-${sessionId}",
            '-p', "${config.port}:5000"
        ]

        // Add labels
        labels.each { key, value ->
            command.addAll(['--label', "${key}=${value}"])
        }

        // Configure authentication if required
        if (config.requiresAuth) {
            // Create htpasswd file for authentication
            String htpasswdContent = generateHtpasswd(config.username, config.password)

            // Use environment variables for auth config
            command.addAll([
                '-e', 'REGISTRY_AUTH=htpasswd',
                '-e', 'REGISTRY_AUTH_HTPASSWD_REALM=Registry Realm',
                '-e', "REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd"
            ])

            // Mount auth config via tmpfs (secure)
            command.addAll([
                '--tmpfs', '/auth:noexec,nosuid,size=1m',
                '--entrypoint', '/bin/sh'
            ])
        }

        // Add registry image
        command.add('registry:2')

        if (config.requiresAuth) {
            // Custom entrypoint to set up auth
            command.addAll([
                '-c',
                "echo '${htpasswdContent}' > /auth/htpasswd && exec /entrypoint.sh /etc/docker/registry/config.yml"
            ])
        }

        logger.debug("Starting registry with command: ${command.join(' ')}")

        def process = command.execute()
        def exitCode = process.waitFor()
        def stdout = process.inputStream.text.trim()
        def stderr = process.errorStream.text.trim()

        if (exitCode != 0) {
            throw new RuntimeException("Failed to start registry container: ${stderr}")
        }

        return stdout // Container ID
    }

    /**
     * Generate htpasswd entry for basic authentication
     */
    private String generateHtpasswd(String username, String password) {
        // Use bcrypt for password hashing (simple approach for testing)
        def process = ['docker', 'run', '--rm', '--entrypoint', 'htpasswd', 'httpd:2.4', '-Bbn', username, password].execute()
        def exitCode = process.waitFor()
        def stdout = process.inputStream.text.trim()

        if (exitCode != 0) {
            throw new RuntimeException("Failed to generate htpasswd entry")
        }

        return stdout
    }

    /**
     * Wait for registry to be healthy and ready
     */
    private void waitForRegistryHealth(String registryName, int port) {
        int maxAttempts = 30
        int attempt = 0

        while (attempt < maxAttempts) {
            try {
                def process = ['curl', '-f', '-s', "http://localhost:${port}/v2/"].execute()
                def exitCode = process.waitFor()

                if (exitCode == 0) {
                    logger.debug("Registry ${registryName} is healthy")
                    return
                }
            } catch (Exception e) {
                // Ignore and retry
            }

            attempt++
            Thread.sleep(1000) // Wait 1 second between attempts
        }

        throw new RuntimeException("Registry ${registryName} failed to become healthy after ${maxAttempts} attempts")
    }

    /**
     * Graceful shutdown of all test registries
     */
    void stopAllRegistries() {
        logger.lifecycle("Stopping all test registries")

        // Stop containers in reverse order
        runningContainers.entrySet().toList().reverse().each { entry ->
            def name = entry.key
            def containerId = entry.value
            try {
                logger.debug("Stopping registry container: ${name} (${containerId})")
                stopContainer(containerId)
                removeContainer(containerId)
                logger.lifecycle("✓ Stopped registry: ${name}")
            } catch (Exception e) {
                logger.warn("Failed to stop registry container ${name}: ${e.message}")
            }
        }

        // Clean up other resources
        cleanupVolumes()
        cleanupNetworks()
        releaseAllocatedPorts()

        // Clear tracking maps
        runningContainers.clear()
        createdVolumes.clear()
        createdNetworks.clear()
        allocatedPorts.clear()

        logger.lifecycle("All test registries stopped and cleaned up")
    }

    /**
     * Emergency cleanup for orphaned containers and resources
     */
    void emergencyCleanup() {
        logger.warn("Performing emergency cleanup of test registries")

        try {
            // Find and stop ALL containers with our test label
            def orphanedContainers = findContainersByLabel(REGISTRY_LABEL)
            logger.lifecycle("Found ${orphanedContainers.size()} orphaned registry containers")

            orphanedContainers.each { containerId ->
                try {
                    forceStopContainer(containerId)
                    forceRemoveContainer(containerId)
                    logger.debug("✓ Cleaned up orphaned container: ${containerId}")
                } catch (Exception e) {
                    logger.warn("Failed to clean up container ${containerId}: ${e.message}")
                }
            }

            // Clean up volumes created by test registries
            def orphanedVolumes = findVolumesByLabel(REGISTRY_LABEL)
            orphanedVolumes.each { volumeName ->
                try {
                    forceRemoveVolume(volumeName)
                    logger.debug("✓ Cleaned up orphaned volume: ${volumeName}")
                } catch (Exception e) {
                    logger.warn("Failed to clean up volume ${volumeName}: ${e.message}")
                }
            }

            // Clean up networks created by test registries
            def orphanedNetworks = findNetworksByLabel(REGISTRY_LABEL)
            orphanedNetworks.each { networkName ->
                try {
                    forceRemoveNetwork(networkName)
                    logger.debug("✓ Cleaned up orphaned network: ${networkName}")
                } catch (Exception e) {
                    logger.warn("Failed to clean up network ${networkName}: ${e.message}")
                }
            }

            // Release allocated ports
            releaseAllocatedPorts()

            logger.lifecycle("Emergency cleanup completed")

        } catch (Exception e) {
            logger.error("Emergency cleanup failed", e)
        }
    }

    /**
     * Verify health of all running registries
     */
    void verifyRegistryHealth() {
        def unhealthyRegistries = []

        runningContainers.each { name, containerId ->
            if (!isContainerHealthy(containerId)) {
                logger.warn("Unhealthy registry container detected: ${name}")
                unhealthyRegistries.add(name)
            }
        }

        // Clean up unhealthy containers
        unhealthyRegistries.each { name ->
            try {
                String containerId = runningContainers[name]
                stopContainer(containerId)
                removeContainer(containerId)
                runningContainers.remove(name)
                logger.lifecycle("Cleaned up unhealthy registry: ${name}")
            } catch (Exception e) {
                logger.warn("Failed to clean up unhealthy registry ${name}: ${e.message}")
            }
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void registerCleanupHooks() {
        // Register JVM shutdown hook as backup
        Runtime.runtime.addShutdownHook(new Thread({
            logger.warn("JVM shutdown detected, performing emergency cleanup")
            emergencyCleanup()
        }))
    }

    private void stopContainer(String containerId) {
        def process = ['docker', 'stop', containerId].execute()
        process.waitFor()
    }

    private void removeContainer(String containerId) {
        def process = ['docker', 'rm', '-f', containerId].execute()
        process.waitFor()
    }

    private void forceStopContainer(String containerId) {
        def process = ['docker', 'stop', '-t', '2', containerId].execute()
        process.waitFor()
    }

    private void forceRemoveContainer(String containerId) {
        def process = ['docker', 'rm', '-f', containerId].execute()
        process.waitFor()
    }

    private void forceRemoveVolume(String volumeName) {
        def process = ['docker', 'volume', 'rm', '-f', volumeName].execute()
        process.waitFor()
    }

    private void forceRemoveNetwork(String networkName) {
        def process = ['docker', 'network', 'rm', networkName].execute()
        process.waitFor()
    }

    private boolean isContainerHealthy(String containerId) {
        try {
            def process = ['docker', 'inspect', '--format', '{{.State.Status}}', containerId].execute()
            def exitCode = process.waitFor()
            def status = process.inputStream.text.trim()

            return exitCode == 0 && status == 'running'
        } catch (Exception e) {
            return false
        }
    }

    private List<String> findContainersByLabel(String label) {
        try {
            def process = ['docker', 'ps', '-aq', '--filter', "label=${label}"].execute()
            def exitCode = process.waitFor()
            def stdout = process.inputStream.text.trim()

            if (exitCode == 0 && !stdout.isEmpty()) {
                return stdout.split('\n').toList()
            }
        } catch (Exception e) {
            logger.debug("Error finding containers by label: ${e.message}")
        }

        return []
    }

    private List<String> findVolumesByLabel(String label) {
        try {
            def process = ['docker', 'volume', 'ls', '-q', '--filter', "label=${label}"].execute()
            def exitCode = process.waitFor()
            def stdout = process.inputStream.text.trim()

            if (exitCode == 0 && !stdout.isEmpty()) {
                return stdout.split('\n').toList()
            }
        } catch (Exception e) {
            logger.debug("Error finding volumes by label: ${e.message}")
        }

        return []
    }

    private List<String> findNetworksByLabel(String label) {
        try {
            def process = ['docker', 'network', 'ls', '-q', '--filter', "label=${label}"].execute()
            def exitCode = process.waitFor()
            def stdout = process.inputStream.text.trim()

            if (exitCode == 0 && !stdout.isEmpty()) {
                return stdout.split('\n').toList()
            }
        } catch (Exception e) {
            logger.debug("Error finding networks by label: ${e.message}")
        }

        return []
    }

    private void cleanupVolumes() {
        createdVolumes.each { volumeName ->
            try {
                forceRemoveVolume(volumeName)
            } catch (Exception e) {
                logger.debug("Failed to remove volume ${volumeName}: ${e.message}")
            }
        }
    }

    private void cleanupNetworks() {
        createdNetworks.each { networkName ->
            try {
                forceRemoveNetwork(networkName)
            } catch (Exception e) {
                logger.debug("Failed to remove network ${networkName}: ${e.message}")
            }
        }
    }

    private void releaseAllocatedPorts() {
        allocatedPorts.clear()
    }

    /**
     * Registry information for test usage
     */
    static class RegistryInfo {
        String name
        int port
        String containerId
        boolean requiresAuth
        String username
        String password

        String getUrl() {
            return "localhost:${port}"
        }

        String getFullImageName(String imageName) {
            return "${url}/${imageName}"
        }
    }
}