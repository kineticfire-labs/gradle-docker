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
 * Functional tests for BuildStepExecutor workflow execution
 *
 * These tests verify the BuildStepExecutor can orchestrate build step execution
 * through Gradle's task infrastructure.
 */
class BuildStepExecutorFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-build-step'"
    }

    def "BuildStepExecutor computes correct task names"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.BuildStepExecutor
            import com.kineticfire.gradle.docker.workflow.TaskLookupFactory

            task verifyTaskNames {
                doLast {
                    def taskLookup = TaskLookupFactory.fromTaskContainer(project.tasks)
                    def executor = new BuildStepExecutor(taskLookup)

                    assert executor.computeBuildTaskName('myApp') == 'dockerBuildMyApp'
                    assert executor.computeBuildTaskName('testImage') == 'dockerBuildTestImage'
                    assert executor.computeBuildTaskName('api') == 'dockerBuildApi'

                    println "Task name computation verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyTaskNames')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Task name computation verified successfully!')
        result.task(':verifyTaskNames').outcome == TaskOutcome.SUCCESS
    }

    def "BuildStepExecutor validates BuildStepSpec correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.BuildStepExecutor
            import com.kineticfire.gradle.docker.workflow.TaskLookupFactory
            import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
            import org.gradle.api.GradleException

            task verifyValidation {
                doLast {
                    def taskLookup = TaskLookupFactory.fromTaskContainer(project.tasks)
                    def executor = new BuildStepExecutor(taskLookup)

                    // Test null spec
                    try {
                        executor.validateBuildSpec(null)
                        throw new AssertionError('Should have thrown exception for null spec')
                    } catch (GradleException e) {
                        assert e.message.contains('cannot be null')
                    }

                    // Test spec without image
                    def emptySpec = project.objects.newInstance(BuildStepSpec)
                    try {
                        executor.validateBuildSpec(emptySpec)
                        throw new AssertionError('Should have thrown exception for missing image')
                    } catch (GradleException e) {
                        assert e.message.contains('image must be configured')
                    }

                    println "Validation verified successfully!"
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
        result.output.contains('Validation verified successfully!')
        result.task(':verifyValidation').outcome == TaskOutcome.SUCCESS
    }

    def "BuildStepExecutor executes beforeBuild and afterBuild hooks"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.BuildStepExecutor
            import com.kineticfire.gradle.docker.workflow.TaskLookupFactory
            import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
            import com.kineticfire.gradle.docker.spec.ImageSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import org.gradle.api.Action

            // Create a mock build task
            task dockerBuildTestApp {
                doLast {
                    println "MOCK_BUILD_EXECUTED"
                }
            }

            task verifyHooks {
                doLast {
                    def taskLookup = TaskLookupFactory.fromTaskContainer(project.tasks)
                    def executor = new BuildStepExecutor(taskLookup)

                    // Create ImageSpec
                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'testApp',
                        project.objects,
                        project.providers,
                        project.layout
                    )

                    // Create BuildStepSpec with hooks - must cast closures to Action<Void>
                    def buildSpec = project.objects.newInstance(BuildStepSpec)
                    buildSpec.image.set(imageSpec)
                    buildSpec.beforeBuild.set({ println "BEFORE_BUILD_HOOK" } as Action<Void>)
                    buildSpec.afterBuild.set({ println "AFTER_BUILD_HOOK" } as Action<Void>)

                    // Execute
                    def context = PipelineContext.create('test')
                    def result = executor.execute(buildSpec, context)

                    // Verify context updated
                    assert result.buildCompleted
                    assert result.builtImage == imageSpec

                    println "Hooks verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyHooks')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('BEFORE_BUILD_HOOK')
        result.output.contains('MOCK_BUILD_EXECUTED')
        result.output.contains('AFTER_BUILD_HOOK')
        result.output.contains('Hooks verified successfully!')
        result.task(':verifyHooks').outcome == TaskOutcome.SUCCESS
    }

    def "BuildStepExecutor updates PipelineContext with built image"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.BuildStepExecutor
            import com.kineticfire.gradle.docker.workflow.TaskLookupFactory
            import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
            import com.kineticfire.gradle.docker.spec.ImageSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext

            // Create a mock build task
            task dockerBuildMyService {
                doLast {
                    println "Building myService..."
                }
            }

            task verifyContextUpdate {
                doLast {
                    def taskLookup = TaskLookupFactory.fromTaskContainer(project.tasks)
                    def executor = new BuildStepExecutor(taskLookup)

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'myService',
                        project.objects,
                        project.providers,
                        project.layout
                    )

                    def buildSpec = project.objects.newInstance(BuildStepSpec)
                    buildSpec.image.set(imageSpec)

                    def context = PipelineContext.create('productionPipeline')
                        .withMetadata('environment', 'production')

                    def result = executor.execute(buildSpec, context)

                    // Verify original context data preserved
                    assert result.pipelineName == 'productionPipeline'
                    assert result.getMetadataValue('environment') == 'production'

                    // Verify build completed
                    assert result.buildCompleted
                    assert result.builtImage == imageSpec
                    assert result.isBuildSuccessful()

                    println "Context update verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyContextUpdate')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Context update verified successfully!')
        result.task(':verifyContextUpdate').outcome == TaskOutcome.SUCCESS
    }

    def "BuildStepExecutor fails when build task is missing"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.BuildStepExecutor
            import com.kineticfire.gradle.docker.workflow.TaskLookupFactory
            import com.kineticfire.gradle.docker.spec.workflow.BuildStepSpec
            import com.kineticfire.gradle.docker.spec.ImageSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import org.gradle.api.GradleException

            // Note: No dockerBuildMissingImage task created

            task verifyMissingTaskFailure {
                doLast {
                    def taskLookup = TaskLookupFactory.fromTaskContainer(project.tasks)
                    def executor = new BuildStepExecutor(taskLookup)

                    def imageSpec = project.objects.newInstance(
                        ImageSpec,
                        'missingImage',
                        project.objects,
                        project.providers,
                        project.layout
                    )

                    def buildSpec = project.objects.newInstance(BuildStepSpec)
                    buildSpec.image.set(imageSpec)

                    def context = PipelineContext.create('test')

                    try {
                        executor.execute(buildSpec, context)
                        throw new AssertionError('Should have thrown exception for missing task')
                    } catch (GradleException e) {
                        assert e.message.contains('not found')
                        assert e.message.contains('dockerBuildMissingImage')
                    }

                    println "Missing task failure verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyMissingTaskFailure')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('Missing task failure verified successfully!')
        result.task(':verifyMissingTaskFailure').outcome == TaskOutcome.SUCCESS
    }

    def "PipelineContext can be created and manipulated"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import com.kineticfire.gradle.docker.workflow.TestResult

            task verifyPipelineContext {
                doLast {
                    // Test creation
                    def context = PipelineContext.create('testPipeline')
                    assert context.pipelineName == 'testPipeline'
                    assert !context.buildCompleted
                    assert !context.testCompleted

                    // Test immutable operations
                    def ctx2 = context.withMetadata('key', 'value')
                    assert ctx2.getMetadataValue('key') == 'value'
                    assert context.metadata.isEmpty() // original unchanged

                    // Test applied tags
                    def ctx3 = ctx2.withAppliedTag('v1.0')
                    assert ctx3.appliedTags == ['v1.0']

                    // Test multiple tags
                    def ctx4 = ctx3.withAppliedTags(['latest', 'stable'])
                    assert ctx4.appliedTags == ['v1.0', 'latest', 'stable']

                    // Test test result
                    def testResult = new TestResult(true, 10, 0, 0, 0, 10)
                    def ctx5 = ctx4.withTestResult(testResult)
                    assert ctx5.testCompleted
                    assert ctx5.isTestSuccessful()

                    println "PipelineContext verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyPipelineContext')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('PipelineContext verified successfully!')
        result.task(':verifyPipelineContext').outcome == TaskOutcome.SUCCESS
    }

    private List<File> getPluginClasspath() {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .collect { new File(it) }
    }
}
