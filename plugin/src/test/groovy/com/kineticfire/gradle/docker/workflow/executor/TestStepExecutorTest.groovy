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

package com.kineticfire.gradle.docker.workflow.executor

import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import com.kineticfire.gradle.docker.spec.workflow.TestStepSpec
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TaskLookup
import com.kineticfire.gradle.docker.workflow.TaskLookupFactory
import com.kineticfire.gradle.docker.workflow.TestResult
import com.kineticfire.gradle.docker.workflow.TestResultCapture
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for TestStepExecutor
 */
class TestStepExecutorTest extends Specification {

    Project project
    TestStepExecutor executor
    TestResultCapture mockResultCapture
    ComposeStackSpec stackSpec
    TestStepSpec testStepSpec
    Task testTask

    def setup() {
        project = ProjectBuilder.builder().build()
        mockResultCapture = Mock(TestResultCapture)
        executor = new TestStepExecutor(project.tasks, mockResultCapture)

        stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack', project.objects)

        testStepSpec = project.objects.newInstance(TestStepSpec)
        testStepSpec.stack.set(stackSpec)

        testTask = project.tasks.create('integrationTest')
        testStepSpec.testTask.set(testTask)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor accepts TaskContainer"() {
        when:
        def exec = new TestStepExecutor(project.tasks)

        then:
        exec != null
    }

    def "constructor accepts TaskContainer and resultCapture"() {
        when:
        def exec = new TestStepExecutor(project.tasks, mockResultCapture)

        then:
        exec != null
    }

    def "constructor accepts TaskLookup and resultCapture"() {
        when:
        def exec = new TestStepExecutor(TaskLookupFactory.from(project.tasks), mockResultCapture)

        then:
        exec != null
    }

    // ===== VALIDATE TEST SPEC TESTS =====

    def "validateTestSpec throws exception when testSpec is null"() {
        when:
        executor.validateTestSpec(null)

        then:
        def e = thrown(GradleException)
        e.message.contains('cannot be null')
    }

    def "validateTestSpec throws exception when stack is not set"() {
        given:
        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTask.set(testTask)

        when:
        executor.validateTestSpec(spec)

        then:
        def e = thrown(GradleException)
        e.message.contains('stack must be configured')
    }

    def "validateTestSpec throws exception when testTask is not set"() {
        given:
        def spec = project.objects.newInstance(TestStepSpec)
        spec.stack.set(stackSpec)

        when:
        executor.validateTestSpec(spec)

        then:
        def e = thrown(GradleException)
        e.message.contains('testTask must be configured')
    }

    def "validateTestSpec passes when stack and testTask are set"() {
        when:
        executor.validateTestSpec(testStepSpec)

        then:
        noExceptionThrown()
    }

    // ===== COMPUTE TASK NAME TESTS =====

    def "computeComposeUpTaskName capitalizes stack name"() {
        expect:
        executor.computeComposeUpTaskName(stackName) == expected

        where:
        stackName       | expected
        'myStack'       | 'composeUpMyStack'
        'testStack'     | 'composeUpTestStack'
        'app'           | 'composeUpApp'
        'API'           | 'composeUpAPI'
    }

    def "computeComposeDownTaskName capitalizes stack name"() {
        expect:
        executor.computeComposeDownTaskName(stackName) == expected

        where:
        stackName       | expected
        'myStack'       | 'composeDownMyStack'
        'testStack'     | 'composeDownTestStack'
        'app'           | 'composeDownApp'
        'API'           | 'composeDownAPI'
    }

    def "computeComposeUpTaskName handles single character"() {
        expect:
        executor.computeComposeUpTaskName('a') == 'composeUpA'
    }

    def "computeComposeDownTaskName handles single character"() {
        expect:
        executor.computeComposeDownTaskName('a') == 'composeDownA'
    }

    def "computeComposeUpTaskName handles empty string"() {
        expect:
        executor.computeComposeUpTaskName('') == 'composeUp'
    }

    def "computeComposeDownTaskName handles empty string"() {
        expect:
        executor.computeComposeDownTaskName('') == 'composeDown'
    }

    def "computeComposeUpTaskName handles null"() {
        expect:
        executor.computeComposeUpTaskName(null) == 'composeUpnull'
    }

    def "computeComposeDownTaskName handles null"() {
        expect:
        executor.computeComposeDownTaskName(null) == 'composeDownnull'
    }

    // ===== CAPITALIZE FIRST LETTER TESTS =====

    def "capitalizeFirstLetter capitalizes correctly"() {
        expect:
        executor.capitalizeFirstLetter(input) == expected

        where:
        input       | expected
        'test'      | 'Test'
        'Test'      | 'Test'
        'TEST'      | 'TEST'
        'a'         | 'A'
        'ab'        | 'Ab'
        'myApp'     | 'MyApp'
    }

