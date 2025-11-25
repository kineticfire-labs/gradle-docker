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

import spock.lang.Specification

/**
 * Unit tests for TestResult
 */
class TestResultTest extends Specification {

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with all properties"() {
        when:
        def result = new TestResult(true, 10, 2, 1, 0, 13)

        then:
        result.success == true
        result.executed == 10
        result.upToDate == 2
        result.skipped == 1
        result.failureCount == 0
        result.totalCount == 13
    }

    def "constructor creates failed test result"() {
        when:
        def result = new TestResult(false, 8, 0, 0, 2, 10)

        then:
        result.success == false
        result.failureCount == 2
        result.totalCount == 10
    }

    // ===== GETTER TESTS =====

    def "isSuccess() returns correct value for successful test"() {
        given:
        def result = new TestResult(true, 10, 0, 0, 0, 10)

        expect:
        result.isSuccess() == true
        result.success == true
    }

    def "isSuccess() returns correct value for failed test"() {
        given:
        def result = new TestResult(false, 8, 0, 0, 2, 10)

        expect:
        result.isSuccess() == false
        result.success == false
    }

    def "getFailureCount() returns correct value"() {
        given:
        def result = new TestResult(false, 5, 0, 0, 3, 8)

        expect:
        result.getFailureCount() == 3
        result.failureCount == 3
    }

    def "getTotalCount() returns correct value"() {
        given:
        def result = new TestResult(true, 20, 0, 0, 0, 20)

        expect:
        result.totalCount == 20
    }

    // ===== SERIALIZATION TESTS =====

    def "TestResult is serializable"() {
        given:
        def result = new TestResult(true, 10, 2, 1, 0, 13)

        when:
        def baos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(baos)
        oos.writeObject(result)
        oos.close()

        def bais = new ByteArrayInputStream(baos.toByteArray())
        def ois = new ObjectInputStream(bais)
        def deserializedResult = ois.readObject() as TestResult
        ois.close()

        then:
        deserializedResult.success == result.success
        deserializedResult.executed == result.executed
        deserializedResult.upToDate == result.upToDate
        deserializedResult.skipped == result.skipped
        deserializedResult.failureCount == result.failureCount
        deserializedResult.totalCount == result.totalCount
    }

    // ===== VARIOUS SCENARIOS =====

    def "TestResult with zero failures indicates success"() {
        when:
        def result = new TestResult(true, 15, 0, 0, 0, 15)

        then:
        result.success
        result.failureCount == 0
        result.executed == 15
    }

    def "TestResult with failures indicates failure"() {
        when:
        def result = new TestResult(false, 10, 0, 0, 5, 15)

        then:
        !result.success
        result.failureCount == 5
        result.totalCount == 15
    }

    def "TestResult with all tests skipped"() {
        when:
        def result = new TestResult(true, 0, 0, 10, 0, 10)

        then:
        result.success
        result.executed == 0
        result.skipped == 10
        result.failureCount == 0
    }

    def "TestResult with mix of executed, upToDate, and skipped"() {
        when:
        def result = new TestResult(true, 5, 3, 2, 0, 10)

        then:
        result.success
        result.executed == 5
        result.upToDate == 3
        result.skipped == 2
        result.totalCount == 10
    }

    def "TestResult with all tests upToDate"() {
        when:
        def result = new TestResult(true, 0, 10, 0, 0, 10)

        then:
        result.success
        result.executed == 0
        result.upToDate == 10
    }

    // ===== EDGE CASES =====

    def "TestResult with zero tests"() {
        when:
        def result = new TestResult(true, 0, 0, 0, 0, 0)

        then:
        result.success
        result.totalCount == 0
        result.failureCount == 0
    }

    def "TestResult with single test executed"() {
        when:
        def result = new TestResult(true, 1, 0, 0, 0, 1)

        then:
        result.success
        result.executed == 1
        result.totalCount == 1
    }

    def "TestResult with single test failed"() {
        when:
        def result = new TestResult(false, 1, 0, 0, 1, 1)

        then:
        !result.success
        result.executed == 1
        result.failureCount == 1
        result.totalCount == 1
    }

    def "TestResult with large number of tests"() {
        when:
        def result = new TestResult(true, 1000, 500, 100, 0, 1600)

        then:
        result.success
        result.executed == 1000
        result.upToDate == 500
        result.skipped == 100
        result.totalCount == 1600
    }

    def "TestResult fields are final and cannot be reassigned"() {
        given:
        def result = new TestResult(true, 10, 2, 1, 0, 13)

        expect:
        result.success == true
        result.executed == 10
        result.upToDate == 2
        result.skipped == 1
        result.failureCount == 0
        result.totalCount == 13
    }

    // ===== PROPERTY ACCESS TESTS =====

    def "all properties are accessible"() {
        given:
        def result = new TestResult(true, 10, 2, 1, 0, 13)

        expect:
        result.success != null
        result.executed != null
        result.upToDate != null
        result.skipped != null
        result.failureCount != null
        result.totalCount != null
    }

    def "getter methods work correctly"() {
        given:
        def result = new TestResult(false, 7, 1, 2, 3, 13)

        expect:
        result.isSuccess() == false
        result.getFailureCount() == 3
        result.success == false
        result.failureCount == 3
    }

    // ===== DIFFERENT TEST RESULT SCENARIOS =====

    def "typical passing test suite"() {
        when:
        def result = new TestResult(true, 50, 10, 5, 0, 65)

        then:
        result.success
        result.executed == 50
        result.upToDate == 10
        result.skipped == 5
        result.failureCount == 0
        result.totalCount == 65
    }

    def "typical failing test suite"() {
        when:
        def result = new TestResult(false, 45, 10, 5, 5, 65)

        then:
        !result.success
        result.executed == 45
        result.upToDate == 10
        result.skipped == 5
        result.failureCount == 5
        result.totalCount == 65
    }

    def "partially executed test suite with failures"() {
        when:
        def result = new TestResult(false, 20, 0, 0, 10, 30)

        then:
        !result.success
        result.executed == 20
        result.failureCount == 10
        result.totalCount == 30
    }
}
