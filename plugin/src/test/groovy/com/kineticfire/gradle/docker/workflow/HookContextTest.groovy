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
 * Unit tests for HookContext
 */
class HookContextTest extends Specification {

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with all properties"() {
        when:
        def context = new HookContext(
            taskName: 'dockerBuildMyApp',
            pipelineName: 'ci',
            timestamp: 1234567890L,
            phase: 'before'
        )

        then:
        context.taskName == 'dockerBuildMyApp'
        context.pipelineName == 'ci'
        context.timestamp == 1234567890L
        context.phase == 'before'
    }

    def "constructor with after phase"() {
        when:
        def context = new HookContext(
            taskName: 'integrationTest',
            pipelineName: 'staging',
            timestamp: 9876543210L,
            phase: 'after'
        )

        then:
        context.taskName == 'integrationTest'
        context.pipelineName == 'staging'
        context.timestamp == 9876543210L
        context.phase == 'after'
    }

    // ===== FACTORY METHOD TESTS - before() =====

    def "before() factory method creates context with before phase"() {
        when:
        def context = HookContext.before('dockerBuildApp', 'ci')

        then:
        context.taskName == 'dockerBuildApp'
        context.pipelineName == 'ci'
        context.phase == 'before'
        context.timestamp > 0
    }

    def "before() factory method sets current timestamp"() {
        given:
        def beforeTime = System.currentTimeMillis()

        when:
        def context = HookContext.before('buildTask', 'pipeline')

        then:
        def afterTime = System.currentTimeMillis()
        context.timestamp >= beforeTime
        context.timestamp <= afterTime
    }

    def "before() factory method with various task names"() {
        expect:
        HookContext.before(taskName, 'pipeline').taskName == taskName

        where:
        taskName << ['dockerBuildApp', 'composeUpStack', 'integrationTest', 'workflowCiTagOnSuccess']
    }

    def "before() factory method with various pipeline names"() {
        expect:
        HookContext.before('task', pipelineName).pipelineName == pipelineName

        where:
        pipelineName << ['ci', 'staging', 'production', 'myPipeline']
    }

    // ===== FACTORY METHOD TESTS - after() =====

    def "after() factory method creates context with after phase"() {
        when:
        def context = HookContext.after('dockerBuildApp', 'ci')

        then:
        context.taskName == 'dockerBuildApp'
        context.pipelineName == 'ci'
        context.phase == 'after'
        context.timestamp > 0
    }

    def "after() factory method sets current timestamp"() {
        given:
        def beforeTime = System.currentTimeMillis()

        when:
        def context = HookContext.after('buildTask', 'pipeline')

        then:
        def afterTime = System.currentTimeMillis()
        context.timestamp >= beforeTime
        context.timestamp <= afterTime
    }

    def "after() factory method with various task names"() {
        expect:
        HookContext.after(taskName, 'pipeline').taskName == taskName

        where:
        taskName << ['dockerBuildApp', 'composeDownStack', 'integrationTest', 'workflowCiSuccess']
    }

    def "after() factory method with various pipeline names"() {
        expect:
        HookContext.after('task', pipelineName).pipelineName == pipelineName

        where:
        pipelineName << ['ci', 'staging', 'production', 'myPipeline']
    }

    // ===== FACTORY METHOD TESTS - of() =====

    def "of() factory method creates context with specified values"() {
        when:
        def context = HookContext.of('taskName', 'pipelineName', 1234567890L, 'before')

        then:
        context.taskName == 'taskName'
        context.pipelineName == 'pipelineName'
        context.timestamp == 1234567890L
        context.phase == 'before'
    }

    def "of() factory method with after phase"() {
        when:
        def context = HookContext.of('myTask', 'myPipeline', 9999999999L, 'after')

        then:
        context.taskName == 'myTask'
        context.pipelineName == 'myPipeline'
        context.timestamp == 9999999999L
        context.phase == 'after'
    }

    def "of() factory method useful for testing with fixed timestamp"() {
        given:
        def fixedTimestamp = 1609459200000L // 2021-01-01 00:00:00 UTC

        when:
        def context = HookContext.of('testTask', 'testPipeline', fixedTimestamp, 'before')

        then:
        context.timestamp == fixedTimestamp
    }

    // ===== IMMUTABILITY TESTS =====

    def "HookContext is immutable"() {
        given:
        def context = HookContext.before('task', 'pipeline')

        expect:
        // @Immutable annotation makes the class immutable
        // All fields should be final
        context.taskName != null
        context.pipelineName != null
        context.phase != null
        context.timestamp > 0
    }

    // ===== EQUALS AND HASHCODE TESTS =====

    def "equals() returns true for same values"() {
        given:
        def context1 = HookContext.of('task', 'pipeline', 1000L, 'before')
        def context2 = HookContext.of('task', 'pipeline', 1000L, 'before')

        expect:
        context1 == context2
        context1.hashCode() == context2.hashCode()
    }

    def "equals() returns false for different task names"() {
        given:
        def context1 = HookContext.of('task1', 'pipeline', 1000L, 'before')
        def context2 = HookContext.of('task2', 'pipeline', 1000L, 'before')

        expect:
        context1 != context2
    }

    def "equals() returns false for different pipeline names"() {
        given:
        def context1 = HookContext.of('task', 'pipeline1', 1000L, 'before')
        def context2 = HookContext.of('task', 'pipeline2', 1000L, 'before')

        expect:
        context1 != context2
    }

    def "equals() returns false for different timestamps"() {
        given:
        def context1 = HookContext.of('task', 'pipeline', 1000L, 'before')
        def context2 = HookContext.of('task', 'pipeline', 2000L, 'before')

        expect:
        context1 != context2
    }

    def "equals() returns false for different phases"() {
        given:
        def context1 = HookContext.of('task', 'pipeline', 1000L, 'before')
        def context2 = HookContext.of('task', 'pipeline', 1000L, 'after')

        expect:
        context1 != context2
    }

    // ===== TOSTRING TESTS =====

    def "toString() includes all fields"() {
        given:
        def context = HookContext.of('dockerBuildApp', 'ci', 1234567890L, 'before')

        when:
        def string = context.toString()

        then:
        string.contains('dockerBuildApp')
        string.contains('ci')
        string.contains('1234567890')
        string.contains('before')
    }

    def "toString() includes HookContext prefix"() {
        given:
        def context = HookContext.before('task', 'pipeline')

        when:
        def string = context.toString()

        then:
        // @Immutable generates toString with full class name
        string.contains('HookContext(')
        string.endsWith(')')
    }

    // ===== PROPERTY ACCESS TESTS =====

    def "all properties are accessible"() {
        given:
        def context = HookContext.before('task', 'pipeline')

        expect:
        context.taskName != null
        context.pipelineName != null
        context.timestamp != null
        context.phase != null
    }

    def "phase values are exactly 'before' or 'after'"() {
        expect:
        HookContext.before('task', 'pipeline').phase == 'before'
        HookContext.after('task', 'pipeline').phase == 'after'
    }

    // ===== EDGE CASES =====

    def "handles empty task name"() {
        when:
        def context = HookContext.before('', 'pipeline')

        then:
        context.taskName == ''
    }

    def "handles empty pipeline name"() {
        when:
        def context = HookContext.before('task', '')

        then:
        context.pipelineName == ''
    }

    def "handles null task name"() {
        when:
        def context = HookContext.before(null, 'pipeline')

        then:
        context.taskName == null
    }

    def "handles null pipeline name"() {
        when:
        def context = HookContext.before('task', null)

        then:
        context.pipelineName == null
    }

    def "handles zero timestamp"() {
        when:
        def context = HookContext.of('task', 'pipeline', 0L, 'before')

        then:
        context.timestamp == 0L
    }

    def "handles very large timestamp"() {
        given:
        def largeTimestamp = Long.MAX_VALUE

        when:
        def context = HookContext.of('task', 'pipeline', largeTimestamp, 'before')

        then:
        context.timestamp == largeTimestamp
    }

    // ===== REAL-WORLD USAGE SCENARIOS =====

    def "typical beforeBuild hook context"() {
        when:
        def context = HookContext.before('dockerBuildMyApp', 'ci')

        then:
        context.taskName == 'dockerBuildMyApp'
        context.pipelineName == 'ci'
        context.phase == 'before'
        context.timestamp > 0
    }

    def "typical afterBuild hook context"() {
        when:
        def context = HookContext.after('dockerBuildMyApp', 'ci')

        then:
        context.taskName == 'dockerBuildMyApp'
        context.pipelineName == 'ci'
        context.phase == 'after'
        context.timestamp > 0
    }

    def "typical beforeTest hook context"() {
        when:
        def context = HookContext.before('integrationTest', 'staging')

        then:
        context.taskName == 'integrationTest'
        context.pipelineName == 'staging'
        context.phase == 'before'
        context.timestamp > 0
    }

    def "typical afterSuccess hook context"() {
        when:
        def context = HookContext.after('workflowCiTagOnSuccess', 'ci')

        then:
        context.taskName == 'workflowCiTagOnSuccess'
        context.pipelineName == 'ci'
        context.phase == 'after'
        context.timestamp > 0
    }
}