    def "capitalizeFirstLetter handles empty string"() {
        expect:
        executor.capitalizeFirstLetter('') == ''
    }

    def "capitalizeFirstLetter handles null"() {
        expect:
        executor.capitalizeFirstLetter(null) == null
    }

    // ===== EXECUTE COMPOSE UP TASK TESTS =====

    def "executeComposeUpTask throws exception when task not found"() {
        when:
        executor.executeComposeUpTask('nonExistentTask')

        then:
        def e = thrown(GradleException)
        e.message.contains('not found')
        e.message.contains('nonExistentTask')
    }

    def "executeComposeUpTask executes task actions"() {
        given:
        def actionExecuted = false
        def task = project.tasks.create('composeUpTest') {
            doLast { actionExecuted = true }
        }

        when:
        executor.executeComposeUpTask('composeUpTest')

        then:
        actionExecuted
    }

    // ===== EXECUTE COMPOSE DOWN TASK TESTS =====

    def "executeComposeDownTask does nothing when task not found"() {
        when:
        executor.executeComposeDownTask('nonExistentTask')

        then:
        noExceptionThrown()
    }

    def "executeComposeDownTask executes task actions"() {
        given:
        def actionExecuted = false
        def task = project.tasks.create('composeDownTest') {
            doLast { actionExecuted = true }
        }

        when:
        executor.executeComposeDownTask('composeDownTest')

        then:
        actionExecuted
    }

    def "executeComposeDownTask handles task exception gracefully"() {
        given:
        project.tasks.create('composeDownFailing') {
            doLast { throw new RuntimeException('Cleanup failed') }
        }

        when:
        executor.executeComposeDownTask('composeDownFailing')

        then:
        noExceptionThrown()
    }

    // ===== EXECUTE TEST TASK TESTS =====

    def "executeTestTask executes task actions"() {
        given:
        def actionExecuted = false
        testTask.doLast { actionExecuted = true }

        when:
        executor.executeTestTask(testTask)

        then:
        actionExecuted
    }

    // ===== LOOKUP TASK TESTS =====

    def "lookupTask returns task when exists"() {
        given:
        project.tasks.create('myTask')

        expect:
        executor.lookupTask('myTask') != null
    }

    def "lookupTask returns null when task does not exist"() {
        expect:
        executor.lookupTask('nonExistent') == null
    }

    // ===== EXECUTE TASK TESTS =====

    def "executeTask runs all task actions"() {
        given:
        def action1Executed = false
        def action2Executed = false
        def task = project.tasks.create('testMultiAction') {
            doLast { action1Executed = true }
            doLast { action2Executed = true }
        }

        when:
        executor.executeTask(task)

        then:
        action1Executed
        action2Executed
    }

    def "executeTask handles task with no actions"() {
        given:
        def task = project.tasks.create('emptyTask')

        when:
        executor.executeTask(task)

        then:
        noExceptionThrown()
    }

    // ===== HOOK EXECUTION TESTS =====

    def "executeBeforeTestHook executes hook when present"() {
        given:
        def hookExecuted = false
        def hook = { hookExecuted = true } as Action<Void>
        testStepSpec.beforeTest.set(hook)

        when:
        executor.executeBeforeTestHook(testStepSpec)

        then:
        hookExecuted
    }

    def "executeBeforeTestHook does nothing when hook not present"() {
        when:
        executor.executeBeforeTestHook(testStepSpec)

        then:
        noExceptionThrown()
    }

    def "executeAfterTestHook executes hook when present"() {
        given:
        TestResult receivedResult = null
        def hook = { TestResult r -> receivedResult = r } as Action<TestResult>
        testStepSpec.afterTest.set(hook)
        def testResult = new TestResult(true, 10, 0, 0, 0, 10)

        when:
        executor.executeAfterTestHook(testStepSpec, testResult)

        then:
        receivedResult == testResult
    }

    def "executeAfterTestHook does nothing when hook not present"() {
        given:
        def testResult = new TestResult(true, 10, 0, 0, 0, 10)

        when:
        executor.executeAfterTestHook(testStepSpec, testResult)

        then:
        noExceptionThrown()
    }

    def "executeHook executes action with null parameter"() {
        given:
        def receivedParam = 'notNull'
        def hook = { param -> receivedParam = param } as Action<Void>

        when:
        executor.executeHook(hook)

        then:
        receivedParam == null
    }

    // ===== EXECUTE TESTS =====

    def "execute validates testSpec"() {
        given:
        def context = PipelineContext.create('test')

        when:
        executor.execute(null, context)

        then:
        thrown(GradleException)
    }

    def "execute throws exception when composeUp task not found"() {
        given:
        def context = PipelineContext.create('test')

        when:
        executor.execute(testStepSpec, context)

        then:
        def e = thrown(GradleException)
        e.message.contains('not found')
    }

