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

package com.kineticfire.gradle.docker.workflow.validation

import com.kineticfire.gradle.docker.extension.DockerExtension
import com.kineticfire.gradle.docker.extension.DockerOrchExtension
import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Validator for pipeline specifications
 *
 * Validates cross-DSL references between dockerWorkflows, docker, and dockerOrch DSLs.
 * Ensures that referenced images, compose stacks, and test tasks exist.
 */
class PipelineValidator {

    private static final Logger LOGGER = Logging.getLogger(PipelineValidator)

    private final Project project
    private final DockerExtension dockerExtension
    private final DockerOrchExtension dockerOrchExtension

    PipelineValidator(Project project, DockerExtension dockerExtension, DockerOrchExtension dockerOrchExtension) {
        this.project = project
        this.dockerExtension = dockerExtension
        this.dockerOrchExtension = dockerOrchExtension
    }

    /**
     * Validate a pipeline specification
     *
     * @param pipelineSpec The pipeline specification to validate
     * @throws GradleException if validation fails
     */
    void validate(PipelineSpec pipelineSpec) {
        LOGGER.debug("Validating pipeline: {}", pipelineSpec.name)

        validateBuildStep(pipelineSpec)
        validateTestStep(pipelineSpec)

        LOGGER.debug("Pipeline {} validation passed", pipelineSpec.name)
    }

    /**
     * Validate the build step configuration
     */
    void validateBuildStep(PipelineSpec pipelineSpec) {
        def buildSpec = pipelineSpec.build.getOrNull()
        if (buildSpec == null) {
            LOGGER.debug("No build step configured for pipeline: {}", pipelineSpec.name)
            return
        }

        if (!isBuildStepConfigured(buildSpec)) {
            LOGGER.debug("Build step has no image configured for pipeline: {}", pipelineSpec.name)
            return
        }

        def imageSpec = buildSpec.image.get()
        validateImageReference(pipelineSpec.name, imageSpec.name)
    }

    /**
     * Check if the build step has an image configured
     */
    boolean isBuildStepConfigured(BuildStepSpec buildSpec) {
        return buildSpec.image.isPresent()
    }

    /**
     * Validate that the referenced image exists in docker.images
     */
    void validateImageReference(String pipelineName, String imageName) {
        if (dockerExtension == null) {
            throw new GradleException(
                "Pipeline '${pipelineName}' references image '${imageName}', " +
                "but docker extension is not available. " +
                "Ensure the plugin is applied correctly."
            )
        }

        def imageSpec = dockerExtension.images.findByName(imageName)
        if (imageSpec == null) {
            def availableImages = dockerExtension.images.names.join(', ')
            throw new GradleException(
                "Pipeline '${pipelineName}' references image '${imageName}', " +
                "but no such image is defined in docker.images. " +
                "Available images: [${availableImages}]\n" +
                "Suggestion: Add the image to docker.images { ${imageName} { ... } }"
            )
        }

        LOGGER.debug("Image reference '{}' validated for pipeline '{}'", imageName, pipelineName)
    }

    /**
     * Validate the test step configuration
     */
    void validateTestStep(PipelineSpec pipelineSpec) {
        def testSpec = pipelineSpec.test.getOrNull()
        if (testSpec == null) {
            LOGGER.debug("No test step configured for pipeline: {}", pipelineSpec.name)
            return
        }

        if (!isTestStepConfigured(testSpec)) {
            LOGGER.debug("Test step has no stack/testTask configured for pipeline: {}", pipelineSpec.name)
            return
        }

        validateStackReference(pipelineSpec.name, testSpec)
        validateTestTaskReference(pipelineName: pipelineSpec.name, testSpec: testSpec)
    }

    /**
     * Check if the test step has required configuration
     *
     * When delegateStackManagement is true, only testTaskName is required.
     * When delegateStackManagement is false (default), both stack and testTaskName are required.
     */
    boolean isTestStepConfigured(TestStepSpec testSpec) {
        def delegateStackManagement = testSpec.delegateStackManagement.getOrElse(false)

        if (delegateStackManagement) {
            // When delegating, only testTaskName is required
            return testSpec.testTaskName.isPresent()
        } else {
            // When managing lifecycle, both stack and testTaskName are required
            return testSpec.stack.isPresent() || testSpec.testTaskName.isPresent()
        }
    }

    /**
     * Validate that the referenced compose stack exists in dockerOrch.composeStacks
     *
     * When delegateStackManagement is true and stack is also set, logs a warning since
     * the stack property will be ignored (lifecycle managed by testIntegration).
     */
    void validateStackReference(String pipelineName, TestStepSpec testSpec) {
        def delegateStackManagement = testSpec.delegateStackManagement.getOrElse(false)

        if (!testSpec.stack.isPresent()) {
            // No stack configured - valid when delegateStackManagement is true
            if (!delegateStackManagement) {
                // Stack required when not delegating - but this check is handled in isTestStepConfigured
                return
            }
            return
        }

        def stackSpec = testSpec.stack.get()
        def stackName = stackSpec.name

        // Warn if delegateStackManagement is true but stack is also set
        if (delegateStackManagement) {
            LOGGER.warn(
                "Pipeline '{}' has delegateStackManagement=true but also sets stack='{}'. " +
                "The stack property will be ignored since testIntegration manages the compose lifecycle. " +
                "Consider removing the stack configuration to avoid confusion.",
                pipelineName, stackName
            )
            // Don't validate stack reference since it won't be used
            return
        }

        if (dockerOrchExtension == null) {
            throw new GradleException(
                "Pipeline '${pipelineName}' references compose stack '${stackName}', " +
                "but dockerOrch extension is not available. " +
                "Ensure the plugin is applied correctly."
            )
        }

        def foundStack = dockerOrchExtension.composeStacks.findByName(stackName)
        if (foundStack == null) {
            def availableStacks = dockerOrchExtension.composeStacks.names.join(', ')
            throw new GradleException(
                "Pipeline '${pipelineName}' references compose stack '${stackName}', " +
                "but no such stack is defined in dockerOrch.composeStacks. " +
                "Available stacks: [${availableStacks}]\n" +
                "Suggestion: Add the stack to dockerOrch.composeStacks { ${stackName} { ... } }"
            )
        }

        LOGGER.debug("Stack reference '{}' validated for pipeline '{}'", stackName, pipelineName)
    }

    /**
     * Validate that the referenced test task exists
     */
    void validateTestTaskReference(Map args) {
        def pipelineName = args.pipelineName
        def testSpec = args.testSpec

        if (!testSpec.testTaskName.isPresent()) {
            return
        }

        def taskName = testSpec.testTaskName.get()

        // Verify the task exists in the project
        def foundTask = project.tasks.findByName(taskName)
        if (foundTask == null) {
            throw new GradleException(
                "Pipeline '${pipelineName}' references test task '${taskName}', " +
                "but no such task exists in the project.\n" +
                "Suggestion: Ensure the test task is defined before configuring the pipeline"
            )
        }

        LOGGER.debug("Test task reference '{}' validated for pipeline '{}'", taskName, pipelineName)
    }

    /**
     * Validate all pipelines in the extension
     */
    void validateAll(Collection<PipelineSpec> pipelines) {
        def errors = []

        pipelines.each { pipelineSpec ->
            try {
                validate(pipelineSpec)
            } catch (GradleException e) {
                errors.add(e.message)
            }
        }

        if (!errors.isEmpty()) {
            throw new GradleException(
                "Pipeline validation failed:\n" +
                errors.collect { "  - ${it}" }.join('\n')
            )
        }
    }
}
