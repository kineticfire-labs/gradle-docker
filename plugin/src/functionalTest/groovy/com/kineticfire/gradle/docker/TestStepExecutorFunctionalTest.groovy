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
 * Functional tests for TestStepExecutor workflow execution
 *
 * These tests verify the TestStepExecutor can orchestrate test step execution
 * (composeUp → test → composeDown) through Gradle's task infrastructure.
 */
class TestStepExecutorFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile = testProjectDir.resolve('build.gradle').toFile()

        settingsFile << "rootProject.name = 'test-test-step'"
    }

    def "TestStepExecutor computes correct task names"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.TestStepExecutor

            task verifyTaskNames {
                doLast {
                    def executor = new TestStepExecutor(project.tasks)

                    assert executor.computeComposeUpTaskName('myStack') == 'composeUpMyStack'
                    assert executor.computeComposeUpTaskName('testStack') == 'composeUpTestStack'
                    assert executor.computeComposeUpTaskName('api') == 'composeUpApi'

                    assert executor.computeComposeDownTaskName('myStack') == 'composeDownMyStack'
                    assert executor.computeComposeDownTaskName('testStack') == 'composeDownTestStack'
                    assert executor.computeComposeDownTaskName('api') == 'composeDownApi'

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

    def "TestStepExecutor validates TestStepSpec correctly"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.TestStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
            import com.kineticfire.gradle.docker.spec.ComposeStackSpec
            import org.gradle.api.GradleException

            task verifyValidation {
                doLast {
                    def executor = new TestStepExecutor(project.tasks)

                    // Test null spec
                    try {
                        executor.validateTestSpec(null)
                        throw new AssertionError('Should have thrown exception for null spec')
                    } catch (GradleException e) {
                        assert e.message.contains('cannot be null')
                    }

                    // Test spec without stack
                    def specNoStack = project.objects.newInstance(TestStepSpec)
                    specNoStack.testTask.set(tasks.create('dummyTest'))
                    try {
                        executor.validateTestSpec(specNoStack)
                        throw new AssertionError('Should have thrown exception for missing stack')
                    } catch (GradleException e) {
                        assert e.message.contains('stack must be configured')
                    }

                    // Test spec without testTask
                    def specNoTask = project.objects.newInstance(TestStepSpec)
                    def stackSpec = project.objects.newInstance(
                        ComposeStackSpec,
                        'testStack',
                        project.objects
                    )
                    specNoTask.stack.set(stackSpec)
                    try {
                        executor.validateTestSpec(specNoTask)
                        throw new AssertionError('Should have thrown exception for missing testTask')
                    } catch (GradleException e) {
                        assert e.message.contains('testTask must be configured')
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

    def "TestStepExecutor executes beforeTest and afterTest hooks"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.TestStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
            import com.kineticfire.gradle.docker.spec.ComposeStackSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import com.kineticfire.gradle.docker.workflow.TestResult
            import org.gradle.api.Action

            // Create mock compose tasks
            task composeUpTestStack {
                doLast {
                    println "MOCK_COMPOSE_UP_EXECUTED"
                }
            }
            task composeDownTestStack {
                doLast {
                    println "MOCK_COMPOSE_DOWN_EXECUTED"
                }
            }

            // Create a mock test task (use unique name to avoid conflict with plugin's integrationTest)
            task myTestTask {
                doLast {
                    println "MOCK_TEST_EXECUTED"
                }
            }

            task verifyHooks {
                doLast {
                    def executor = new TestStepExecutor(project.tasks)

                    // Create ComposeStackSpec
                    def stackSpec = project.objects.newInstance(
                        ComposeStackSpec,
                        'testStack',
                        project.objects
                    )

                    // Create TestStepSpec with hooks - must cast closures to Action
                    def testSpec = project.objects.newInstance(TestStepSpec)
                    testSpec.stack.set(stackSpec)
                    testSpec.testTask.set(tasks.named('myTestTask').get())
                    testSpec.beforeTest.set({ println "BEFORE_TEST_HOOK" } as Action<Void>)
                    testSpec.afterTest.set({ TestResult r ->
                        println "AFTER_TEST_HOOK: success=\${r.success}"
                    } as Action<TestResult>)

                    // Execute
                    def context = PipelineContext.create('test')
                    def result = executor.execute(testSpec, context)

                    // Verify context updated
                    assert result.testCompleted
                    assert result.testResult != null
                    assert result.testResult.success

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
        result.output.contains('BEFORE_TEST_HOOK')
        result.output.contains('MOCK_COMPOSE_UP_EXECUTED')
        result.output.contains('MOCK_TEST_EXECUTED')
        result.output.contains('MOCK_COMPOSE_DOWN_EXECUTED')
        result.output.contains('AFTER_TEST_HOOK: success=true')
        result.output.contains('Hooks verified successfully!')
        result.task(':verifyHooks').outcome == TaskOutcome.SUCCESS
    }

    def "TestStepExecutor updates PipelineContext with test result"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.TestStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
            import com.kineticfire.gradle.docker.spec.ComposeStackSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext

            // Create mock compose tasks
            task composeUpMyService {
                doLast {
                    println "Starting containers..."
                }
            }
            task composeDownMyService {
                doLast {
                    println "Stopping containers..."
                }
            }

            // Create mock test task
            task runTests {
                doLast {
                    println "Running tests..."
                }
            }

            task verifyContextUpdate {
                doLast {
                    def executor = new TestStepExecutor(project.tasks)

                    def stackSpec = project.objects.newInstance(
                        ComposeStackSpec,
                        'myService',
                        project.objects
                    )

                    def testSpec = project.objects.newInstance(TestStepSpec)
                    testSpec.stack.set(stackSpec)
                    testSpec.testTask.set(tasks.named('runTests').get())

                    def context = PipelineContext.create('productionPipeline')
                        .withMetadata('environment', 'production')

                    def result = executor.execute(testSpec, context)

                    // Verify original context data preserved
                    assert result.pipelineName == 'productionPipeline'
                    assert result.getMetadataValue('environment') == 'production'

                    // Verify test completed
                    assert result.testCompleted
                    assert result.testResult != null
                    assert result.isTestSuccessful()

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

    def "TestStepExecutor fails when composeUp task is missing"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.TestStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
            import com.kineticfire.gradle.docker.spec.ComposeStackSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import org.gradle.api.GradleException

            // Note: No composeUpMissingStack task created

            task dummyTest {
                doLast {
                    println "Running test..."
                }
            }

            task verifyMissingTaskFailure {
                doLast {
                    def executor = new TestStepExecutor(project.tasks)

                    def stackSpec = project.objects.newInstance(
                        ComposeStackSpec,
                        'missingStack',
                        project.objects
                    )

                    def testSpec = project.objects.newInstance(TestStepSpec)
                    testSpec.stack.set(stackSpec)
                    testSpec.testTask.set(tasks.named('dummyTest').get())

                    def context = PipelineContext.create('test')

                    try {
                        executor.execute(testSpec, context)
                        throw new AssertionError('Should have thrown exception for missing task')
                    } catch (GradleException e) {
                        assert e.message.contains('not found')
                        assert e.message.contains('composeUpMissingStack')
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

    def "TestStepExecutor calls composeDown even when test fails"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.executor.TestStepExecutor
            import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
            import com.kineticfire.gradle.docker.spec.ComposeStackSpec
            import com.kineticfire.gradle.docker.workflow.PipelineContext
            import org.gradle.api.GradleException

            // Track if composeDown was called
            ext.composeDownCalled = false

            task composeUpFailingStack {
                doLast {
                    println "COMPOSE_UP_CALLED"
                }
            }
            task composeDownFailingStack {
                doLast {
                    project.ext.composeDownCalled = true
                    println "COMPOSE_DOWN_CALLED"
                }
            }

            task failingTest {
                doLast {
                    throw new RuntimeException("Test failed intentionally")
                }
            }

            task verifyComposeDownOnFailure {
                doLast {
                    def executor = new TestStepExecutor(project.tasks)

                    def stackSpec = project.objects.newInstance(
                        ComposeStackSpec,
                        'failingStack',
                        project.objects
                    )

                    def testSpec = project.objects.newInstance(TestStepSpec)
                    testSpec.stack.set(stackSpec)
                    testSpec.testTask.set(tasks.named('failingTest').get())

                    def context = PipelineContext.create('test')

                    try {
                        executor.execute(testSpec, context)
                        throw new AssertionError('Should have thrown exception for failed test')
                    } catch (GradleException e) {
                        assert e.message.contains('Test execution failed')
                    }

                    // Verify composeDown was called despite test failure
                    assert project.ext.composeDownCalled : "composeDown should have been called"

                    println "ComposeDown on failure verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyComposeDownOnFailure')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('COMPOSE_UP_CALLED')
        result.output.contains('COMPOSE_DOWN_CALLED')
        result.output.contains('ComposeDown on failure verified successfully!')
        result.task(':verifyComposeDownOnFailure').outcome == TaskOutcome.SUCCESS
    }

    def "TestResultCapture captures results from JUnit XML"() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'com.kineticfire.gradle.docker'
            }

            import com.kineticfire.gradle.docker.workflow.TestResultCapture

            task verifyTestResultCapture {
                doLast {
                    def capture = new TestResultCapture()

                    // Test safeParseInt
                    assert capture.safeParseInt('10', 0) == 10
                    assert capture.safeParseInt('', 5) == 5
                    assert capture.safeParseInt(null, 5) == 5
                    assert capture.safeParseInt('abc', 5) == 5

                    // Test createSuccessResult
                    def successResult = capture.createSuccessResult()
                    assert successResult.success
                    assert successResult.failureCount == 0

                    // Test createFailureResult
                    def failureResult = capture.createFailureResult()
                    assert !failureResult.success
                    assert failureResult.failureCount == 1

                    println "TestResultCapture verified successfully!"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('verifyTestResultCapture')
            .withPluginClasspath(getPluginClasspath())
            .build()

        then:
        result.output.contains('TestResultCapture verified successfully!')
        result.task(':verifyTestResultCapture').outcome == TaskOutcome.SUCCESS
    }

    private List<File> getPluginClasspath() {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .collect { new File(it) }
    }
}
