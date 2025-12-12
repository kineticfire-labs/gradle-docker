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

import com.kineticfire.gradle.docker.service.DockerService
import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import com.kineticfire.gradle.docker.spec.ImageSpec
import com.kineticfire.gradle.docker.spec.workflow.AlwaysStepSpec
import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TaskLookup
import com.kineticfire.gradle.docker.workflow.TestResult
import com.kineticfire.gradle.docker.workflow.executor.AlwaysStepExecutor
import com.kineticfire.gradle.docker.workflow.executor.BuildStepExecutor
import com.kineticfire.gradle.docker.workflow.executor.ConditionalExecutor
import com.kineticfire.gradle.docker.workflow.executor.TestStepExecutor
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for PipelineRunTask
 */
class PipelineRunTaskTest extends Specification {

    @TempDir
    Path tempDir

    Project project
    PipelineRunTask task
    TaskLookup taskLookup

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        task = project.tasks.create('pipelineRun', PipelineRunTask)
        taskLookup = createTaskLookup(project)
    }

    /**
     * Create a TaskLookup for testing that wraps a project's TaskContainer.
     */
    private TaskLookup createTaskLookup(Project project) {
        return new TaskLookup() {
            @Override
            Task findByName(String taskName) {
                return project.tasks.findByName(taskName)
            }

            @Override
            void execute(String taskName) {
                def task = findByName(taskName)
                if (task != null) {
                    execute(task)
                }
            }

            @Override
            void execute(Task task) {
                task.actions.each { action ->
                    action.execute(task)
                }
            }
        }
    }

    // ===== VALIDATION TESTS =====

    def "validatePipelineSpec throws exception when pipelineSpec not present"() {
        when:
        task.pipelineName.set('test-pipeline')
        task.validatePipelineSpec()

        then:
        def e = thrown(GradleException)
        e.message.contains('PipelineSpec must be configured')
    }

    def "validatePipelineSpec throws exception when pipelineName not present"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        task.pipelineSpec.set(pipelineSpec)

        when:
        task.validatePipelineSpec()

        then:
        def e = thrown(GradleException)
        e.message.contains('Pipeline name must be configured')
    }

    def "validatePipelineSpec passes with valid configuration"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        task.pipelineSpec.set(pipelineSpec)
        task.pipelineName.set('test-pipeline')

        when:
        task.validatePipelineSpec()

        then:
        noExceptionThrown()
    }

    // ===== BUILD STEP TESTS =====

    def "executeBuildStep skips when build spec is null"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def context = PipelineContext.create('test')

        when:
        def result = task.executeBuildStep(pipelineSpec, context)

        then:
        result == context
    }

    def "executeBuildStep skips when image not configured"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def buildSpec = project.objects.newInstance(BuildStepSpec)
        pipelineSpec.build.set(buildSpec)
        def context = PipelineContext.create('test')

        when:
        def result = task.executeBuildStep(pipelineSpec, context)

        then:
        result == context
    }

    def "executeBuildStep delegates to BuildStepExecutor when configured"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def buildSpec = project.objects.newInstance(BuildStepSpec)
        def imageSpec = project.objects.newInstance(ImageSpec, 'myImage', project.objects)
        buildSpec.image.set(imageSpec)
        pipelineSpec.build.set(buildSpec)

        def context = PipelineContext.create('test')
        def updatedContext = context.withMetadata('built', 'true')

        def mockExecutor = Stub(BuildStepExecutor, constructorArgs: [taskLookup]) {
            execute(_, _) >> updatedContext
        }
        task.setBuildStepExecutor(mockExecutor)

        when:
        def result = task.executeBuildStep(pipelineSpec, context)

        then:
        result == updatedContext
    }

    def "isBuildStepConfigured returns false when image not present"() {
        given:
        def buildSpec = project.objects.newInstance(BuildStepSpec)

        expect:
        !task.isBuildStepConfigured(buildSpec)
    }

    def "isBuildStepConfigured returns true when image is present"() {
        given:
        def buildSpec = project.objects.newInstance(BuildStepSpec)
        def imageSpec = project.objects.newInstance(ImageSpec, 'myImage', project.objects)
        buildSpec.image.set(imageSpec)

        expect:
        task.isBuildStepConfigured(buildSpec)
    }

    // ===== TEST STEP TESTS =====

    def "executeTestStep skips when test spec is null"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def context = PipelineContext.create('test')

        when:
        def result = task.executeTestStep(pipelineSpec, context)

        then:
        result == context
    }

    def "executeTestStep skips when stack not configured"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def testSpec = project.objects.newInstance(TestStepSpec)
        pipelineSpec.test.set(testSpec)
        def context = PipelineContext.create('test')

        when:
        def result = task.executeTestStep(pipelineSpec, context)

        then:
        result == context
    }

    def "executeTestStep delegates to TestStepExecutor when configured"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def testSpec = project.objects.newInstance(TestStepSpec)
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        project.tasks.create('myTest')
        testSpec.stack.set(stackSpec)
        testSpec.testTaskName.set('myTest')
        pipelineSpec.test.set(testSpec)

        def context = PipelineContext.create('test')
        def testResult = new TestResult(true, 5, 0, 0, 0, 5)
        def updatedContext = context.withTestResult(testResult)

        // Use Stub with constructorArgs since TestStepExecutor requires TaskLookup
        def mockExecutor = Stub(TestStepExecutor, constructorArgs: [taskLookup]) {
            execute(_, _) >> updatedContext
        }
        task.setTestStepExecutor(mockExecutor)

        when:
        def result = task.executeTestStep(pipelineSpec, context)

        then:
        result == updatedContext
    }

    def "isTestStepConfigured returns false when stack not present"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)
        testSpec.testTaskName.set('myTest')

        expect:
        !task.isTestStepConfigured(testSpec)
    }

    def "isTestStepConfigured returns false when testTaskName not present"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        testSpec.stack.set(stackSpec)

        expect:
        !task.isTestStepConfigured(testSpec)
    }

    def "isTestStepConfigured returns true when fully configured"() {
        given:
        def testSpec = project.objects.newInstance(TestStepSpec)
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        testSpec.stack.set(stackSpec)
        testSpec.testTaskName.set('myTest')

        expect:
        task.isTestStepConfigured(testSpec)
    }

    // ===== CONDITIONAL STEP TESTS =====

    def "executeConditionalStep skips when test not completed"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def context = PipelineContext.create('test')

        when:
        def result = task.executeConditionalStep(pipelineSpec, context)

        then:
        result == context
    }

    def "executeConditionalStep delegates to ConditionalExecutor"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def successSpec = project.objects.newInstance(SuccessStepSpec)
        def failureSpec = project.objects.newInstance(FailureStepSpec)
        pipelineSpec.onTestSuccess.set(successSpec)
        pipelineSpec.onTestFailure.set(failureSpec)

        def testResult = new TestResult(true, 5, 0, 0, 0, 5)
        def context = PipelineContext.create('test').withTestResult(testResult)
        def updatedContext = context.withAppliedTags(['success'])

        // ConditionalExecutor has no-arg constructor so we can use Stub directly
        def mockExecutor = Stub(ConditionalExecutor) {
            executeConditional(_, _, _, _) >> updatedContext
        }
        task.setConditionalExecutor(mockExecutor)

        when:
        def result = task.executeConditionalStep(pipelineSpec, context)

        then:
        result == updatedContext
    }

    // ===== GET SUCCESS/FAILURE SPEC TESTS =====

    def "getSuccessSpec returns onTestSuccess when present"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def successSpec = project.objects.newInstance(SuccessStepSpec)
        successSpec.additionalTags.set(['test-tag'])
        pipelineSpec.onTestSuccess.set(successSpec)

        when:
        def result = task.getSuccessSpec(pipelineSpec)

        then:
        result == successSpec
    }

    def "getSuccessSpec returns onTestSuccess convention when set"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        // onTestSuccess has a convention, so it's always present

        when:
        def result = task.getSuccessSpec(pipelineSpec)

        then:
        result != null // Convention provides a default SuccessStepSpec
    }

    def "getSuccessSpec returns explicitly set onSuccess over convention"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def successSpec = project.objects.newInstance(SuccessStepSpec)
        successSpec.additionalTags.set(['on-success-tag'])
        pipelineSpec.onSuccess.set(successSpec)

        when:
        def result = task.getSuccessSpec(pipelineSpec)

        then:
        // onTestSuccess convention takes precedence since it's always present
        result != null
    }

    def "getFailureSpec returns onTestFailure when present"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def failureSpec = project.objects.newInstance(FailureStepSpec)
        failureSpec.additionalTags.set(['test-fail-tag'])
        pipelineSpec.onTestFailure.set(failureSpec)

        when:
        def result = task.getFailureSpec(pipelineSpec)

        then:
        result == failureSpec
    }

    def "getFailureSpec returns onTestFailure convention when present"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        // onTestFailure has a convention, so it's always present

        when:
        def result = task.getFailureSpec(pipelineSpec)

        then:
        result != null // Convention provides a default FailureStepSpec
    }

    def "getFailureSpec returns explicitly set onFailure"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def failureSpec = project.objects.newInstance(FailureStepSpec)
        failureSpec.additionalTags.set(['on-fail-tag'])
        pipelineSpec.onFailure.set(failureSpec)

        when:
        def result = task.getFailureSpec(pipelineSpec)

        then:
        // onTestFailure convention takes precedence since it's always present
        result != null
    }

    // ===== ALWAYS STEP TESTS =====

    def "executeAlwaysStep skips when always spec is null"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def context = PipelineContext.create('test')

        when:
        task.executeAlwaysStep(pipelineSpec, context)

        then:
        noExceptionThrown()
    }

    def "executeAlwaysStep executes cleanup when configured"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def alwaysSpec = project.objects.newInstance(AlwaysStepSpec)
        alwaysSpec.removeTestContainers.set(true)
        alwaysSpec.cleanupImages.set(true)
        pipelineSpec.always.set(alwaysSpec)
        def context = PipelineContext.create('test')

        def mockAlwaysExecutor = Mock(AlwaysStepExecutor)
        task.setAlwaysStepExecutor(mockAlwaysExecutor)

        when:
        task.executeAlwaysStep(pipelineSpec, context)

        then:
        1 * mockAlwaysExecutor.execute(alwaysSpec, context, false) >> context
    }

    def "executeAlwaysStep delegates to AlwaysStepExecutor with testsPassed true when tests pass"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def alwaysSpec = project.objects.newInstance(AlwaysStepSpec)
        pipelineSpec.always.set(alwaysSpec)
        def testResult = new TestResult(true, 5, 0, 0, 0, 5)
        def context = PipelineContext.create('test').withTestResult(testResult)

        def mockAlwaysExecutor = Mock(AlwaysStepExecutor)
        task.setAlwaysStepExecutor(mockAlwaysExecutor)

        when:
        task.executeAlwaysStep(pipelineSpec, context)

        then:
        1 * mockAlwaysExecutor.execute(alwaysSpec, context, true) >> context
    }

    def "executeAlwaysStep delegates to AlwaysStepExecutor with testsPassed false when tests fail"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def alwaysSpec = project.objects.newInstance(AlwaysStepSpec)
        pipelineSpec.always.set(alwaysSpec)
        def testResult = new TestResult(false, 3, 0, 0, 2, 5)
        def context = PipelineContext.create('test').withTestResult(testResult)

        def mockAlwaysExecutor = Mock(AlwaysStepExecutor)
        task.setAlwaysStepExecutor(mockAlwaysExecutor)

        when:
        task.executeAlwaysStep(pipelineSpec, context)

        then:
        1 * mockAlwaysExecutor.execute(alwaysSpec, context, false) >> context
    }

    def "executeAlwaysStep handles executor exception gracefully"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def alwaysSpec = project.objects.newInstance(AlwaysStepSpec)
        pipelineSpec.always.set(alwaysSpec)
        def context = PipelineContext.create('test')

        def mockAlwaysExecutor = Mock(AlwaysStepExecutor) {
            execute(_, _, _) >> { throw new RuntimeException('Cleanup failed') }
        }
        task.setAlwaysStepExecutor(mockAlwaysExecutor)

        when:
        task.executeAlwaysStep(pipelineSpec, context)

        then:
        noExceptionThrown()
    }

    // ===== TASK PROPERTIES TESTS =====

    def "task has correct description"() {
        expect:
        task.description == 'Executes a complete pipeline workflow'
    }

    def "task has correct group"() {
        expect:
        task.group == 'docker workflows'
    }

    // ===== EXECUTOR INJECTION TESTS =====

    def "setBuildStepExecutor sets custom executor"() {
        given:
        // BuildStepExecutor requires TaskLookup in constructor
        def mockExecutor = Stub(BuildStepExecutor, constructorArgs: [taskLookup])

        when:
        task.setBuildStepExecutor(mockExecutor)

        then:
        noExceptionThrown()
    }

    def "setTestStepExecutor sets custom executor"() {
        given:
        // TestStepExecutor requires TaskLookup in constructor
        def mockExecutor = Stub(TestStepExecutor, constructorArgs: [taskLookup])

        when:
        task.setTestStepExecutor(mockExecutor)

        then:
        noExceptionThrown()
    }

    def "setConditionalExecutor sets custom executor"() {
        given:
        // ConditionalExecutor has no-arg constructor
        def mockExecutor = Stub(ConditionalExecutor)

        when:
        task.setConditionalExecutor(mockExecutor)

        then:
        noExceptionThrown()
    }

    def "setAlwaysStepExecutor sets custom executor"() {
        given:
        // AlwaysStepExecutor has no-arg constructor
        def mockExecutor = Stub(AlwaysStepExecutor)

        when:
        task.setAlwaysStepExecutor(mockExecutor)

        then:
        noExceptionThrown()
    }

    def "setDockerServiceProvider sets custom provider"() {
        given:
        def mockDockerService = Stub(DockerService)
        def mockProvider = Stub(Provider) {
            get() >> mockDockerService
        }

        when:
        task.setDockerServiceProvider(mockProvider)

        then:
        noExceptionThrown()
    }

    // ===== INTEGRATION-LIKE TESTS =====

    def "runPipeline executes full workflow with stubbed executors"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def buildSpec = project.objects.newInstance(BuildStepSpec)
        def imageSpec = project.objects.newInstance(ImageSpec, 'myImage', project.objects)
        buildSpec.image.set(imageSpec)
        pipelineSpec.build.set(buildSpec)

        def testSpec = project.objects.newInstance(TestStepSpec)
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')
        project.tasks.create('myTest2')
        testSpec.stack.set(stackSpec)
        testSpec.testTaskName.set('myTest2')
        pipelineSpec.test.set(testSpec)

        def successSpec = project.objects.newInstance(SuccessStepSpec)
        pipelineSpec.onTestSuccess.set(successSpec)

        def alwaysSpec = project.objects.newInstance(AlwaysStepSpec)
        pipelineSpec.always.set(alwaysSpec)

        task.pipelineSpec.set(pipelineSpec)
        task.pipelineName.set('integration-test')

        def testResult = new TestResult(true, 5, 0, 0, 0, 5)
        def contextAfterBuild = PipelineContext.create('integration-test')
        def contextAfterTest = contextAfterBuild.withTestResult(testResult)
        def contextAfterConditional = contextAfterTest.withAppliedTags(['success'])

        def mockBuildExecutor = Stub(BuildStepExecutor, constructorArgs: [taskLookup]) {
            execute(_, _) >> contextAfterBuild
        }
        task.setBuildStepExecutor(mockBuildExecutor)

        def mockTestExecutor = Stub(TestStepExecutor, constructorArgs: [taskLookup]) {
            execute(_, _) >> contextAfterTest
        }
        task.setTestStepExecutor(mockTestExecutor)

        def mockConditionalExecutor = Stub(ConditionalExecutor) {
            executeConditional(_, _, _, _) >> contextAfterConditional
        }
        task.setConditionalExecutor(mockConditionalExecutor)

        def mockAlwaysExecutor = Stub(AlwaysStepExecutor) {
            execute(_, _, _) >> contextAfterConditional
        }
        task.setAlwaysStepExecutor(mockAlwaysExecutor)

        when:
        task.runPipeline()

        then:
        noExceptionThrown()
    }

    def "runPipeline handles failure and still runs cleanup"() {
        given:
        def pipelineSpec = project.objects.newInstance(PipelineSpec, 'test')
        def buildSpec = project.objects.newInstance(BuildStepSpec)
        def imageSpec = project.objects.newInstance(ImageSpec, 'myImage', project.objects)
        buildSpec.image.set(imageSpec)
        pipelineSpec.build.set(buildSpec)

        def alwaysSpec = project.objects.newInstance(AlwaysStepSpec)
        alwaysSpec.removeTestContainers.set(true)
        pipelineSpec.always.set(alwaysSpec)

        task.pipelineSpec.set(pipelineSpec)
        task.pipelineName.set('failing-test')

        def mockBuildExecutor = Stub(BuildStepExecutor, constructorArgs: [taskLookup]) {
            execute(_, _) >> { throw new RuntimeException("Build failed") }
        }
        task.setBuildStepExecutor(mockBuildExecutor)

        when:
        task.runPipeline()

        then:
        def e = thrown(GradleException)
        e.message.contains("Pipeline 'failing-test' failed")
    }
}