    def "execute calls hooks and tasks in correct order"() {
        given:
        def executionOrder = []

        def beforeHook = { executionOrder << 'before' } as Action<Void>
        def afterHook = { TestResult r -> executionOrder << 'after' } as Action<TestResult>
        testStepSpec.beforeTest.set(beforeHook)
        testStepSpec.afterTest.set(afterHook)

        project.tasks.create('composeUpTestStack') {
            doLast { executionOrder << 'composeUp' }
        }
        project.tasks.create('composeDownTestStack') {
            doLast { executionOrder << 'composeDown' }
        }
        testTask.doLast { executionOrder << 'test' }

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(testStepSpec, context)

        then:
        executionOrder == ['before', 'composeUp', 'test', 'composeDown', 'after']
    }

    def "execute returns context with test result"() {
        given:
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack')
        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 5, 0, 0, 0, 5)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        def result = executor.execute(testStepSpec, context)

        then:
        result.testResult == expectedResult
        result.testCompleted
    }

    def "execute preserves existing context data"() {
        given:
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack')
        def context = PipelineContext.create('myPipeline')
            .withMetadata('key', 'value')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        def result = executor.execute(testStepSpec, context)

        then:
        result.pipelineName == 'myPipeline'
        result.metadata['key'] == 'value'
        result.testResult == expectedResult
    }

    def "execute works without hooks"() {
        given:
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack')
        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        def result = executor.execute(testStepSpec, context)

        then:
        result.testCompleted
        result.testResult == expectedResult
    }

    def "execute calls composeDown even when test fails with exception"() {
        given:
        def composeDownCalled = false
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack') {
            doLast { composeDownCalled = true }
        }
        testTask.doLast { throw new RuntimeException('Test failed') }
        def context = PipelineContext.create('test')
        def failureResult = new TestResult(false, 0, 0, 0, 1, 1)
        mockResultCapture.captureFailure(testTask, _ as Exception) >> failureResult

        when:
        executor.execute(testStepSpec, context)

        then:
        thrown(GradleException)
        composeDownCalled
    }

    def "execute captures failure result when test throws exception"() {
        given:
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack')
        def testException = new RuntimeException('Test failed')
        testTask.doLast { throw testException }
        def context = PipelineContext.create('test')
        def failureResult = new TestResult(false, 0, 0, 0, 1, 1)

        when:
        executor.execute(testStepSpec, context)

        then:
        1 * mockResultCapture.captureFailure(testTask, _ as Exception) >> failureResult
        def e = thrown(GradleException)
        e.message.contains('Test execution failed')
    }

    def "execute still calls afterTest hook when test fails"() {
        given:
        TestResult receivedResult = null
        def afterHook = { TestResult r -> receivedResult = r } as Action<TestResult>
        testStepSpec.afterTest.set(afterHook)

        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack')
        testTask.doLast { throw new RuntimeException('Test failed') }
        def context = PipelineContext.create('test')
        def failureResult = new TestResult(false, 0, 0, 0, 1, 1)
        mockResultCapture.captureFailure(testTask, _ as Exception) >> failureResult

        when:
        executor.execute(testStepSpec, context)

        then:
        thrown(GradleException)
        receivedResult == failureResult
    }

    // ===== ERROR HANDLING TESTS =====

    def "execute propagates hook exceptions"() {
        given:
        def hook = { throw new RuntimeException('Hook failed') } as Action<Void>
        testStepSpec.beforeTest.set(hook)
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack')
        def context = PipelineContext.create('test')

        when:
        executor.execute(testStepSpec, context)

        then:
        def e = thrown(RuntimeException)
        e.message == 'Hook failed'
    }

    def "execute propagates composeUp exceptions"() {
        given:
        project.tasks.create('composeUpTestStack') {
            doLast { throw new RuntimeException('ComposeUp failed') }
        }
        project.tasks.create('composeDownTestStack')
        def context = PipelineContext.create('test')

        when:
        executor.execute(testStepSpec, context)

        then:
        def e = thrown(RuntimeException)
        e.message == 'ComposeUp failed'
    }

    // ===== DIFFERENT STACK NAMES TESTS =====

    def "execute works with various stack names"() {
        given:
        def stSpec = project.objects.newInstance(ComposeStackSpec, stackName, project.objects)
        def tSpec = project.objects.newInstance(TestStepSpec)
        tSpec.stack.set(stSpec)
        tSpec.testTask.set(testTask)

        project.tasks.create("composeUp${stackName.capitalize()}")
        project.tasks.create("composeDown${stackName.capitalize()}")
        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        def result = executor.execute(tSpec, context)

        then:
        result.testResult == expectedResult

        where:
        stackName << ['app', 'myService', 'webApi']
    }
}
