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
 * Functional tests for PipelineRunTask
 *
 * These tests verify the PipelineRunTask orchestrates the pipeline workflow
 * correctly through Gradle's task infrastructure.
 */
class PipelineRunTaskFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-pipeline-run'"
    }

    def "PipelineRunTask validates required configuration"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.task.PipelineRunTask
            import org.gradle.api.GradleException

            task verifyValidation {
                doLast {
                    def pipelineTask = tasks.create('testPipeline', PipelineRunTask)

                    // Should fail without pipeline spec
                    try {
                        pipelineTask.validatePipelineSpec()
                        throw new AssertionError('Should have thrown exception')
                    } catch (GradleException e) {
                        assert e.message.contains('PipelineSpec must be configured')
                    }

                    println "VALIDATION_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyValidation')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('VALIDATION_VERIFIED')
        result.task(':verifyValidation').outcome == TaskOutcome.SUCCESS
    }

    def "PipelineRunTask has correct task properties"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.task.PipelineRunTask

            task verifyProperties {
                doLast {
                    def pipelineTask = tasks.create('testPipeline', PipelineRunTask)

                    assert pipelineTask.description == 'Executes a complete pipeline workflow'
                    assert pipelineTask.group == 'docker workflows'

                    println "PROPERTIES_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyProperties')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('PROPERTIES_VERIFIED')
        result.task(':verifyProperties').outcome == TaskOutcome.SUCCESS
    }

    def "PipelineRunTask skips build step when not configured"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.task.PipelineRunTask
            import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext

            task verifyBuildSkip {
                doLast {
                    def pipelineTask = tasks.create('testPipeline', PipelineRunTask)
                    def pipelineSpec = project.objects.newInstance(
                        PipelineSpec,
                        'test',
                        project.objects
                    )
                    def context = PipelineContext.create('test')

                    // Build step should be skipped (no image configured)
                    def result = pipelineTask.executeBuildStep(pipelineSpec, context)

                    // Context should be unchanged
                    assert result == context

                    println "BUILD_SKIP_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyBuildSkip')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('BUILD_SKIP_VERIFIED')
        result.task(':verifyBuildSkip').outcome == TaskOutcome.SUCCESS
    }

    def "PipelineRunTask skips test step when not configured"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.task.PipelineRunTask
            import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext

            task verifyTestSkip {
                doLast {
                    def pipelineTask = tasks.create('testPipeline', PipelineRunTask)
                    def pipelineSpec = project.objects.newInstance(
                        PipelineSpec,
                        'test',
                        project.objects
                    )
                    def context = PipelineContext.create('test')

                    // Test step should be skipped (no stack/testTask configured)
                    def result = pipelineTask.executeTestStep(pipelineSpec, context)

                    // Context should be unchanged
                    assert result == context

                    println "TEST_SKIP_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyTestSkip')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('TEST_SKIP_VERIFIED')
        result.task(':verifyTestSkip').outcome == TaskOutcome.SUCCESS
    }

    def "PipelineRunTask skips conditional step when no test results"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.task.PipelineRunTask
            import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext

            task verifyConditionalSkip {
                doLast {
                    def pipelineTask = tasks.create('testPipeline', PipelineRunTask)
                    def pipelineSpec = project.objects.newInstance(
                        PipelineSpec,
                        'test',
                        project.objects
                    )

                    // Context without test results
                    def context = PipelineContext.create('test')
                    assert !context.testCompleted

                    def result = pipelineTask.executeConditionalStep(pipelineSpec, context)

                    // Context should be unchanged
                    assert result == context

                    println "CONDITIONAL_SKIP_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyConditionalSkip')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('CONDITIONAL_SKIP_VERIFIED')
        result.task(':verifyConditionalSkip').outcome == TaskOutcome.SUCCESS
    }

    def "PipelineRunTask executes cleanup in always step"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.task.PipelineRunTask
            import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
            import com.kineticfire.gradle.docker.spec.workflow.AlwaysStepSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext

            task verifyAlwaysExecution {
                doLast {
                    def pipelineTask = tasks.create('testPipeline', PipelineRunTask)
                    def pipelineSpec = project.objects.newInstance(
                        PipelineSpec,
                        'test',
                        project.objects
                    )

                    // Configure always step
                    def alwaysSpec = project.objects.newInstance(AlwaysStepSpec, project.objects)
                    alwaysSpec.removeTestContainers.set(true)
                    alwaysSpec.cleanupImages.set(true)
                    pipelineSpec.always.set(alwaysSpec)

                    def context = PipelineContext.create('test')

                    // Should not throw
                    pipelineTask.executeAlwaysStep(pipelineSpec, context)

                    println "ALWAYS_EXECUTION_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyAlwaysExecution')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('ALWAYS_EXECUTION_VERIFIED')
        result.task(':verifyAlwaysExecution').outcome == TaskOutcome.SUCCESS
    }

    def "PipelineRunTask getSuccessSpec returns correct spec"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.task.PipelineRunTask
            import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
            import com.kineticfire.gradle.docker.spec.workflow.SuccessStepSpec

            task verifyGetSuccessSpec {
                doLast {
                    def pipelineTask = tasks.create('testPipeline', PipelineRunTask)
                    def pipelineSpec = project.objects.newInstance(
                        PipelineSpec,
                        'test',
                        project.objects
                    )

                    // Configure onTestSuccess
                    def successSpec = project.objects.newInstance(SuccessStepSpec, project.objects)
                    successSpec.additionalTags.set(['test-tag'])
                    pipelineSpec.onTestSuccess.set(successSpec)

                    def result = pipelineTask.getSuccessSpec(pipelineSpec)

                    assert result == successSpec
                    assert result.additionalTags.get().contains('test-tag')

                    println "GET_SUCCESS_SPEC_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyGetSuccessSpec')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('GET_SUCCESS_SPEC_VERIFIED')
        result.task(':verifyGetSuccessSpec').outcome == TaskOutcome.SUCCESS
    }

    def "PipelineRunTask getFailureSpec returns correct spec"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.task.PipelineRunTask
            import com.kineticfire.gradle.docker.spec.workflow.PipelineSpec
            import com.kineticfire.gradle.docker.spec.workflow.FailureStepSpec

            task verifyGetFailureSpec {
                doLast {
                    def pipelineTask = tasks.create('testPipeline', PipelineRunTask)
                    def pipelineSpec = project.objects.newInstance(
                        PipelineSpec,
                        'test',
                        project.objects
                    )

                    // Configure onTestFailure
                    def failureSpec = project.objects.newInstance(FailureStepSpec, project.objects)
                    failureSpec.additionalTags.set(['failure-tag'])
                    pipelineSpec.onTestFailure.set(failureSpec)

                    def result = pipelineTask.getFailureSpec(pipelineSpec)

                    assert result == failureSpec
                    assert result.additionalTags.get().contains('failure-tag')

                    println "GET_FAILURE_SPEC_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyGetFailureSpec')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('GET_FAILURE_SPEC_VERIFIED')
        result.task(':verifyGetFailureSpec').outcome == TaskOutcome.SUCCESS
    }

    private List<File> getPluginClasspath() {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .collect { new File(it) }
    }
}
