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

package com.kineticfire.gradle.docker.extension

import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Unit tests for ComposeAnnotationHintListener.
 *
 * Tests the listener's ability to detect missing Docker Compose annotations and provide helpful hints.
 */
class ComposeAnnotationHintListenerTest extends Specification {

    @Subject
    ComposeAnnotationHintListener listener

    def setup() {
        listener = new ComposeAnnotationHintListener("testStack", "class")
    }

    // ==================== Constructor Tests ====================

    def "constructor initializes with stack name and lifecycle"() {
        when:
        def classListener = new ComposeAnnotationHintListener("myStack", "class")
        def methodListener = new ComposeAnnotationHintListener("otherStack", "method")

        then:
        classListener != null
        methodListener != null
    }

    // ==================== Initial State Tests ====================

    def "isHintProvided returns false initially"() {
        expect:
        !listener.isHintProvided()
    }

    def "resetHintState resets hint provided flag"() {
        given:
        // Trigger a hint by simulating a connection error failure
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))

        when:
        listener.afterTest(descriptor, result)

        then:
        listener.isHintProvided()

        when:
        listener.resetHintState()

        then:
        !listener.isHintProvided()
    }

    // ==================== beforeSuite Tests ====================

    def "beforeSuite does nothing"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", null)

        when:
        listener.beforeSuite(descriptor)

        then:
        !listener.isHintProvided()
        noExceptionThrown()
    }

    // ==================== beforeTest Tests ====================

    def "beforeTest does nothing"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")

        when:
        listener.beforeTest(descriptor)

        then:
        !listener.isHintProvided()
        noExceptionThrown()
    }

    // ==================== Connection Error Detection Tests ====================

    @Unroll
    def "afterTest detects connection error: '#errorMessage'"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException(errorMessage))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        listener.isHintProvided()
        output.contains("HINT: Possible missing Docker Compose annotation")
        output.contains("testStack")
        output.contains("class")

        where:
        errorMessage << [
            "Connection refused",
            "connection refused to localhost:8080",
            "Connect timed out",
            "connect timed out after 30 seconds",
            "No route to host",
            "no route to host 192.168.1.1",
            "Connection reset",
            "connection reset by peer",
            "Connection closed",
            "connection closed unexpectedly",
            "Socket timeout",
            "socket timeout occurred",
            "ECONNREFUSED",
            "econnrefused on port 5432"
        ]
    }

    def "afterTest detects connection error in cause message"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def rootCause = new RuntimeException("Connection refused to localhost:8080")
        def wrapper = new RuntimeException("Test failed", rootCause)
        def result = createFailedResult(wrapper)
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        listener.isHintProvided()
        output.contains("HINT: Possible missing Docker Compose annotation")
    }

    // ==================== State File Error Detection Tests ====================

    @Unroll
    def "afterTest detects state file error: '#errorMessage'"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException(errorMessage))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        listener.isHintProvided()
        output.contains("HINT: Possible missing Docker Compose annotation")

        where:
        errorMessage << [
            "COMPOSE_STATE_FILE is null",
            "compose_state_file environment variable is null",
            "State file not found at /tmp/compose-state.json",
            "state file not found"
        ]
    }

    // ==================== Stack Config Error Detection Tests ====================

    @Unroll
    def "afterTest detects stack config error: '#errorMessage'"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException(errorMessage))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        listener.isHintProvided()
        output.contains("HINT: Possible missing Docker Compose annotation")

        where:
        errorMessage << [
            "Stack not configured for this test",
            "stack not configured",
            "Compose not configured properly",
            "compose not configured"
        ]
    }

    // ==================== Non-Connection Error Tests ====================

    @Unroll
    def "afterTest does not provide hint for unrelated error: '#errorMessage'"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException(errorMessage))
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        !listener.isHintProvided()

        where:
        errorMessage << [
            "Assertion failed: expected 5 but was 3",
            "NullPointerException",
            "Index out of bounds",
            "Invalid argument",
            "File not found: /tmp/test.txt",
            "ClassNotFoundException: com.example.Missing",
            "Method not found",
            "Access denied",
            "Permission denied"
        ]
    }

    // ==================== Successful Test Tests ====================

    def "afterTest does not provide hint for successful test"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createSuccessResult()
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        !listener.isHintProvided()
    }

    def "afterSuite does not provide hint for successful suite"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", null)
        def result = createSuccessResult()
        captureStderr {
            listener.afterSuite(descriptor, result)
        }

        expect:
        !listener.isHintProvided()
    }

    // ==================== Null Exception Tests ====================

    def "afterTest handles failure with null exception"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResultWithNullException()

        when:
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        then:
        !listener.isHintProvided()
        noExceptionThrown()
    }

    def "afterTest handles exception with null message"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException((String) null))

        when:
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        then:
        !listener.isHintProvided()
        noExceptionThrown()
    }

    def "afterTest handles exception with null cause message"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def cause = new RuntimeException((String) null)
        def result = createFailedResult(new RuntimeException("Connection refused", cause))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        listener.isHintProvided()
        output.contains("HINT")
    }

    // ==================== afterSuite Tests ====================

    def "afterSuite detects connection error in suite failure"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTestSuite", null)
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterSuite(descriptor, result)
        }

        expect:
        listener.isHintProvided()
        output.contains("HINT: Possible missing Docker Compose annotation")
    }

    def "afterSuite does not provide hint for non-connection suite failure"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTestSuite", null)
        def result = createFailedResult(new RuntimeException("Some other error"))
        captureStderr {
            listener.afterSuite(descriptor, result)
        }

        expect:
        !listener.isHintProvided()
    }

    // ==================== Hint Once Tests ====================

    def "hint is provided only once per test run"() {
        given:
        def descriptor1 = createTestDescriptor("com.example.MyTest1", "testMethod1")
        def descriptor2 = createTestDescriptor("com.example.MyTest2", "testMethod2")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        int hintCount = 0

        when:
        def output1 = captureStderr {
            listener.afterTest(descriptor1, result)
        }
        if (output1.contains("HINT")) hintCount++

        def output2 = captureStderr {
            listener.afterTest(descriptor2, result)
        }
        if (output2.contains("HINT")) hintCount++

        then:
        hintCount == 1
        listener.isHintProvided()
    }

    // ==================== Hint Content Tests ====================

    def "hint includes correct class name for fully qualified name"() {
        given:
        def descriptor = createTestDescriptor("com.example.package.MyIntegrationTest", "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        output.contains("com.example.package.MyIntegrationTest")
        output.contains("class MyIntegrationTest extends Specification")
        output.contains("class MyIntegrationTest {")
    }

    def "hint includes correct class name for simple name"() {
        given:
        def descriptor = createTestDescriptor("SimpleTest", "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        output.contains("SimpleTest")
        output.contains("class SimpleTest extends Specification")
    }

    def "hint includes Spock annotation example"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        output.contains("Spock:")
        output.contains("@ComposeUp")
        output.contains("extends Specification")
    }

    def "hint includes JUnit 5 annotation example with correct extension for class lifecycle"() {
        given:
        listener = new ComposeAnnotationHintListener("testStack", "class")
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        output.contains("JUnit 5:")
        output.contains("@ExtendWith(DockerComposeClassExtension.class)")
    }

    def "hint includes JUnit 5 annotation example with correct extension for method lifecycle"() {
        given:
        listener = new ComposeAnnotationHintListener("testStack", "method")
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        output.contains("JUnit 5:")
        output.contains("@ExtendWith(DockerComposeMethodExtension.class)")
    }

    def "hint includes stack name"() {
        given:
        listener = new ComposeAnnotationHintListener("myCustomStack", "class")
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        output.contains("myCustomStack")
    }

    def "hint includes lifecycle mode"() {
        given:
        listener = new ComposeAnnotationHintListener("testStack", "method")
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        output.contains("'method' lifecycle")
    }

    def "hint includes documentation reference"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        output.contains("docs/usage/usage-docker-orch.md")
    }

    // ==================== Descriptor with null className Tests ====================

    def "hint handles descriptor with null className"() {
        given:
        def descriptor = createTestDescriptor(null, "testMethod")
        def result = createFailedResult(new RuntimeException("Connection refused"))
        def output = captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        listener.isHintProvided()
        output.contains("UnknownClass")
    }

    // ==================== Edge Case Tests for Branch Coverage ====================

    def "afterSuite does not provide duplicate hint when already provided"() {
        given:
        // First, trigger a hint via afterTest
        def testDescriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def testResult = createFailedResult(new RuntimeException("Connection refused"))
        captureStderr {
            listener.afterTest(testDescriptor, testResult)
        }

        // Now try afterSuite with same error
        def suiteDescriptor = createTestDescriptor("com.example.MySuite", null)
        def suiteResult = createFailedResult(new RuntimeException("Connection refused"))

        when:
        def output = captureStderr {
            listener.afterSuite(suiteDescriptor, suiteResult)
        }

        then:
        listener.isHintProvided()
        !output.contains("HINT")  // Second hint should not be printed
    }

    def "isStateFileError returns false for partial match - only compose_state_file"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("COMPOSE_STATE_FILE variable is set"))
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        !listener.isHintProvided()
    }

    def "isStateFileError returns false for partial match - only state without file"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("state variable not found"))
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        !listener.isHintProvided()
    }

    def "isStateFileError returns false for partial match - file without state"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("file not found at path"))
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        !listener.isHintProvided()
    }

    def "isStackConfigError returns false for partial match - only stack"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("stack is empty"))
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        !listener.isHintProvided()
    }

    def "isStackConfigError returns false for partial match - only compose"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("compose file missing"))
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        !listener.isHintProvided()
    }

    def "isStackConfigError returns false for partial match - only not configured"() {
        given:
        def descriptor = createTestDescriptor("com.example.MyTest", "testMethod")
        def result = createFailedResult(new RuntimeException("service not configured"))
        captureStderr {
            listener.afterTest(descriptor, result)
        }

        expect:
        !listener.isHintProvided()
    }

    // ==================== Helper Methods ====================

    private TestDescriptor createTestDescriptor(String className, String methodName) {
        Mock(TestDescriptor) {
            getClassName() >> className
            getName() >> (methodName ?: className)
        }
    }

    private TestResult createFailedResult(Throwable exception) {
        Mock(TestResult) {
            getResultType() >> TestResult.ResultType.FAILURE
            getException() >> exception
        }
    }

    private TestResult createFailedResultWithNullException() {
        Mock(TestResult) {
            getResultType() >> TestResult.ResultType.FAILURE
            getException() >> null
        }
    }

    private TestResult createSuccessResult() {
        Mock(TestResult) {
            getResultType() >> TestResult.ResultType.SUCCESS
            getException() >> null
        }
    }

    private String captureStderr(Closure closure) {
        def originalErr = System.err
        def baos = new ByteArrayOutputStream()
        System.err = new PrintStream(baos)
        try {
            closure.call()
            return baos.toString()
        } finally {
            System.err = originalErr
        }
    }
}
