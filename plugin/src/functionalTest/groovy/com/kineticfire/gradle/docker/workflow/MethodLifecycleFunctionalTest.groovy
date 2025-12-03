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

package com.kineticfire.gradle.docker.workflow

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Functional tests for METHOD lifecycle support in dockerWorkflows DSL.
 *
 * These tests verify that:
 * - DSL parsing correctly handles lifecycle = WorkflowLifecycle.METHOD
 * - DSL parsing correctly handles lifecycle = WorkflowLifecycle.CLASS
 * - Default lifecycle is CLASS when not specified
 * - Enum values are type-checked at configuration time
 */
class MethodLifecycleFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile
    File composeFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        // Create a minimal compose file for tests that need it
        def composeDir = testProjectDir.resolve('compose').toFile()
        composeDir.mkdirs()
        composeFile = new File(composeDir, 'app.yml')
        composeFile << """
services:
  app:
    image: alpine:latest
    command: echo "test"
"""

        settingsFile << "rootProject.name = 'test-method-lifecycle'"
    }

    def "DSL parsing accepts lifecycle = WorkflowLifecycle.METHOD"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    ciPipeline {
                        description.set('CI pipeline with method lifecycle')

                        test {
                            stack = dockerOrch.composeStacks.testStack
                            testTaskName = 'integrationTest'
                            lifecycle = WorkflowLifecycle.METHOD
                        }
                    }
                }
            }

            task verifyLifecycle {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('ciPipeline')
                    def testSpec = pipeline.test.get()

                    assert testSpec.lifecycle.get() == WorkflowLifecycle.METHOD
                    println "METHOD lifecycle verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyLifecycle')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('METHOD lifecycle verified successfully!')
        result.task(':verifyLifecycle').outcome == TaskOutcome.SUCCESS
    }

    def "DSL parsing accepts lifecycle = WorkflowLifecycle.CLASS"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            dockerWorkflows {
                pipelines {
                    ciPipeline {
                        description.set('CI pipeline with class lifecycle')

                        test {
                            testTaskName = 'integrationTest'
                            lifecycle = WorkflowLifecycle.CLASS
                        }
                    }
                }
            }

            task verifyLifecycle {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('ciPipeline')
                    def testSpec = pipeline.test.get()

                    assert testSpec.lifecycle.get() == WorkflowLifecycle.CLASS
                    println "CLASS lifecycle verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyLifecycle')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('CLASS lifecycle verified successfully!')
        result.task(':verifyLifecycle').outcome == TaskOutcome.SUCCESS
    }

    def "default lifecycle is CLASS when not specified"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            dockerWorkflows {
                pipelines {
                    ciPipeline {
                        description.set('CI pipeline with default lifecycle')

                        test {
                            testTaskName = 'integrationTest'
                            // lifecycle not specified - should default to CLASS
                        }
                    }
                }
            }

            task verifyDefaultLifecycle {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('ciPipeline')
                    def testSpec = pipeline.test.get()

                    assert testSpec.lifecycle.get() == WorkflowLifecycle.CLASS
                    println "Default CLASS lifecycle verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyDefaultLifecycle')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Default CLASS lifecycle verified successfully!')
        result.task(':verifyDefaultLifecycle').outcome == TaskOutcome.SUCCESS
    }

    def "lifecycle enum values are type-checked"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            task verifyEnumValues {
                doLast {
                    // Verify enum exists and has expected values
                    def values = WorkflowLifecycle.values()
                    assert values.length == 2

                    // Verify CLASS and METHOD exist
                    assert WorkflowLifecycle.valueOf('CLASS') == WorkflowLifecycle.CLASS
                    assert WorkflowLifecycle.valueOf('METHOD') == WorkflowLifecycle.METHOD

                    // Verify ordinals are stable
                    assert WorkflowLifecycle.CLASS.ordinal() == 0
                    assert WorkflowLifecycle.METHOD.ordinal() == 1

                    println "Enum type checking verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyEnumValues')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Enum type checking verified successfully!')
        result.task(':verifyEnumValues').outcome == TaskOutcome.SUCCESS
    }

    def "lifecycle property can be updated after initial configuration"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    ciPipeline {
                        description.set('CI pipeline')

                        test {
                            stack = dockerOrch.composeStacks.testStack
                            testTaskName = 'integrationTest'
                            lifecycle = WorkflowLifecycle.CLASS
                        }
                    }
                }
            }

            // Update lifecycle after initial configuration
            dockerWorkflows.pipelines.named('ciPipeline') {
                test {
                    lifecycle = WorkflowLifecycle.METHOD
                }
            }

            task verifyLifecycleUpdate {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('ciPipeline')
                    def testSpec = pipeline.test.get()

                    assert testSpec.lifecycle.get() == WorkflowLifecycle.METHOD
                    println "Lifecycle update verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyLifecycleUpdate')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Lifecycle update verified successfully!')
        result.task(':verifyLifecycleUpdate').outcome == TaskOutcome.SUCCESS
    }

    def "invalid lifecycle string throws exception at configuration time"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            task verifyInvalidEnum {
                doLast {
                    try {
                        WorkflowLifecycle.valueOf('INVALID')
                        throw new AssertionError('Should have thrown exception for invalid enum value')
                    } catch (IllegalArgumentException e) {
                        assert e.message.contains('INVALID')
                        println "Invalid enum detection verified successfully!"
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyInvalidEnum')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Invalid enum detection verified successfully!')
        result.task(':verifyInvalidEnum').outcome == TaskOutcome.SUCCESS
    }

    def "METHOD lifecycle can be combined with other test step properties"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    ciPipeline {
                        description.set('CI pipeline with full test configuration')

                        test {
                            stack = dockerOrch.composeStacks.testStack
                            testTaskName = 'integrationTest'
                            lifecycle = WorkflowLifecycle.METHOD
                            timeoutMinutes = 45
                        }
                    }
                }
            }

            task verifyFullConfiguration {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('ciPipeline')
                    def testSpec = pipeline.test.get()

                    assert testSpec.lifecycle.get() == WorkflowLifecycle.METHOD
                    assert testSpec.testTaskName.get() == 'integrationTest'
                    assert testSpec.timeoutMinutes.get() == 45

                    println "Full test configuration verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyFullConfiguration')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Full test configuration verified successfully!')
        result.task(':verifyFullConfiguration').outcome == TaskOutcome.SUCCESS
    }

    def "lifecycle property is accessible via Gradle Provider API"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            dockerOrch {
                composeStacks {
                    testStack {
                        files.from('compose/app.yml')
                    }
                }
            }

            dockerWorkflows {
                pipelines {
                    ciPipeline {
                        test {
                            stack = dockerOrch.composeStacks.testStack
                            testTaskName = 'integrationTest'
                            lifecycle = WorkflowLifecycle.METHOD
                        }
                    }
                }
            }

            task verifyProviderApi {
                doLast {
                    def pipeline = dockerWorkflows.pipelines.getByName('ciPipeline')
                    def testSpec = pipeline.test.get()
                    def lifecycleProperty = testSpec.lifecycle

                    // Verify it's a Property
                    assert lifecycleProperty != null
                    assert lifecycleProperty.isPresent()

                    // Verify getOrElse works
                    assert lifecycleProperty.getOrElse(WorkflowLifecycle.CLASS) == WorkflowLifecycle.METHOD

                    // Verify get works
                    assert lifecycleProperty.get() == WorkflowLifecycle.METHOD

                    println "Provider API verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyProviderApi')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Provider API verified successfully!')
        result.task(':verifyProviderApi').outcome == TaskOutcome.SUCCESS
    }

    def "METHOD lifecycle without stack fails validation"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle

            dockerWorkflows {
                pipelines {
                    ciPipeline {
                        test {
                            testTaskName = 'integrationTest'
                            lifecycle = WorkflowLifecycle.METHOD
                            // Missing stack - should fail validation
                        }
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks')
            .withPluginClasspath(getPluginClasspath())
            .buildAndFail()

        then:
        result.output.contains('lifecycle=METHOD but no stack is configured')
        result.output.contains('Method lifecycle requires a stack')
    }

    private List<File> getPluginClasspath() {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .collect { new File(it) }
    }
}
