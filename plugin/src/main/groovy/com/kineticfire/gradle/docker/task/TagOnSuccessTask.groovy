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

package com.kineticfire.gradle.docker.task

import com.kineticfire.gradle.docker.generator.PipelineStateFile
import com.kineticfire.gradle.docker.service.DockerService
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Shared task for conditional tagging of Docker images based on test results.
 *
 * This task reads the test result from a state file and applies additional tags
 * to the image only if tests passed. It's used by both dockerProject and
 * dockerWorkflows DSLs for the onSuccess tagging workflow.
 *
 * Configuration Cache Compatible: Yes
 * - Uses flattened @Input properties only
 * - No nested object serialization
 * - No Project reference capture
 * - File-based state communication via @InputFile
 */
abstract class TagOnSuccessTask extends DefaultTask {

    TagOnSuccessTask() {
        // Set up default values for Provider API compatibility
        imageName.convention("")
        additionalTags.convention([])
        sourceImageRef.convention("")
    }

    /**
     * The Docker service for performing tag operations.
     * Marked @Internal as services should not be part of task inputs.
     */
    @Internal
    abstract Property<DockerService> getDockerService()

    /**
     * The base image name to tag.
     * This is the image that will receive the additional tags.
     */
    @Input
    abstract Property<String> getImageName()

    /**
     * The list of additional tags to apply to the image.
     * These tags are applied only if tests passed.
     */
    @Input
    abstract ListProperty<String> getAdditionalTags()

    /**
     * The source image reference (including tag) to tag from.
     * If not set, defaults to imageName:latest.
     */
    @Input
    @Optional
    abstract Property<String> getSourceImageRef()

    /**
     * The test result file to check.
     * The task will only execute if this file exists and indicates success.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getTestResultFile()

    @TaskAction
    void tagImage() {
        def service = dockerService.get()
        if (service == null) {
            throw new IllegalStateException("dockerService must be provided")
        }

        def resultFile = testResultFile.get().asFile
        if (!resultFile.exists()) {
            logger.lifecycle("Test result file not found, skipping tag operation: {}", resultFile.absolutePath)
            return
        }

        // Read and check test result
        def testResult = PipelineStateFile.readTestResult(resultFile)
        if (!testResult.success) {
            logger.lifecycle("Tests did not pass, skipping additional tags")
            return
        }

        def imageNameValue = imageName.get()
        if (imageNameValue.isEmpty()) {
            throw new IllegalStateException("imageName must be specified")
        }

        def tagsToApply = additionalTags.get()
        if (tagsToApply.isEmpty()) {
            logger.lifecycle("No additional tags specified, skipping tag operation")
            return
        }

        // Determine source image reference
        def sourceRef = sourceImageRef.getOrElse("")
        if (sourceRef.isEmpty()) {
            sourceRef = "${imageNameValue}:latest"
        }

        // Build full tag references
        def fullTagRefs = tagsToApply.collect { tag ->
            if (tag.contains(':')) {
                return tag
            }
            return "${imageNameValue}:${tag}"
        }

        logger.lifecycle("Applying additional tags to {}: {}", sourceRef, fullTagRefs)

        // Apply tags
        def future = service.tagImage(sourceRef, fullTagRefs)
        future.get()

        logger.lifecycle("Successfully applied {} additional tag(s) to image", fullTagRefs.size())
    }
}
