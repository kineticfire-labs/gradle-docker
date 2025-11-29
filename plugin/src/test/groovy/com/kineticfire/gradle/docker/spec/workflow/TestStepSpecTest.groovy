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

package com.kineticfire.gradle.docker.spec.workflow

import com.kineticfire.gradle.docker.spec.ComposeStackSpec
import com.kineticfire.gradle.docker.workflow.TestResult
import org.gradle.api.Action
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for TestStepSpec
 */
class TestStepSpecTest extends Specification {

    def project
    def testStepSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        testStepSpec = project.objects.newInstance(TestStepSpec)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        testStepSpec != null
        testStepSpec.timeoutMinutes.present
        testStepSpec.timeoutMinutes.get() == 30
        testStepSpec.delegateStackManagement.present
        testStepSpec.delegateStackManagement.get() == false
    }

    // ===== PROPERTY TESTS =====

    def "stack property works correctly"() {
        given:
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'testStack')

        when:
        testStepSpec.stack.set(stackSpec)

        then:
        testStepSpec.stack.present
        testStepSpec.stack.get() == stackSpec
    }

    def "testTaskName property works correctly"() {
        when:
        testStepSpec.testTaskName.set('integrationTest')

        then:
        testStepSpec.testTaskName.present
        testStepSpec.testTaskName.get() == 'integrationTest'
    }

    def "timeoutMinutes property works correctly"() {
        when:
        testStepSpec.timeoutMinutes.set(45)

        then:
        testStepSpec.timeoutMinutes.present
        testStepSpec.timeoutMinutes.get() == 45
    }

    def "timeoutMinutes has default value of 30"() {
        expect:
        testStepSpec.timeoutMinutes.present
        testStepSpec.timeoutMinutes.get() == 30
    }

    def "delegateStackManagement has default value of false"() {
        expect:
        testStepSpec.delegateStackManagement.present
        testStepSpec.delegateStackManagement.get() == false
    }

    def "delegateStackManagement property can be set to true"() {
        when:
        testStepSpec.delegateStackManagement.set(true)

        then:
        testStepSpec.delegateStackManagement.present
        testStepSpec.delegateStackManagement.get() == true
    }

    def "delegateStackManagement property can be set to false explicitly"() {
        when:
        testStepSpec.delegateStackManagement.set(false)

        then:
        testStepSpec.delegateStackManagement.present
        testStepSpec.delegateStackManagement.get() == false
    }

    def "delegateStackManagement property can be updated after initial configuration"() {
        when:
        testStepSpec.delegateStackManagement.set(true)

        then:
        testStepSpec.delegateStackManagement.get() == true

        when:
        testStepSpec.delegateStackManagement.set(false)

        then:
        testStepSpec.delegateStackManagement.get() == false

        when:
        testStepSpec.delegateStackManagement.set(true)

        then:
        testStepSpec.delegateStackManagement.get() == true
    }

    def "beforeTest hook property works correctly"() {
        given:
        def hookCalled = false
        def hook = { hookCalled = true } as Action<Void>

        when:
        testStepSpec.beforeTest.set(hook)

        then:
        testStepSpec.beforeTest.present
        testStepSpec.beforeTest.get() != null

        when:
        testStepSpec.beforeTest.get().execute(null)

        then:
        hookCalled
    }

    def "afterTest hook property works correctly with TestResult"() {
        given:
        TestResult capturedResult = null
        def hook = { TestResult result -> capturedResult = result } as Action<TestResult>
        def testResult = new TestResult(true, 10, 0, 0, 0, 10)

        when:
        testStepSpec.afterTest.set(hook)

        then:
        testStepSpec.afterTest.present
        testStepSpec.afterTest.get() != null

        when:
        testStepSpec.afterTest.get().execute(testResult)

        then:
        capturedResult != null
        capturedResult == testResult
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def stackSpec = project.objects.newInstance(ComposeStackSpec, 'myStack')
        def beforeHookCalled = false
        def afterHookCalled = false
        TestResult capturedResult = null
        def beforeHook = { beforeHookCalled = true } as Action<Void>
        def afterHook = { TestResult result ->
            afterHookCalled = true
            capturedResult = result
        } as Action<TestResult>

        when:
        testStepSpec.stack.set(stackSpec)
        testStepSpec.testTaskName.set('myTest')
        testStepSpec.timeoutMinutes.set(60)
        testStepSpec.beforeTest.set(beforeHook)
        testStepSpec.afterTest.set(afterHook)

        then:
        testStepSpec.stack.get() == stackSpec
        testStepSpec.testTaskName.get() == 'myTest'
        testStepSpec.timeoutMinutes.get() == 60
        testStepSpec.beforeTest.present
        testStepSpec.afterTest.present

        when:
        testStepSpec.beforeTest.get().execute(null)
        def result = new TestResult(true, 5, 0, 0, 0, 5)
        testStepSpec.afterTest.get().execute(result)

        then:
        beforeHookCalled
        afterHookCalled
        capturedResult == result
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        given:
        def initialStack = project.objects.newInstance(ComposeStackSpec, 'initial')
        def updatedStack = project.objects.newInstance(ComposeStackSpec, 'updated')

        when:
        testStepSpec.stack.set(initialStack)
        testStepSpec.testTaskName.set('initialTest')
        testStepSpec.timeoutMinutes.set(20)

        then:
        testStepSpec.stack.get() == initialStack
        testStepSpec.testTaskName.get() == 'initialTest'
        testStepSpec.timeoutMinutes.get() == 20

        when:
        testStepSpec.stack.set(updatedStack)
        testStepSpec.testTaskName.set('updatedTest')
        testStepSpec.timeoutMinutes.set(90)

        then:
        testStepSpec.stack.get() == updatedStack
        testStepSpec.testTaskName.get() == 'updatedTest'
        testStepSpec.timeoutMinutes.get() == 90
    }

    // ===== EDGE CASES =====

    def "timeout can be set to various valid values"() {
        expect:
        [1, 5, 10, 30, 60, 120, 240].each { timeout ->
            testStepSpec.timeoutMinutes.set(timeout)
            assert testStepSpec.timeoutMinutes.get() == timeout
        }
    }

    def "afterTest hook receives different TestResult instances"() {
        given:
        List<TestResult> capturedResults = []
        def hook = { TestResult result -> capturedResults.add(result) } as Action<TestResult>
        testStepSpec.afterTest.set(hook)

        def result1 = new TestResult(true, 10, 0, 0, 0, 10)
        def result2 = new TestResult(false, 8, 0, 0, 2, 10)

        when:
        testStepSpec.afterTest.get().execute(result1)
        testStepSpec.afterTest.get().execute(result2)

        then:
        capturedResults.size() == 2
        capturedResults[0].success
        !capturedResults[1].success
        capturedResults[1].failureCount == 2
    }

    def "stack property can reference different ComposeStackSpec instances"() {
        given:
        def stack1 = project.objects.newInstance(ComposeStackSpec, 'stack1')
        def stack2 = project.objects.newInstance(ComposeStackSpec, 'stack2')

        expect:
        [stack1, stack2].each { stack ->
            testStepSpec.stack.set(stack)
            assert testStepSpec.stack.get() == stack
        }
    }

    def "testTaskName property can be set to different values"() {
        expect:
        ['test1', 'test2', 'integrationTest'].each { taskName ->
            testStepSpec.testTaskName.set(taskName)
            assert testStepSpec.testTaskName.get() == taskName
        }
    }

    def "hooks are independent of each other"() {
        given:
        def beforeCalled = false
        def afterCalled = false
        def beforeHook = { beforeCalled = true } as Action<Void>
        def afterHook = { TestResult result -> afterCalled = true } as Action<TestResult>

        when:
        testStepSpec.beforeTest.set(beforeHook)
        testStepSpec.afterTest.set(afterHook)
        testStepSpec.beforeTest.get().execute(null)

        then:
        beforeCalled
        !afterCalled

        when:
        testStepSpec.afterTest.get().execute(new TestResult(true, 1, 0, 0, 0, 1))

        then:
        afterCalled
    }

    def "multiple hooks can be set and replaced"() {
        given:
        def firstHookCalled = false
        def secondHookCalled = false
        def firstHook = { firstHookCalled = true } as Action<Void>
        def secondHook = { secondHookCalled = true } as Action<Void>

        when:
        testStepSpec.beforeTest.set(firstHook)
        testStepSpec.beforeTest.get().execute(null)

        then:
        firstHookCalled
        !secondHookCalled

        when:
        testStepSpec.beforeTest.set(secondHook)
        testStepSpec.beforeTest.get().execute(null)

        then:
        secondHookCalled
    }
}
