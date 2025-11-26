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

package com.kineticfire.gradle.docker

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for ConditionalExecutor workflow execution
 *
 * These tests verify the ConditionalExecutor can route to success or failure
 * paths based on test results through Gradle's task infrastructure.
 */
class ConditionalExecutorFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-conditional'"
    }

    def "ConditionalExecutor routes to success path when tests pass"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.ConditionalExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import com.kineticfire.gradle.docker.workflow.TestResult
            import org.gradle.api.Action

            task verifySuccessPath {
                doLast {
                    def executor = new ConditionalExecutor()
                    def context = PipelineContext.create('test-pipeline')

                    // Create passing test result
                    def testResult = TestResult.success(10, 10, 0)

                    // Create success spec with tags
                    def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
                    successSpec.additionalTags.set(['latest', 'stable'])

                    // Create failure spec (should not be used)
                    def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
                    failureSpec.additionalTags.set(['failed'])

                    // Execute conditional
                    def result = executor.executeConditional(testResult, successSpec, failureSpec, context)

                    // Verify success path was taken
                    assert result.appliedTags.contains('latest')
                    assert result.appliedTags.contains('stable')
                    assert !result.appliedTags.contains('failed')

                    println "SUCCESS_PATH_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifySuccessPath')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('SUCCESS_PATH_VERIFIED')
        result.task(':verifySuccessPath').outcome == TaskOutcome.SUCCESS
    }

    def "ConditionalExecutor routes to failure path when tests fail"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.ConditionalExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import com.kineticfire.gradle.docker.workflow.TestResult

            task verifyFailurePath {
                doLast {
                    def executor = new ConditionalExecutor()
                    def context = PipelineContext.create('test-pipeline')

                    // Create failing test result
                    def testResult = TestResult.failure(10, 8, 2, 0)

                    // Create success spec (should not be used)
                    def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
                    successSpec.additionalTags.set(['latest'])

                    // Create failure spec with tags
                    def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
                    failureSpec.additionalTags.set(['broken', 'needs-fix'])

                    // Execute conditional
                    def result = executor.executeConditional(testResult, successSpec, failureSpec, context)

                    // Verify failure path was taken
                    assert result.appliedTags.contains('broken')
                    assert result.appliedTags.contains('needs-fix')
                    assert !result.appliedTags.contains('latest')

                    println "FAILURE_PATH_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyFailurePath')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('FAILURE_PATH_VERIFIED')
        result.task(':verifyFailurePath').outcome == TaskOutcome.SUCCESS
    }

    def "ConditionalExecutor executes afterSuccess hook"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.ConditionalExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import com.kineticfire.gradle.docker.workflow.TestResult
            import org.gradle.api.Action

            task verifySuccessHook {
                doLast {
                    def executor = new ConditionalExecutor()
                    def context = PipelineContext.create('test-pipeline')
                    def testResult = TestResult.success(5, 5, 0)

                    def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
                    successSpec.afterSuccess.set({
                        println "AFTER_SUCCESS_HOOK_EXECUTED"
                    } as Action<Void>)

                    executor.executeConditional(testResult, successSpec, null, context)
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifySuccessHook')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('AFTER_SUCCESS_HOOK_EXECUTED')
        result.task(':verifySuccessHook').outcome == TaskOutcome.SUCCESS
    }

    def "ConditionalExecutor executes afterFailure hook"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.ConditionalExecutor
            import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import com.kineticfire.gradle.docker.workflow.TestResult
            import org.gradle.api.Action

            task verifyFailureHook {
                doLast {
                    def executor = new ConditionalExecutor()
                    def context = PipelineContext.create('test-pipeline')
                    def testResult = TestResult.failure(5, 3, 2, 0)

                    def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
                    failureSpec.afterFailure.set({
                        println "AFTER_FAILURE_HOOK_EXECUTED"
                    } as Action<Void>)

                    executor.executeConditional(testResult, null, failureSpec, context)
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyFailureHook')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('AFTER_FAILURE_HOOK_EXECUTED')
        result.task(':verifyFailureHook').outcome == TaskOutcome.SUCCESS
    }

    def "ConditionalExecutor handles null test result gracefully"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.ConditionalExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext

            task verifyNullHandling {
                doLast {
                    def executor = new ConditionalExecutor()
                    def context = PipelineContext.create('test-pipeline')
                        .withMetadata('key', 'value')

                    def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
                    def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)

                    // Execute with null test result
                    def result = executor.executeConditional(null, successSpec, failureSpec, context)

                    // Should return context unchanged
                    assert result.pipelineName == 'test-pipeline'
                    assert result.getMetadataValue('key') == 'value'
                    assert result.appliedTags.isEmpty()

                    println "NULL_HANDLING_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyNullHandling')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('NULL_HANDLING_VERIFIED')
        result.task(':verifyNullHandling').outcome == TaskOutcome.SUCCESS
    }

    def "ConditionalExecutor preserves pipeline context through execution"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.ConditionalExecutor
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import com.kineticfire.gradle.docker.workflow.TestResult

            task verifyContextPreservation {
                doLast {
                    def executor = new ConditionalExecutor()

                    // Create context with metadata
                    def context = PipelineContext.create('ci-pipeline')
                        .withMetadata('version', '1.0.0')
                        .withMetadata('environment', 'production')
                        .withMetadata('build-number', '42')

                    def testResult = TestResult.success(20, 20, 0)
                    def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
                    successSpec.additionalTags.set(['release'])

                    def result = executor.executeConditional(testResult, successSpec, null, context)

                    // Verify all metadata preserved
                    assert result.pipelineName == 'ci-pipeline'
                    assert result.getMetadataValue('version') == '1.0.0'
                    assert result.getMetadataValue('environment') == 'production'
                    assert result.getMetadataValue('build-number') == '42'
                    assert result.appliedTags.contains('release')

                    println "CONTEXT_PRESERVATION_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyContextPreservation')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('CONTEXT_PRESERVATION_VERIFIED')
        result.task(':verifyContextPreservation').outcome == TaskOutcome.SUCCESS
    }

    def "ConditionalExecutor handles null specs gracefully"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.ConditionalExecutor
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import com.kineticfire.gradle.docker.workflow.TestResult

            task verifyNullSpecs {
                doLast {
                    def executor = new ConditionalExecutor()
                    def context = PipelineContext.create('test')

                    // Test with passing result but null success spec
                    def passResult = TestResult.success(5, 5, 0)
                    def result1 = executor.executeConditional(passResult, null, null, context)
                    assert result1 == context

                    // Test with failing result but null failure spec
                    def failResult = TestResult.failure(5, 3, 2, 0)
                    def result2 = executor.executeConditional(failResult, null, null, context)
                    assert result2 == context

                    println "NULL_SPECS_HANDLED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyNullSpecs')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('NULL_SPECS_HANDLED')
        result.task(':verifyNullSpecs').outcome == TaskOutcome.SUCCESS
    }

    private List<File> getPluginClasspath() {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .collect { new File(it) }
    }
}
