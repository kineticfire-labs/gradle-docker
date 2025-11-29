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
 * Functional tests for plugin integration with dockerWorkflows DSL
 *
 * Tests the complete plugin integration: extension registration, task creation,
 * and validation of cross-DSL references.
 */
class WorkflowPluginIntegrationFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-workflow-integration'"
    }

    // ===== EXTENSION REGISTRATION TESTS =====

    def "plugin registers dockerWorkflows extension"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyExtension {
                doLast {
                    def ext = project.extensions.findByName('dockerWorkflows')
                    assert ext != null : "dockerWorkflows extension should be registered"
                    println "EXTENSION_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyExtension')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('EXTENSION_VERIFIED')
        result.task(':verifyExtension').outcome == TaskOutcome.SUCCESS
    }

    def "dockerWorkflows extension supports pipelines container"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
                pipelines {
                    testPipeline {
                        description.set('A test pipeline')
                    }
                }
            }

            task verifyPipeline {
                doLast {
                    def ext = project.extensions.findByName('dockerWorkflows')
                    assert ext.pipelines.size() == 1 : "Should have one pipeline"
                    assert ext.pipelines.findByName('testPipeline') != null : "Pipeline 'testPipeline' should exist"
                    println "PIPELINE_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyPipeline')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('PIPELINE_VERIFIED')
        result.task(':verifyPipeline').outcome == TaskOutcome.SUCCESS
    }

    // ===== TASK REGISTRATION TESTS =====

    def "plugin registers runPipelines aggregate task"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            task verifyTask {
                doLast {
                    def task = project.tasks.findByName('runPipelines')
                    assert task != null : "runPipelines task should be registered"
                    assert task.group == 'docker workflows' : "Task should be in 'docker workflows' group"
                    println "AGGREGATE_TASK_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyTask')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('AGGREGATE_TASK_VERIFIED')
        result.task(':verifyTask').outcome == TaskOutcome.SUCCESS
    }

    def "plugin registers pipeline tasks for each pipeline"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
                pipelines {
                    ciPipeline { }
                    cdPipeline { }
                }
            }

            task verifyTasks {
                doLast {
                    def ciTask = project.tasks.findByName('runCiPipeline')
                    def cdTask = project.tasks.findByName('runCdPipeline')
                    assert ciTask != null : "runCiPipeline task should be registered"
                    assert cdTask != null : "runCdPipeline task should be registered"
                    println "PIPELINE_TASKS_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyTasks')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('PIPELINE_TASKS_VERIFIED')
        result.task(':verifyTasks').outcome == TaskOutcome.SUCCESS
    }

    def "pipeline task has correct configuration"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
                pipelines {
                    myPipeline {
                        description.set('My test pipeline')
                    }
                }
            }

            task verifyConfig {
                doLast {
                    def task = project.tasks.findByName('runMyPipeline')
                    assert task.pipelineName.get() == 'myPipeline' : "Pipeline name should be 'myPipeline'"
                    assert task.pipelineSpec.isPresent() : "Pipeline spec should be present"
                    assert task.group == 'docker workflows' : "Task should be in 'docker workflows' group"
                    println "CONFIG_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyConfig')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('CONFIG_VERIFIED')
        result.task(':verifyConfig').outcome == TaskOutcome.SUCCESS
    }

    // ===== COMPLETE DSL TESTS =====

    def "complete DSL with docker, dockerOrch, and dockerWorkflows works"() {
        given:
        // Create a minimal docker context
        def contextDir = testProjectDir.resolve('docker-context').toFile()
        contextDir.mkdirs()
        new File(contextDir, 'Dockerfile') << 'FROM alpine:latest'

        // Create a compose file
        def composeFile = testProjectDir.resolve('compose.yml').toFile()
        composeFile << """
services:
  app:
    image: alpine:latest
"""

        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    myImage {
                        imageName.set('test-image')
                        context.set(file('docker-context'))
                        tags.set(['latest'])
                    }
                }
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('compose.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    ciPipeline {
                        description.set('CI Pipeline')
                        build {
                            image.set(docker.images.myImage)
                        }
                        test {
                            stack.set(dockerOrch.composeStacks.testStack)
                        }
                    }
                }
            }

            task verifyDsl {
                doLast {
                    def ext = project.extensions.findByName('dockerWorkflows')
                    def pipeline = ext.pipelines.findByName('ciPipeline')

                    assert pipeline != null : "Pipeline should exist"
                    assert pipeline.build.isPresent() : "Build step should be present"
                    assert pipeline.build.get().image.isPresent() : "Build image should be set"
                    assert pipeline.test.isPresent() : "Test step should be present"
                    assert pipeline.test.get().stack.isPresent() : "Test stack should be set"

                    println "DSL_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyDsl')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('DSL_VERIFIED')
        result.task(':verifyDsl').outcome == TaskOutcome.SUCCESS
    }

    // ===== VALIDATION TESTS =====

    def "validation passes for valid cross-DSL references"() {
        given:
        // Create a minimal docker context
        def contextDir = testProjectDir.resolve('docker-context').toFile()
        contextDir.mkdirs()
        new File(contextDir, 'Dockerfile') << 'FROM alpine:latest'

        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            docker {
                images {
                    validImage {
                        imageName.set('valid-image')
                        context.set(file('docker-context'))
                        tags.set(['latest'])
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    validPipeline {
                        build {
                            image.set(docker.images.validImage)
                        }
                    }
                }
            }

            task verify {
                doLast {
                    // If we got here, validation passed
                    println "VALIDATION_PASSED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verify')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('VALIDATION_PASSED')
        result.task(':verify').outcome == TaskOutcome.SUCCESS
    }

    // ===== TASKS LISTING TEST =====

    def "tasks command shows pipeline tasks in docker workflows group"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
                pipelines {
                    buildPipeline { }
                    deployPipeline { }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks', '--group=docker workflows')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('runPipelines')
        result.output.contains('runBuildPipeline')
        result.output.contains('runDeployPipeline')
    }

    // ===== DELEGATE STACK MANAGEMENT TESTS =====

    def "test step delegateStackManagement defaults to false"() {
        given:
        // Create a compose file
        def composeFile = testProjectDir.resolve('compose.yml').toFile()
        composeFile << """
services:
  app:
    image: alpine:latest
"""

        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('compose.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    myPipeline {
                        test {
                            stack.set(dockerOrch.composeStacks.testStack)
                        }
                    }
                }
            }

            task verifyDefault {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.findByName('myPipeline')
                    def testStep = pipeline.test.get()
                    assert testStep.delegateStackManagement.get() == false : "delegateStackManagement should default to false"
                    println "DELEGATE_DEFAULT_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyDefault')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('DELEGATE_DEFAULT_VERIFIED')
        result.task(':verifyDefault').outcome == TaskOutcome.SUCCESS
    }

    def "test step delegateStackManagement can be set to true"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
                pipelines {
                    delegatedPipeline {
                        test {
                            delegateStackManagement.set(true)
                        }
                    }
                }
            }

            task verifyDelegateTrue {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.findByName('delegatedPipeline')
                    def testStep = pipeline.test.get()
                    assert testStep.delegateStackManagement.get() == true : "delegateStackManagement should be true"
                    println "DELEGATE_TRUE_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyDelegateTrue')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('DELEGATE_TRUE_VERIFIED')
        result.task(':verifyDelegateTrue').outcome == TaskOutcome.SUCCESS
    }

    def "test step stack is optional when delegateStackManagement is true"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            // Create a dummy test task for validation
            task myIntegrationTest {
                doLast { println 'Integration test executed' }
            }

            dockerWorkflows {
                pipelines {
                    delegatedPipeline {
                        test {
                            delegateStackManagement.set(true)
                            testTaskName.set('myIntegrationTest')
                            // Note: stack is not set - should be valid when delegateStackManagement is true
                        }
                    }
                }
            }

            task verifyNoStack {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.findByName('delegatedPipeline')
                    def testStep = pipeline.test.get()
                    assert testStep.delegateStackManagement.get() == true : "delegateStackManagement should be true"
                    assert !testStep.stack.isPresent() : "stack should not be set"
                    assert testStep.testTaskName.get() == 'myIntegrationTest' : "testTaskName should be set"
                    println "NO_STACK_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyNoStack')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('NO_STACK_VERIFIED')
        result.task(':verifyNoStack').outcome == TaskOutcome.SUCCESS
    }

    def "complete DSL with delegateStackManagement and testIntegration pattern"() {
        given:
        // Create a compose file
        def composeFile = testProjectDir.resolve('compose.yml').toFile()
        composeFile << """
services:
  app:
    image: alpine:latest
"""

        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            dockerOrch {
                composeStacks {
                    appStack {
                        files.from('compose.yml')
                    }
                }
            }

            // Plugin auto-creates 'integrationTest' task, so we configure it
            // instead of creating a new one
            afterEvaluate {
                tasks.named('integrationTest') {
                    doLast { println 'Integration test executed' }
                }
            }

            dockerWorkflows {
                pipelines {
                    ciPipeline {
                        description.set('CI Pipeline with delegated lifecycle')
                        test {
                            delegateStackManagement.set(true)
                            testTaskName.set('integrationTest')
                            // stack is optional when delegating
                            timeoutMinutes.set(60)
                        }
                    }
                }
            }

            task verifyDelegatedDsl {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.findByName('ciPipeline')
                    def testStep = pipeline.test.get()

                    assert pipeline.description.get() == 'CI Pipeline with delegated lifecycle'
                    assert testStep.delegateStackManagement.get() == true
                    assert testStep.testTaskName.get() == 'integrationTest'
                    assert testStep.timeoutMinutes.get() == 60

                    println "DELEGATED_DSL_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyDelegatedDsl')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('DELEGATED_DSL_VERIFIED')
        result.task(':verifyDelegatedDsl').outcome == TaskOutcome.SUCCESS
    }

    // ===== EMPTY PIPELINES TEST =====

    def "plugin works with no pipelines configured"() {
        given:
        buildFile << """
            plugins {
                id 'com.kineticfire.gradle.docker'
            }

            dockerWorkflows {
                pipelines { }
            }

            task verify {
                doLast {
                    def ext = project.extensions.findByName('dockerWorkflows')
                    assert ext.pipelines.isEmpty() : "Pipelines should be empty"
                    println "EMPTY_VERIFIED"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verify')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('EMPTY_VERIFIED')
        result.task(':verify').outcome == TaskOutcome.SUCCESS
    }

    private List<File> getPluginClasspath() {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .collect { new File(it) }
    }
}
