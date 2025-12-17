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

package com.kineticfire.gradle.docker.generator

/**
 * Standardized task naming utilities for the dockerProject and dockerWorkflows DSLs.
 *
 * This utility class provides consistent naming conventions for all generated tasks,
 * ensuring predictable and discoverable task names across the plugin.
 *
 * Naming Conventions:
 * - Docker build tasks: dockerBuild{ImageName}
 * - Compose tasks: composeUp{StackName}, composeDown{StackName}
 * - Pipeline tasks: {prefix}TagOnSuccess, {prefix}Save, {prefix}Publish{Target}
 * - Lifecycle tasks: run{Prefix}
 *
 * All names follow camelCase convention with the action verb first.
 */
final class TaskNamingUtils {

    private TaskNamingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Capitalize the first character of a string.
     * Empty or null strings return empty string.
     *
     * @param name The string to capitalize
     * @return The capitalized string
     */
    static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return ""
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1)
    }

    /**
     * Generate a Docker build task name.
     * Format: dockerBuild{ImageName}
     *
     * @param imageName The image name (will be capitalized)
     * @return The task name, e.g., "dockerBuildMyApp"
     */
    static String buildTaskName(String imageName) {
        return "dockerBuild${capitalize(imageName)}"
    }

    /**
     * Generate a Docker tag task name.
     * Format: dockerTag{ImageName}
     *
     * @param imageName The image name (will be capitalized)
     * @return The task name, e.g., "dockerTagMyApp"
     */
    static String tagTaskName(String imageName) {
        return "dockerTag${capitalize(imageName)}"
    }

    /**
     * Generate a compose up task name.
     * Format: composeUp{StackName}
     *
     * @param stackName The compose stack name (will be capitalized)
     * @return The task name, e.g., "composeUpMyStack"
     */
    static String composeUpTaskName(String stackName) {
        return "composeUp${capitalize(stackName)}"
    }

    /**
     * Generate a compose down task name.
     * Format: composeDown{StackName}
     *
     * @param stackName The compose stack name (will be capitalized)
     * @return The task name, e.g., "composeDownMyStack"
     */
    static String composeDownTaskName(String stackName) {
        return "composeDown${capitalize(stackName)}"
    }

    /**
     * Generate a tag-on-success task name.
     * Format: {prefix}TagOnSuccess
     *
     * @param prefix The task prefix (e.g., "dockerProject", "workflowMyPipeline")
     * @return The task name, e.g., "dockerProjectTagOnSuccess"
     */
    static String tagOnSuccessTaskName(String prefix) {
        return "${prefix}TagOnSuccess"
    }

    /**
     * Generate a save task name.
     * Format: {prefix}Save
     *
     * @param prefix The task prefix (e.g., "dockerProject", "workflowMyPipeline")
     * @return The task name, e.g., "dockerProjectSave"
     */
    static String saveTaskName(String prefix) {
        return "${prefix}Save"
    }

    /**
     * Generate a publish task name with target.
     * Format: {prefix}Publish{Target}
     *
     * @param prefix The task prefix (e.g., "dockerProject", "workflowMyPipeline")
     * @param target The publish target name (will be capitalized)
     * @return The task name, e.g., "dockerProjectPublishDockerhub"
     */
    static String publishTaskName(String prefix, String target) {
        return "${prefix}Publish${capitalize(target)}"
    }

    /**
     * Generate a simple publish task name without target.
     * Format: {prefix}Publish
     *
     * @param prefix The task prefix (e.g., "dockerProject", "workflowMyPipeline")
     * @return The task name, e.g., "dockerProjectPublish"
     */
    static String publishTaskName(String prefix) {
        return "${prefix}Publish"
    }

    /**
     * Generate a cleanup task name.
     * Format: {prefix}Cleanup
     *
     * @param prefix The task prefix (e.g., "dockerProject", "workflowMyPipeline")
     * @return The task name, e.g., "dockerProjectCleanup"
     */
    static String cleanupTaskName(String prefix) {
        return "${prefix}Cleanup"
    }

    /**
     * Generate a lifecycle task name.
     * Format: run{Prefix}
     *
     * @param prefix The task prefix (will be capitalized)
     * @return The task name, e.g., "runDockerProject"
     */
    static String lifecycleTaskName(String prefix) {
        return "run${capitalize(prefix)}"
    }

    /**
     * Generate a test configuration task name.
     * Format: {name}IntegrationTest
     *
     * @param name The test configuration name (e.g., "apiTests")
     * @return The task name, e.g., "apiTestsIntegrationTest"
     */
    static String testTaskName(String name) {
        return "${name}IntegrationTest"
    }

    /**
     * Generate a workflow prefix from a pipeline name.
     * Format: workflow{PipelineName}
     *
     * @param pipelineName The pipeline name (will be capitalized)
     * @return The workflow prefix, e.g., "workflowMyPipeline"
     */
    static String workflowPrefix(String pipelineName) {
        return "workflow${capitalize(pipelineName)}"
    }

    /**
     * Generate the default dockerProject task prefix.
     *
     * @return "dockerProject"
     */
    static String dockerProjectPrefix() {
        return "dockerProject"
    }

    /**
     * Normalize a name for use in task names.
     * Removes special characters and converts to camelCase.
     *
     * @param name The name to normalize
     * @return The normalized name
     */
    static String normalizeName(String name) {
        if (name == null || name.isEmpty()) {
            return ""
        }
        // Replace hyphens and underscores with spaces, then convert to camelCase
        def parts = name.replaceAll(/[-_]/, ' ').split(/\s+/)
        if (parts.length == 0) {
            return ""
        }
        // First part stays lowercase, rest get capitalized
        def result = new StringBuilder(parts[0].toLowerCase())
        for (int i = 1; i < parts.length; i++) {
            result.append(capitalize(parts[i].toLowerCase()))
        }
        return result.toString()
    }

    /**
     * Generate a state directory path for a pipeline.
     *
     * @param prefix The task prefix (e.g., "dockerProject", "workflowMyPipeline")
     * @return The state directory path relative to build directory
     */
    static String stateDirectory(String prefix) {
        return "${prefix}/state"
    }

    /**
     * Sanitize a name by converting to lowercase and removing non-alphanumeric characters.
     * This matches the convention used by DockerProjectTranslator for docker.images names.
     *
     * @param name The name to sanitize (e.g., 'project-scenario1-app')
     * @return The sanitized name (e.g., 'projectscenario1app')
     */
    static String sanitizeName(String name) {
        if (name == null || name.isEmpty()) {
            return ''
        }
        return name.toLowerCase().replaceAll('[^a-z0-9]', '')
    }
}
