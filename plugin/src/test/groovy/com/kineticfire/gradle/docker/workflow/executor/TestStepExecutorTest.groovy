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
import com.kineticfire.gradle.docker.spec.workflow.WorkflowLifecycle
import com.kineticfire.gradle.docker.workflow.PipelineContext
import com.kineticfire.gradle.docker.workflow.TaskLookup
import com.kineticfire.gradle.docker.workflow.TestResult
import com.kineticfire.gradle.docker.workflow.TestResultCapture
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test as GradleTestTask
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
    TaskLookup taskLookup

    def setup() {
        project = ProjectBuilder.builder().build()
        mockResultCapture = Mock(TestResultCapture)
        // Create a test TaskLookup that wraps the real project.tasks
        taskLookup = createTaskLookup(project)
        executor = new TestStepExecutor(taskLookup, mockResultCapture)

        stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')

        testStepSpec = project.objects.newInstance(TestStepSpec)
        testStepSpec.stack.set(stackSpec)

        testTask = project.tasks.create('integrationTest')
        testStepSpec.testTaskName.set('integrationTest')
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

    // ===== CONSTRUCTOR TESTS =====

    def "constructor accepts TaskLookup"() {
        when:
        def exec = new TestStepExecutor(taskLookup)

        then:
        exec != null
    }

    def "constructor accepts TaskLookup and resultCapture"() {
        when:
        def exec = new TestStepExecutor(taskLookup, mockResultCapture)

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

    def "validateTestSpec throws exception when stack is not set and delegateStackManagement is false"() {
        given:
        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        // delegateStackManagement defaults to false

        when:
        executor.validateTestSpec(spec)

        then:
        def e = thrown(GradleException)
        e.message.contains('stack must be configured when delegateStackManagement is false')
    }

    def "validateTestSpec throws exception when testTaskName is not set"() {
        given:
        def spec = project.objects.newInstance(TestStepSpec)
        spec.stack.set(stackSpec)

        when:
        executor.validateTestSpec(spec)

        then:
        def e = thrown(GradleException)
        e.message.contains('testTaskName must be configured')
    }

    def "validateTestSpec passes when stack and testTaskName are set"() {
        when:
        executor.validateTestSpec(testStepSpec)

        then:
        noExceptionThrown()
    }

    def "validateTestSpec passes when delegateStackManagement is true and stack is not set"() {
        given:
        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.delegateStackManagement.set(true)

        when:
        executor.validateTestSpec(spec)

        then:
        noExceptionThrown()
    }

    def "validateTestSpec passes when delegateStackManagement is true and testTaskName is set"() {
        given:
        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.delegateStackManagement.set(true)
        // stack is not set - this is valid when delegating

        when:
        executor.validateTestSpec(spec)

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
        tSpec.testTaskName.set('integrationTest')

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

    // ===== DELEGATE STACK MANAGEMENT TESTS =====

    def "execute skips composeUp when delegateStackManagement is true"() {
        given:
        def composeUpCalled = false
        project.tasks.create('composeUpTestStack') {
            doLast { composeUpCalled = true }
        }
        project.tasks.create('composeDownTestStack')

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.delegateStackManagement.set(true)
        // stack is not set - valid when delegating

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(spec, context)

        then:
        !composeUpCalled
    }

    def "execute skips composeDown when delegateStackManagement is true"() {
        given:
        def composeDownCalled = false
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack') {
            doLast { composeDownCalled = true }
        }

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.delegateStackManagement.set(true)

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(spec, context)

        then:
        !composeDownCalled
    }

    def "execute still runs composeUp when delegateStackManagement is false"() {
        given:
        def composeUpCalled = false
        project.tasks.create('composeUpTestStack') {
            doLast { composeUpCalled = true }
        }
        project.tasks.create('composeDownTestStack')

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(testStepSpec, context)

        then:
        composeUpCalled
    }

    def "execute still runs composeDown when delegateStackManagement is false"() {
        given:
        def composeDownCalled = false
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack') {
            doLast { composeDownCalled = true }
        }

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(testStepSpec, context)

        then:
        composeDownCalled
    }

    def "execute still runs beforeTest hook when delegateStackManagement is true"() {
        given:
        def hookExecuted = false
        def beforeHook = { hookExecuted = true } as Action<Void>

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.delegateStackManagement.set(true)
        spec.beforeTest.set(beforeHook)

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(spec, context)

        then:
        hookExecuted
    }

    def "execute still runs afterTest hook when delegateStackManagement is true"() {
        given:
        TestResult receivedResult = null
        def afterHook = { TestResult r -> receivedResult = r } as Action<TestResult>

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.delegateStackManagement.set(true)
        spec.afterTest.set(afterHook)

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(spec, context)

        then:
        receivedResult == expectedResult
    }

    def "execute still captures test result when delegateStackManagement is true"() {
        given:
        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.delegateStackManagement.set(true)

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 5, 0, 0, 0, 5)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        def result = executor.execute(spec, context)

        then:
        result.testResult == expectedResult
        result.testCompleted
    }

    def "execute still throws on test failure when delegateStackManagement is true"() {
        given:
        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.delegateStackManagement.set(true)

        testTask.doLast { throw new RuntimeException('Test failed') }
        def context = PipelineContext.create('test')
        def failureResult = new TestResult(false, 0, 0, 0, 1, 1)
        mockResultCapture.captureFailure(testTask, _ as Exception) >> failureResult

        when:
        executor.execute(spec, context)

        then:
        def e = thrown(GradleException)
        e.message.contains('Test execution failed')
    }

    def "execute runs hooks and test task without compose when delegateStackManagement is true"() {
        given:
        def executionOrder = []

        def beforeHook = { executionOrder << 'before' } as Action<Void>
        def afterHook = { TestResult r -> executionOrder << 'after' } as Action<TestResult>

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.delegateStackManagement.set(true)
        spec.beforeTest.set(beforeHook)
        spec.afterTest.set(afterHook)

        // Create compose tasks but they should NOT be called
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
        executor.execute(spec, context)

        then:
        executionOrder == ['before', 'test', 'after']
    }

    // ===== METHOD LIFECYCLE TESTS =====

    def "execute skips composeUp when lifecycle is METHOD"() {
        given:
        def composeUpCalled = false
        project.tasks.create('composeUpTestStack') {
            doLast { composeUpCalled = true }
        }
        project.tasks.create('composeDownTestStack')

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.stack.set(stackSpec)
        spec.lifecycle.set(WorkflowLifecycle.METHOD)

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(spec, context)

        then:
        !composeUpCalled
    }

    def "execute skips composeDown when lifecycle is METHOD"() {
        given:
        def composeDownCalled = false
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack') {
            doLast { composeDownCalled = true }
        }

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.stack.set(stackSpec)
        spec.lifecycle.set(WorkflowLifecycle.METHOD)

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(spec, context)

        then:
        !composeDownCalled
    }

    def "execute still runs composeUp when lifecycle is CLASS"() {
        given:
        def composeUpCalled = false
        project.tasks.create('composeUpTestStack') {
            doLast { composeUpCalled = true }
        }
        project.tasks.create('composeDownTestStack')

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.stack.set(stackSpec)
        spec.lifecycle.set(WorkflowLifecycle.CLASS)

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(spec, context)

        then:
        composeUpCalled
    }

    def "execute still runs composeDown when lifecycle is CLASS"() {
        given:
        def composeDownCalled = false
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack') {
            doLast { composeDownCalled = true }
        }

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.stack.set(stackSpec)
        spec.lifecycle.set(WorkflowLifecycle.CLASS)

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 1, 0, 0, 0, 1)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        executor.execute(spec, context)

        then:
        composeDownCalled
    }

    def "execute runs hooks and test task without compose when lifecycle is METHOD"() {
        given:
        def executionOrder = []

        def beforeHook = { executionOrder << 'before' } as Action<Void>
        def afterHook = { TestResult r -> executionOrder << 'after' } as Action<TestResult>

        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.stack.set(stackSpec)
        spec.lifecycle.set(WorkflowLifecycle.METHOD)
        spec.beforeTest.set(beforeHook)
        spec.afterTest.set(afterHook)

        // Create compose tasks but they should NOT be called
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
        executor.execute(spec, context)

        then:
        executionOrder == ['before', 'test', 'after']
    }

    def "execute still captures test result when lifecycle is METHOD"() {
        given:
        def spec = project.objects.newInstance(TestStepSpec)
        spec.testTaskName.set('integrationTest')
        spec.stack.set(stackSpec)
        spec.lifecycle.set(WorkflowLifecycle.METHOD)

        def context = PipelineContext.create('test')
        def expectedResult = new TestResult(true, 5, 0, 0, 0, 5)
        mockResultCapture.captureFromTask(testTask) >> expectedResult

        when:
        def result = executor.execute(spec, context)

        then:
        result.testResult == expectedResult
        result.testCompleted
    }

    // ===== SET SYSTEM PROPERTIES FOR METHOD LIFECYCLE TESTS =====

    def "setSystemPropertiesForMethodLifecycle sets core properties on Test task"() {
        given:
        def gradleTestTask = project.tasks.create('methodTest', GradleTestTask)

        when:
        executor.setSystemPropertiesForMethodLifecycle(gradleTestTask, stackSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.lifecycle'] == 'method'
        gradleTestTask.systemProperties['docker.compose.stack'] == 'testStack'
    }

    def "setSystemPropertiesForMethodLifecycle sets compose files property"() {
        given:
        def gradleTestTask = project.tasks.create('methodTest', GradleTestTask)
        def tempFile = File.createTempFile('compose', '.yml')
        tempFile.deleteOnExit()
        stackSpec.files.from(tempFile)

        when:
        executor.setSystemPropertiesForMethodLifecycle(gradleTestTask, stackSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.files'] != null
        gradleTestTask.systemProperties['docker.compose.files'].contains(tempFile.absolutePath)
    }

    def "setSystemPropertiesForMethodLifecycle sets project name when present"() {
        given:
        def gradleTestTask = project.tasks.create('methodTest', GradleTestTask)
        stackSpec.projectName.set('my-project')

        when:
        executor.setSystemPropertiesForMethodLifecycle(gradleTestTask, stackSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.projectName'] == 'my-project'
    }

    def "setSystemPropertiesForMethodLifecycle does not set project name when not present"() {
        given:
        def gradleTestTask = project.tasks.create('methodTest', GradleTestTask)

        when:
        executor.setSystemPropertiesForMethodLifecycle(gradleTestTask, stackSpec)

        then:
        !gradleTestTask.systemProperties.containsKey('docker.compose.projectName')
    }

    def "setSystemPropertiesForMethodLifecycle logs warning for non-Test task"() {
        given:
        def regularTask = project.tasks.create('nonTestTask')

        when:
        executor.setSystemPropertiesForMethodLifecycle(regularTask, stackSpec)

        then:
        noExceptionThrown()
        // Warning is logged but no exception is thrown
    }

    def "setSystemPropertiesForMethodLifecycle does not throw for non-Test task"() {
        given:
        def regularTask = project.tasks.create('regularTask')

        when:
        executor.setSystemPropertiesForMethodLifecycle(regularTask, stackSpec)

        then:
        noExceptionThrown()
    }

    // ===== COLLECT COMPOSE FILE PATHS TESTS =====

    def "collectComposeFilePaths returns empty list when no files configured"() {
        expect:
        executor.collectComposeFilePaths(stackSpec).isEmpty()
    }

    def "collectComposeFilePaths collects from files property"() {
        given:
        def tempFile = File.createTempFile('compose', '.yml')
        tempFile.deleteOnExit()
        stackSpec.files.from(tempFile)

        when:
        def paths = executor.collectComposeFilePaths(stackSpec)

        then:
        paths.size() == 1
        paths[0] == tempFile.absolutePath
    }

    def "collectComposeFilePaths collects multiple files"() {
        given:
        def tempFile1 = File.createTempFile('compose1', '.yml')
        def tempFile2 = File.createTempFile('compose2', '.yml')
        tempFile1.deleteOnExit()
        tempFile2.deleteOnExit()
        stackSpec.files.from(tempFile1, tempFile2)

        when:
        def paths = executor.collectComposeFilePaths(stackSpec)

        then:
        paths.size() == 2
        paths.contains(tempFile1.absolutePath)
        paths.contains(tempFile2.absolutePath)
    }

    // ===== SET WAIT SPEC SYSTEM PROPERTIES TESTS =====

    def "setWaitSpecSystemProperties sets services property"() {
        given:
        def gradleTestTask = project.tasks.create('waitTest', GradleTestTask)
        def waitSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        waitSpec.waitForServices.set(['service1', 'service2'])

        when:
        executor.setWaitSpecSystemProperties(gradleTestTask, 'docker.compose.waitForHealthy', waitSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.waitForHealthy.services'] == 'service1,service2'
    }

    def "setWaitSpecSystemProperties sets timeoutSeconds property"() {
        given:
        def gradleTestTask = project.tasks.create('waitTest', GradleTestTask)
        def waitSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        waitSpec.timeoutSeconds.set(120)

        when:
        executor.setWaitSpecSystemProperties(gradleTestTask, 'docker.compose.waitForHealthy', waitSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.waitForHealthy.timeoutSeconds'] == '120'
    }

    def "setWaitSpecSystemProperties sets pollSeconds property"() {
        given:
        def gradleTestTask = project.tasks.create('waitTest', GradleTestTask)
        def waitSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        waitSpec.pollSeconds.set(5)

        when:
        executor.setWaitSpecSystemProperties(gradleTestTask, 'docker.compose.waitForRunning', waitSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.waitForRunning.pollSeconds'] == '5'
    }

    def "setWaitSpecSystemProperties does not set empty services"() {
        given:
        def gradleTestTask = project.tasks.create('waitTest', GradleTestTask)
        def waitSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        waitSpec.waitForServices.set([])

        when:
        executor.setWaitSpecSystemProperties(gradleTestTask, 'docker.compose.waitForHealthy', waitSpec)

        then:
        !gradleTestTask.systemProperties.containsKey('docker.compose.waitForHealthy.services')
    }

    // ===== ADDITIONAL COVERAGE TESTS FOR setSystemPropertiesForMethodLifecycle =====

    def "setSystemPropertiesForMethodLifecycle sets waitForHealthy properties when present"() {
        given:
        def gradleTestTask = project.tasks.create('healthyTest', GradleTestTask)
        def waitSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        waitSpec.waitForServices.set(['service1', 'service2'])
        waitSpec.timeoutSeconds.set(60)
        waitSpec.pollSeconds.set(2)
        stackSpec.waitForHealthy.set(waitSpec)

        when:
        executor.setSystemPropertiesForMethodLifecycle(gradleTestTask, stackSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.waitForHealthy.services'] == 'service1,service2'
        gradleTestTask.systemProperties['docker.compose.waitForHealthy.timeoutSeconds'] == '60'
        gradleTestTask.systemProperties['docker.compose.waitForHealthy.pollSeconds'] == '2'
    }

    def "setSystemPropertiesForMethodLifecycle sets waitForRunning properties when present"() {
        given:
        def gradleTestTask = project.tasks.create('runningTest', GradleTestTask)
        def waitSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        waitSpec.waitForServices.set(['app', 'db'])
        waitSpec.timeoutSeconds.set(120)
        waitSpec.pollSeconds.set(5)
        stackSpec.waitForRunning.set(waitSpec)

        when:
        executor.setSystemPropertiesForMethodLifecycle(gradleTestTask, stackSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.waitForRunning.services'] == 'app,db'
        gradleTestTask.systemProperties['docker.compose.waitForRunning.timeoutSeconds'] == '120'
        gradleTestTask.systemProperties['docker.compose.waitForRunning.pollSeconds'] == '5'
    }

    def "setSystemPropertiesForMethodLifecycle sets both waitForHealthy and waitForRunning when present"() {
        given:
        def gradleTestTask = project.tasks.create('bothWaitTest', GradleTestTask)

        def healthySpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        healthySpec.waitForServices.set(['healthy-service'])
        healthySpec.timeoutSeconds.set(30)
        stackSpec.waitForHealthy.set(healthySpec)

        def runningSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        runningSpec.waitForServices.set(['running-service'])
        runningSpec.timeoutSeconds.set(60)
        stackSpec.waitForRunning.set(runningSpec)

        when:
        executor.setSystemPropertiesForMethodLifecycle(gradleTestTask, stackSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.waitForHealthy.services'] == 'healthy-service'
        gradleTestTask.systemProperties['docker.compose.waitForHealthy.timeoutSeconds'] == '30'
        gradleTestTask.systemProperties['docker.compose.waitForRunning.services'] == 'running-service'
        gradleTestTask.systemProperties['docker.compose.waitForRunning.timeoutSeconds'] == '60'
    }

    // ===== ADDITIONAL COVERAGE TESTS FOR collectComposeFilePaths =====

    def "collectComposeFilePaths collects from composeFile property when set"() {
        given:
        def tempFile = File.createTempFile('compose', '.yml')
        tempFile.deleteOnExit()
        stackSpec.composeFile.set(tempFile)

        when:
        def paths = executor.collectComposeFilePaths(stackSpec)

        then:
        paths.size() == 1
        paths[0] == tempFile.absolutePath
    }

    def "collectComposeFilePaths collects from composeFileCollection when set"() {
        given:
        def tempFile = File.createTempFile('compose-collection', '.yml')
        tempFile.deleteOnExit()
        stackSpec.composeFileCollection.from(tempFile)

        when:
        def paths = executor.collectComposeFilePaths(stackSpec)

        then:
        paths.contains(tempFile.absolutePath)
    }

    def "collectComposeFilePaths collects from all sources without duplicates"() {
        given:
        def tempFile1 = File.createTempFile('compose1', '.yml')
        def tempFile2 = File.createTempFile('compose2', '.yml')
        tempFile1.deleteOnExit()
        tempFile2.deleteOnExit()
        stackSpec.files.from(tempFile1)
        stackSpec.composeFile.set(tempFile2)
        stackSpec.composeFileCollection.from(tempFile2)  // Duplicate with composeFile

        when:
        def paths = executor.collectComposeFilePaths(stackSpec)

        then:
        paths.contains(tempFile1.absolutePath)
        paths.contains(tempFile2.absolutePath)
        // Duplicate should not be added
        paths.count { it == tempFile2.absolutePath } == 1
    }

    def "collectComposeFilePaths collects from composeFileCollection with multiple files"() {
        given:
        def tempFile1 = File.createTempFile('collection1', '.yml')
        def tempFile2 = File.createTempFile('collection2', '.yml')
        tempFile1.deleteOnExit()
        tempFile2.deleteOnExit()
        stackSpec.composeFileCollection.from(tempFile1, tempFile2)

        when:
        def paths = executor.collectComposeFilePaths(stackSpec)

        then:
        paths.contains(tempFile1.absolutePath)
        paths.contains(tempFile2.absolutePath)
    }

    // ===== ADDITIONAL COVERAGE TESTS FOR setWaitSpecSystemProperties =====

    def "setWaitSpecSystemProperties uses convention values when not explicitly set"() {
        given:
        def gradleTestTask = project.tasks.create('conventionTest', GradleTestTask)
        def waitSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        // WaitSpec has convention values: timeoutSeconds=60, pollSeconds=2, waitForServices=[]

        when:
        executor.setWaitSpecSystemProperties(gradleTestTask, 'docker.compose.wait', waitSpec)

        then:
        noExceptionThrown()
        // Empty services convention means no services property set
        !gradleTestTask.systemProperties.containsKey('docker.compose.wait.services')
        // But timeout and poll get their convention values
        gradleTestTask.systemProperties['docker.compose.wait.timeoutSeconds'] == '60'
        gradleTestTask.systemProperties['docker.compose.wait.pollSeconds'] == '2'
    }

    def "setWaitSpecSystemProperties sets all properties when all values configured"() {
        given:
        def gradleTestTask = project.tasks.create('allPropsTest', GradleTestTask)
        def waitSpec = project.objects.newInstance(com.kineticfire.gradle.docker.spec.WaitSpec)
        waitSpec.waitForServices.set(['svc1', 'svc2'])
        waitSpec.timeoutSeconds.set(90)
        waitSpec.pollSeconds.set(3)

        when:
        executor.setWaitSpecSystemProperties(gradleTestTask, 'docker.compose.wait', waitSpec)

        then:
        gradleTestTask.systemProperties['docker.compose.wait.services'] == 'svc1,svc2'
        gradleTestTask.systemProperties['docker.compose.wait.timeoutSeconds'] == '90'
        gradleTestTask.systemProperties['docker.compose.wait.pollSeconds'] == '3'
    }

    // ===== ADDITIONAL COVERAGE FOR execute() with test task not found =====

    def "execute throws exception when test task not found"() {
        given:
        project.tasks.create('composeUpTestStack')
        project.tasks.create('composeDownTestStack')
        // Note: 'integrationTest' task exists but we use a different task name
        def spec = project.objects.newInstance(TestStepSpec)
        spec.stack.set(stackSpec)
        spec.testTaskName.set('nonExistentTestTask')
        def context = PipelineContext.create('test')

        when:
        executor.execute(spec, context)

        then:
        def e = thrown(GradleException)
        e.message.contains("Test task 'nonExistentTestTask' not found")
    }

}
