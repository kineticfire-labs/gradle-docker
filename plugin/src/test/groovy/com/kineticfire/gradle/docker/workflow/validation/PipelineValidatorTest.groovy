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
import com.kineticfire.gradle.docker.extension.DockerTestExtension
import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test as GradleTestTask
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for PipelineValidator
 */
class PipelineValidatorTest extends Specification {

    @TempDir
    Path tempDir

    Project project
    DockerExtension dockerExtension
    DockerTestExtension dockerTestExtension
    PipelineValidator validator

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        dockerExtension = project.objects.newInstance(DockerExtension, project.objects, project.providers, project.layout)
        dockerTestExtension = project.objects.newInstance(DockerTestExtension)
        validator = new PipelineValidator(project, dockerExtension, dockerTestExtension)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor accepts all parameters"() {
        when:
        def v = new PipelineValidator(project, dockerExtension, dockerTestExtension)

        then:
        v != null
    }

    // ===== VALIDATE TESTS =====

    def "validate passes for empty pipeline"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'empty')

        when:
        validator.validate(pipelineSpec)

        then:
        noExceptionThrown()
    }

    def "validate passes when pipeline has no build step configured"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'noBuild')

        when:
        validator.validate(pipelineSpec)

        then:
        noExceptionThrown()
    }

    def "validate passes when pipeline has build step with valid image reference"() {
        given:
        def imageSpec = createImageSpec('myImage')
        dockerExtension.images.add(imageSpec)

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'validBuild')
        pipelineSpec.build.get().image.set(imageSpec)

        when:
        validator.validate(pipelineSpec)

        then:
        noExceptionThrown()
    }

    def "validate fails when pipeline references non-existent image"() {
        given:
        def imageSpec = createImageSpec('missingImage')
        // NOT adding to dockerExtension.images

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'badImage')
        pipelineSpec.build.get().image.set(imageSpec)

        when:
        validator.validate(pipelineSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains("references image 'missingImage'")
        e.message.contains("no such image is defined")
    }

    def "validate error message includes available images"() {
        given:
        def existingImage = createImageSpec('existingImage')
        dockerExtension.images.add(existingImage)

        def missingImage = createImageSpec('missingImage')
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'badRef')
        pipelineSpec.build.get().image.set(missingImage)

        when:
        validator.validate(pipelineSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains("existingImage")
    }

    // ===== VALIDATE BUILD STEP TESTS =====

    def "validateBuildStep passes when build spec is null"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'noBuild')

        when:
        validator.validateBuildStep(pipelineSpec)

        then:
        noExceptionThrown()
    }

    def "validateBuildStep passes when build spec has no image"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'noImage')
        // build spec exists but has no image set

        when:
        validator.validateBuildStep(pipelineSpec)

        then:
        noExceptionThrown()
    }

    def "isBuildStepConfigured returns false when image not present"() {
        given:
        def buildSpec = project.objects.newInstance(BuildStepSpec)

        expect:
        !validator.isBuildStepConfigured(buildSpec)
    }

    def "isBuildStepConfigured returns true when image present"() {
        given:
        def buildSpec = project.objects.newInstance(BuildStepSpec)
        def imageSpec = createImageSpec('testImage')
        buildSpec.image.set(imageSpec)

        expect:
        validator.isBuildStepConfigured(buildSpec)
    }

    // ===== VALIDATE IMAGE REFERENCE TESTS =====

    def "validateImageReference passes for existing image"() {
        given:
        def imageSpec = createImageSpec('validImage')
        dockerExtension.images.add(imageSpec)

        when:
        validator.validateImageReference('testPipeline', 'validImage')

        then:
        noExceptionThrown()
    }

    def "validateImageReference fails for missing image"() {
        when:
        validator.validateImageReference('testPipeline', 'nonExistent')

        then:
        def e = thrown(GradleException)
        e.message.contains("nonExistent")
        e.message.contains("testPipeline")
    }

    def "validateImageReference fails when docker extension is null"() {
        given:
        def validatorWithNull = new PipelineValidator(project, null, dockerTestExtension)

        when:
        validatorWithNull.validateImageReference('testPipeline', 'anyImage')

        then:
        def e = thrown(GradleException)
        e.message.contains("docker extension is not available")
    }

    // ===== VALIDATE TEST STEP TESTS =====

    def "validateTestStep passes when test spec is null"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'noTest')

        when:
        validator.validateTestStep(pipelineSpec)

        then:
        noExceptionThrown()
    }

    def "validateTestStep passes when test spec has no stack or testTaskName"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'emptyTest')
        // test spec exists but has no stack or testTaskName set

        when:
        validator.validateTestStep(pipelineSpec)

        then:
        noExceptionThrown()
    }

    def "isTestStepConfigured returns false when neither stack nor testTaskName present"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)

        expect:
        !validator.isTestStepConfigured(testSpec)
    }

    def "isTestStepConfigured returns true when stack present"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        testSpec.stack.set(stackSpec)

        expect:
        validator.isTestStepConfigured(testSpec)
    }

    def "isTestStepConfigured returns true when testTaskName present"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.testTaskName.set('myTest')

        expect:
        validator.isTestStepConfigured(testSpec)
    }

    def "isTestStepConfigured returns true when delegateStackManagement true and testTaskName present"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.delegateStackManagement.set(true)
        testSpec.testTaskName.set('integrationTest')

        expect:
        validator.isTestStepConfigured(testSpec)
    }

    def "isTestStepConfigured returns false when delegateStackManagement true and no testTaskName"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.delegateStackManagement.set(true)
        // No testTaskName set

        expect:
        !validator.isTestStepConfigured(testSpec)
    }

    def "isTestStepConfigured returns false when delegateStackManagement true with only stack"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.delegateStackManagement.set(true)
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        testSpec.stack.set(stackSpec)
        // No testTaskName set - this is insufficient when delegating

        expect:
        !validator.isTestStepConfigured(testSpec)
    }

    // ===== VALIDATE STACK REFERENCE TESTS =====

    def "validateStackReference passes when stack not configured"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)

        when:
        validator.validateStackReference('testPipeline', testSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackReference passes for existing stack"() {
        given:
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'validStack')
        dockerTestExtension.composeStacks.add(stackSpec)

        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.stack.set(stackSpec)

        when:
        validator.validateStackReference('testPipeline', testSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackReference fails for missing stack"() {
        given:
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'missingStack')
        // NOT adding to dockerTestExtension.composeStacks

        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.stack.set(stackSpec)

        when:
        validator.validateStackReference('testPipeline', testSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains("missingStack")
        e.message.contains("testPipeline")
    }

    def "validateStackReference fails when dockerTest extension is null"() {
        given:
        def validatorWithNull = new PipelineValidator(project, dockerExtension, null)
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'anyStack')
        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.stack.set(stackSpec)

        when:
        validatorWithNull.validateStackReference('testPipeline', testSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains("dockerTest extension is not available")
    }

    def "validateStackReference passes when delegateStackManagement true and no stack"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.delegateStackManagement.set(true)
        testSpec.testTaskName.set('integrationTest')
        // No stack - this is valid when delegating

        when:
        validator.validateStackReference('testPipeline', testSpec)

        then:
        noExceptionThrown()
    }

    def "validateStackReference passes when delegateStackManagement true and stack is set"() {
        given:
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'ignoredStack')
        dockerTestExtension.composeStacks.add(stackSpec)

        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.delegateStackManagement.set(true)
        testSpec.stack.set(stackSpec)

        when:
        validator.validateStackReference('testPipeline', testSpec)

        then:
        // Passes but logs a warning - we cannot easily capture log output in unit tests
        // The important thing is it doesn't throw an exception
        noExceptionThrown()
    }

    def "validateStackReference skips stack validation when delegateStackManagement true"() {
        given:
        // Stack exists but is NOT added to dockerTestExtension - normally this would fail
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'nonExistentStack')

        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.delegateStackManagement.set(true)
        testSpec.stack.set(stackSpec)

        when:
        validator.validateStackReference('testPipeline', testSpec)

        then:
        // When delegating, stack reference is not validated (only warning is logged)
        noExceptionThrown()
    }

    // ===== VALIDATE TEST TASK REFERENCE TESTS =====

    def "validateTestTaskReference passes when testTaskName not configured"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)

        when:
        validator.validateTestTaskReference(pipelineName: 'testPipeline', testSpec: testSpec)

        then:
        noExceptionThrown()
    }

    def "validateTestTaskReference passes for existing task"() {
        given:
        project.tasks.register('existingTask').get()
        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.testTaskName.set('existingTask')

        when:
        validator.validateTestTaskReference(pipelineName: 'testPipeline', testSpec: testSpec)

        then:
        noExceptionThrown()
    }

    // ===== VALIDATE ALL TESTS =====

    def "validateAll passes for empty collection"() {
        when:
        validator.validateAll([])

        then:
        noExceptionThrown()
    }

    def "validateAll passes for multiple valid pipelines"() {
        given:
        def image1 = createImageSpec('image1')
        def image2 = createImageSpec('image2')
        dockerExtension.images.add(image1)
        dockerExtension.images.add(image2)

        def pipeline1 = project.objects.newInstance(PipelineSpec, 'pipeline1')
        pipeline1.build.get().image.set(image1)

        def pipeline2 = project.objects.newInstance(PipelineSpec, 'pipeline2')
        pipeline2.build.get().image.set(image2)

        when:
        validator.validateAll([pipeline1, pipeline2])

        then:
        noExceptionThrown()
    }

    def "validateAll collects all errors"() {
        given:
        def missing1 = createImageSpec('missing1')
        def missing2 = createImageSpec('missing2')

        def pipeline1 = project.objects.newInstance(PipelineSpec, 'pipeline1')
        pipeline1.build.get().image.set(missing1)

        def pipeline2 = project.objects.newInstance(PipelineSpec, 'pipeline2')
        pipeline2.build.get().image.set(missing2)

        when:
        validator.validateAll([pipeline1, pipeline2])

        then:
        def e = thrown(GradleException)
        e.message.contains("missing1")
        e.message.contains("missing2")
    }

    // ===== METHOD LIFECYCLE VALIDATION TESTS =====

    def "validateMethodLifecycleConfiguration fails when no stack configured"() {
        given:
        project.tasks.create('integrationTest', GradleTestTask) {
            maxParallelForks = 1
        }

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'methodPipeline')
        def testSpec = pipelineSpec.test.get()
        testSpec.testTaskName.set('integrationTest')
        testSpec.lifecycle.set(WorkflowLifecycle.METHOD)
        // No stack configured

        when:
        validator.validateMethodLifecycleConfiguration(pipelineSpec, testSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains("lifecycle=METHOD but no stack is configured")
        e.message.contains("Add: stack = dockerTest.composeStacks")
    }

    def "validateMethodLifecycleConfiguration passes with valid configuration"() {
        given:
        project.tasks.create('integrationTest', GradleTestTask) {
            maxParallelForks = 1
        }

        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        dockerTestExtension.composeStacks.add(stackSpec)

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'methodPipeline')
        def testSpec = pipelineSpec.test.get()
        testSpec.testTaskName.set('integrationTest')
        testSpec.stack.set(stackSpec)
        testSpec.lifecycle.set(WorkflowLifecycle.METHOD)

        when:
        validator.validateMethodLifecycleConfiguration(pipelineSpec, testSpec)

        then:
        noExceptionThrown()
    }

    def "validateMethodLifecycleConfiguration passes with delegateStackManagement true and no stack"() {
        given:
        project.tasks.create('integrationTest', GradleTestTask) {
            maxParallelForks = 1
        }

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'delegatedMethodPipeline')
        def testSpec = pipelineSpec.test.get()
        testSpec.testTaskName.set('integrationTest')
        testSpec.delegateStackManagement.set(true)
        testSpec.lifecycle.set(WorkflowLifecycle.METHOD)
        // No stack configured - valid when delegateStackManagement=true

        when:
        validator.validateMethodLifecycleConfiguration(pipelineSpec, testSpec)

        then:
        noExceptionThrown()
    }

    def "validateSequentialTestExecution fails when maxParallelForks greater than 1"() {
        given:
        project.tasks.create('parallelTest', GradleTestTask) {
            maxParallelForks = 4
        }

        when:
        validator.validateSequentialTestExecution('testPipeline', 'parallelTest')

        then:
        def e = thrown(GradleException)
        e.message.contains("lifecycle=METHOD")
        e.message.contains("maxParallelForks=4")
        e.message.contains("sequential test execution")
        e.message.contains("port conflicts")
    }

    def "validateSequentialTestExecution passes when maxParallelForks is 1"() {
        given:
        project.tasks.create('sequentialTest', GradleTestTask) {
            maxParallelForks = 1
        }

        when:
        validator.validateSequentialTestExecution('testPipeline', 'sequentialTest')

        then:
        noExceptionThrown()
    }

    def "validateSequentialTestExecution passes when task not found"() {
        when:
        validator.validateSequentialTestExecution('testPipeline', 'nonExistentTask')

        then:
        // Task not found is handled by validateTestTaskReference, not here
        noExceptionThrown()
    }

    def "validateSequentialTestExecution passes for non-Test task"() {
        given:
        project.tasks.create('regularTask')

        when:
        validator.validateSequentialTestExecution('testPipeline', 'regularTask')

        then:
        // Warning is logged but no exception
        noExceptionThrown()
    }

    def "validateSequentialTestExecution error message includes fix suggestions"() {
        given:
        project.tasks.create('parallelTest', GradleTestTask) {
            maxParallelForks = 2
        }

        when:
        validator.validateSequentialTestExecution('myPipeline', 'parallelTest')

        then:
        def e = thrown(GradleException)
        e.message.contains("Set maxParallelForks=1")
        e.message.contains("Use lifecycle=CLASS")
        e.message.contains("tasks.named('parallelTest')")
    }

    def "validateTestStep validates METHOD lifecycle when configured"() {
        given:
        // Create parallel test task - should fail validation
        project.tasks.create('integrationTest', GradleTestTask) {
            maxParallelForks = 4
        }

        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        dockerTestExtension.composeStacks.add(stackSpec)

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'methodPipeline')
        def testSpec = pipelineSpec.test.get()
        testSpec.testTaskName.set('integrationTest')
        testSpec.stack.set(stackSpec)
        testSpec.lifecycle.set(WorkflowLifecycle.METHOD)

        when:
        validator.validateTestStep(pipelineSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains("maxParallelForks=4")
    }

    def "validateTestStep does not validate METHOD lifecycle when CLASS"() {
        given:
        // Create parallel test task - should be allowed with CLASS lifecycle
        project.tasks.create('integrationTest', GradleTestTask) {
            maxParallelForks = 4
        }

        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        dockerTestExtension.composeStacks.add(stackSpec)

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'classPipeline')
        def testSpec = pipelineSpec.test.get()
        testSpec.testTaskName.set('integrationTest')
        testSpec.stack.set(stackSpec)
        testSpec.lifecycle.set(WorkflowLifecycle.CLASS)

        when:
        validator.validateTestStep(pipelineSpec)

        then:
        noExceptionThrown()
    }

    def "validateTestStep does not validate METHOD lifecycle when default"() {
        given:
        // Create parallel test task - should be allowed with default lifecycle
        project.tasks.create('integrationTest', GradleTestTask) {
            maxParallelForks = 4
        }

        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        dockerTestExtension.composeStacks.add(stackSpec)

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'defaultPipeline')
        def testSpec = pipelineSpec.test.get()
        testSpec.testTaskName.set('integrationTest')
        testSpec.stack.set(stackSpec)
        // lifecycle not set - defaults to CLASS

        when:
        validator.validateTestStep(pipelineSpec)

        then:
        noExceptionThrown()
    }

    def "validate fails for METHOD lifecycle with missing stack"() {
        given:
        project.tasks.create('integrationTest', GradleTestTask) {
            maxParallelForks = 1
        }

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'badMethodPipeline')
        def testSpec = pipelineSpec.test.get()
        testSpec.testTaskName.set('integrationTest')
        testSpec.lifecycle.set(WorkflowLifecycle.METHOD)
        // No stack configured

        when:
        validator.validate(pipelineSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains("lifecycle=METHOD but no stack is configured")
    }

    def "validate fails for METHOD lifecycle with parallel test task"() {
        given:
        project.tasks.create('integrationTest', GradleTestTask) {
            maxParallelForks = 8
        }

        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        dockerTestExtension.composeStacks.add(stackSpec)

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'badMethodPipeline')
        def testSpec = pipelineSpec.test.get()
        testSpec.testTaskName.set('integrationTest')
        testSpec.stack.set(stackSpec)
        testSpec.lifecycle.set(WorkflowLifecycle.METHOD)

        when:
        validator.validate(pipelineSpec)

        then:
        def e = thrown(GradleException)
        e.message.contains("maxParallelForks=8")
    }

    def "validate passes for METHOD lifecycle with valid configuration"() {
        given:
        project.tasks.create('integrationTest', GradleTestTask) {
            maxParallelForks = 1
        }

        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        dockerTestExtension.composeStacks.add(stackSpec)

        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'validMethodPipeline')
        def testSpec = pipelineSpec.test.get()
        testSpec.testTaskName.set('integrationTest')
        testSpec.stack.set(stackSpec)
        testSpec.lifecycle.set(WorkflowLifecycle.METHOD)

        when:
        validator.validate(pipelineSpec)

        then:
        noExceptionThrown()
    }

    // ===== HELPER METHODS =====

    private ImageSpec createImageSpec(String name) {
        return project.objects.newInstance(
            ImageSpec,
            name,
            project.objects,
            project.providers,
            project.layout
        )
    }
}
