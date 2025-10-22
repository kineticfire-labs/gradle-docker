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

package com.kineticfire.test

import groovy.transform.CompileStatic

/**
 * Validator for Docker Compose operations
 * Used by integration tests to verify container state
 */
@CompileStatic
class DockerComposeValidator {

    /**
     * Check if a container is running
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @return true if container is running
     */
    static boolean isContainerRunning(String projectName, String serviceName) {
        def process = [
            'docker', 'ps',
            '--filter', "name=${projectName}",
            '--filter', "name=${serviceName}",
            '--filter', 'status=running',
            '--format', '{{.Names}}'
        ].execute()
        process.waitFor()
        def output = process.text.trim()
        return output.contains(serviceName)
    }

    /**
     * Get all running containers for a project
     */
    static List<String> getRunningContainers(String projectName) {
        def process = [
            'docker', 'ps',
            '--filter', "name=${projectName}",
            '--filter', 'status=running',
            '--format', '{{.Names}}'
        ].execute()
        process.waitFor()
        return process.text.trim().split('\n').findAll { it }
    }

    /**
     * Get container status (running, healthy, unhealthy, exited, etc.)
     */
    static String getContainerStatus(String projectName, String serviceName) {
        // First get container name
        def nameProcess = [
            'docker', 'ps', '-a',
            '--filter', "name=${projectName}",
            '--filter', "name=${serviceName}",
            '--format', '{{.Names}}'
        ].execute()
        nameProcess.waitFor()
        def containerName = nameProcess.text.trim().split('\n').find { it }

        if (!containerName) {
            return 'not-found'
        }

        // Get status including health
        def statusProcess = [
            'docker', 'inspect', containerName,
            '--format', '{{.State.Status}}'
        ].execute()
        statusProcess.waitFor()
        def status = statusProcess.text.trim()

        // Check health if container is running
        if (status == 'running') {
            def healthProcess = [
                'docker', 'inspect', containerName,
                '--format', '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}'
            ].execute()
            healthProcess.waitFor()
            def health = healthProcess.text.trim()

            if (health == 'healthy') {
                return 'healthy'
            } else if (health == 'unhealthy') {
                return 'unhealthy'
            } else if (health == 'starting') {
                return 'starting'
            }
        }

        return status
    }

    /**
     * Check if container is healthy using docker inspect
     *
     * NOTE: This method uses 'docker inspect' which may lag slightly behind 'docker compose ps'.
     * For checking health status immediately after compose up, consider using isServiceHealthyViaCompose()
     * which uses the same mechanism as the plugin's wait logic.
     */
    static boolean isContainerHealthy(String projectName, String serviceName) {
        return getContainerStatus(projectName, serviceName) == 'healthy'
    }

    /**
     * Check if service is healthy using docker compose ps
     *
     * This method uses 'docker compose ps' which matches the mechanism used by the plugin's
     * waitForHealthy logic. Use this method when you need to verify the same state that
     * the plugin just verified, avoiding timing issues between different Docker commands.
     *
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @return true if service status contains 'healthy'
     */
    static boolean isServiceHealthyViaCompose(String projectName, String serviceName) {
        try {
            def process = [
                'docker', 'compose',
                '-p', projectName,
                'ps', serviceName,
                '--format', 'table'
            ].execute()
            process.waitFor()

            if (process.exitValue() == 0) {
                def output = process.text.toLowerCase()
                // Check if status contains 'healthy' (e.g., "Up 2 minutes (healthy)")
                return output.contains('healthy')
            }

            return false
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Check if service is running using docker compose ps
     *
     * This method uses 'docker compose ps' which matches the mechanism used by the plugin's
     * waitForRunning logic.
     *
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @return true if service status contains 'up' or 'running'
     */
    static boolean isServiceRunningViaCompose(String projectName, String serviceName) {
        try {
            def process = [
                'docker', 'compose',
                '-p', projectName,
                'ps', serviceName,
                '--format', 'table'
            ].execute()
            process.waitFor()

            if (process.exitValue() == 0) {
                def output = process.text.toLowerCase()
                // Check if status contains 'up' or 'running'
                return output.contains('up') || output.contains('running')
            }

            return false
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Check if container has a health check configured
     * @param projectName Docker Compose project name
     * @param serviceName Service name from compose file
     * @return true if container has a health check, false otherwise
     */
    static boolean hasHealthCheck(String projectName, String serviceName) {
        // First get container name
        def nameProcess = [
            'docker', 'ps', '-a',
            '--filter', "name=${projectName}",
            '--filter', "name=${serviceName}",
            '--format', '{{.Names}}'
        ].execute()
        nameProcess.waitFor()
        def containerName = nameProcess.text.trim().split('\n').find { it }

        if (!containerName) {
            return false
        }

        // Check if health check is configured
        def healthProcess = [
            'docker', 'inspect', containerName,
            '--format', '{{if .State.Health}}true{{else}}false{{end}}'
        ].execute()
        healthProcess.waitFor()
        def hasHealth = healthProcess.text.trim()

        return hasHealth == 'true'
    }

    /**
     * Get container logs
     */
    static String getContainerLogs(String projectName, String serviceName, int tailLines = 100) {
        def nameProcess = [
            'docker', 'ps', '-a',
            '--filter', "name=${projectName}",
            '--filter', "name=${serviceName}",
            '--format', '{{.Names}}'
        ].execute()
        nameProcess.waitFor()
        def containerName = nameProcess.text.trim().split('\n').find { it }

        if (!containerName) {
            return "Container not found: ${projectName}/${serviceName}"
        }

        def logsProcess = [
            'docker', 'logs', '--tail', tailLines.toString(), containerName
        ].execute()
        logsProcess.waitFor()
        return logsProcess.text
    }

    /**
     * Find all containers (running or stopped) matching project name
     */
    static List<String> findAllContainers(String projectName) {
        def process = [
            'docker', 'ps', '-a',
            '--filter', "name=${projectName}",
            '--format', '{{.Names}}'
        ].execute()
        process.waitFor()
        return process.text.trim().split('\n').findAll { it }
    }

    /**
     * Find all networks matching project name
     */
    static List<String> findAllNetworks(String projectName) {
        def process = [
            'docker', 'network', 'ls',
            '--filter', "name=${projectName}",
            '--format', '{{.Name}}'
        ].execute()
        process.waitFor()
        return process.text.trim().split('\n').findAll {
            it && it != 'bridge' && it != 'host' && it != 'none'
        }
    }

    /**
     * Assert no containers or networks remain
     */
    static void assertNoLeaks(String projectName) {
        def containers = findAllContainers(projectName)
        def networks = findAllNetworks(projectName)

        if (containers || networks) {
            def message = "Resource leaks detected for project '${projectName}':\n"
            if (containers) {
                message += "  Containers: ${containers.join(', ')}\n"
            }
            if (networks) {
                message += "  Networks: ${networks.join(', ')}\n"
            }
            throw new AssertionError(message)
        }
    }

    /**
     * Force cleanup of all resources for a project
     */
    static void forceCleanup(String projectName) {
        // Remove containers
        def containers = findAllContainers(projectName)
        containers.each { containerName ->
            ['docker', 'rm', '-f', containerName].execute().waitFor()
        }

        // Remove networks
        def networks = findAllNetworks(projectName)
        networks.each { networkName ->
            ['docker', 'network', 'rm', networkName].execute().waitFor()
        }
    }
}
