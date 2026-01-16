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

package com.kineticfire.gradle.docker.model

import spock.lang.Specification

/**
 * Unit tests for WaitForLogResult and WaitForLogResult.PatternMatch
 */
class WaitForLogResultTest extends Specification {

    // ===== PatternMatch TESTS =====

    def "PatternMatch can be created with matched pattern"() {
        when:
        def match = new WaitForLogResult.PatternMatch('Started Application', true, 5L)

        then:
        match.pattern == 'Started Application'
        match.matched == true
        match.matchedAtSeconds == 5L
    }

    def "PatternMatch can be created with unmatched pattern"() {
        when:
        def match = new WaitForLogResult.PatternMatch('Ready', false, null)

        then:
        match.pattern == 'Ready'
        match.matched == false
        match.matchedAtSeconds == null
    }

    def "PatternMatch handles zero elapsed time"() {
        when:
        def match = new WaitForLogResult.PatternMatch('Immediate', true, 0L)

        then:
        match.matched == true
        match.matchedAtSeconds == 0L
    }

    def "PatternMatch stores pattern matched status and match time"() {
        // Gap #21 addition - explicit field verification
        when:
        def match = new WaitForLogResult.PatternMatch('Started.*', true, 5L)

        then:
        match.pattern == 'Started.*'
        match.matched == true
        match.matchedAtSeconds == 5L
    }

    def "PatternMatch allows null matchedAtSeconds for unmatched patterns"() {
        // Gap #21 addition
        when:
        def match = new WaitForLogResult.PatternMatch('Error', false, null)

        then:
        match.pattern == 'Error'
        match.matched == false
        match.matchedAtSeconds == null
    }

    def "PatternMatch handles large elapsed time value"() {
        // Gap #21 addition
        when:
        def match = new WaitForLogResult.PatternMatch('Pattern', true, Long.MAX_VALUE)

        then:
        match.matchedAtSeconds == Long.MAX_VALUE
    }

    // ===== SUCCESSFUL RESULT TESTS =====

    def "can create successful result with all patterns matched"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('Pattern1', true, 2L),
            new WaitForLogResult.PatternMatch('Pattern2', true, 5L)
        ]

        when:
        def result = new WaitForLogResult('app', patternMatches, true)

        then:
        result.serviceName == 'app'
        result.patternMatches.size() == 2
        result.ready == true
        result.rejected == false
        result.rejectPattern == null
        result.rejectLogLine == null
    }

    def "can create unsuccessful result with partial matches"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('Pattern1', true, 2L),
            new WaitForLogResult.PatternMatch('Pattern2', false, null)
        ]

        when:
        def result = new WaitForLogResult('db', patternMatches, false)

        then:
        result.serviceName == 'db'
        result.ready == false
        result.rejected == false
    }

    // ===== REJECTED RESULT TESTS =====

    def "can create rejected result with reject pattern match"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('Started', true, 2L),
            new WaitForLogResult.PatternMatch('Ready', false, null)
        ]

        when:
        def result = new WaitForLogResult(
            'app',
            patternMatches,
            'Exception.*NullPointer',
            'Exception: NullPointerException at line 42'
        )

        then:
        result.serviceName == 'app'
        result.ready == false
        result.rejected == true
        result.rejectPattern == 'Exception.*NullPointer'
        result.rejectLogLine == 'Exception: NullPointerException at line 42'
    }

    def "secondary constructor creates rejected result with correct fields"() {
        // Gap #16 addition
        when:
        def result = new WaitForLogResult('app', [], 'Error.*', 'Error: startup failed')

        then:
        result.serviceName == 'app'
        result.ready == false
        result.rejected == true
        result.rejectPattern == 'Error.*'
        result.rejectLogLine == 'Error: startup failed'
    }

    def "secondary constructor sets rejected true and ready false"() {
        // Gap #16 addition - verify boolean flags
        when:
        def result = new WaitForLogResult('svc', [], 'pattern', 'line')

        then:
        result.rejected == true
        result.ready == false
    }

    def "secondary constructor preserves pattern matches list"() {
        // Gap #16 addition
        given:
        def matches = [
            new WaitForLogResult.PatternMatch('P1', true, 1L),
            new WaitForLogResult.PatternMatch('P2', false, null)
        ]

        when:
        def result = new WaitForLogResult('svc', matches, 'FATAL', 'FATAL: crash')

        then:
        result.patternMatches.size() == 2
        result.matchedCount == 1
        result.rejected == true
    }

    // ===== getMatchedCount() TESTS =====

    def "getMatchedCount returns correct count for all matched"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('P1', true, 1L),
            new WaitForLogResult.PatternMatch('P2', true, 2L),
            new WaitForLogResult.PatternMatch('P3', true, 3L)
        ]
        def result = new WaitForLogResult('svc', patternMatches, true)

        expect:
        result.matchedCount == 3
    }

    def "getMatchedCount returns correct count for partial matches"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('P1', true, 1L),
            new WaitForLogResult.PatternMatch('P2', false, null),
            new WaitForLogResult.PatternMatch('P3', true, 3L)
        ]
        def result = new WaitForLogResult('svc', patternMatches, false)

        expect:
        result.matchedCount == 2
    }

    def "getMatchedCount returns zero for no matches"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('P1', false, null),
            new WaitForLogResult.PatternMatch('P2', false, null)
        ]
        def result = new WaitForLogResult('svc', patternMatches, false)

        expect:
        result.matchedCount == 0
    }

    def "getMatchedCount returns zero for empty pattern list"() {
        given:
        def result = new WaitForLogResult('svc', [], false)

        expect:
        result.matchedCount == 0
    }

    // ===== getTotalPatterns() TESTS =====

    def "getTotalPatterns returns correct count"() {
        given:
        def patternMatches = [
            new WaitForLogResult.PatternMatch('P1', true, 1L),
            new WaitForLogResult.PatternMatch('P2', false, null),
            new WaitForLogResult.PatternMatch('P3', true, 3L)
        ]
        def result = new WaitForLogResult('svc', patternMatches, false)

        expect:
        result.totalPatterns == 3
    }

    def "getTotalPatterns returns zero for empty list"() {
        given:
        def result = new WaitForLogResult('svc', [], false)

        expect:
        result.totalPatterns == 0
    }

    // ===== IMMUTABILITY TESTS =====

    def "patternMatches list is immutable"() {
        given:
        def patternMatches = [new WaitForLogResult.PatternMatch('P1', true, 1L)]
        def result = new WaitForLogResult('svc', patternMatches, true)

        when:
        result.patternMatches.add(new WaitForLogResult.PatternMatch('P2', false, null))

        then:
        thrown(UnsupportedOperationException)
    }

    // ===== EDGE CASES =====

    def "handles single pattern list"() {
        given:
        def patternMatches = [new WaitForLogResult.PatternMatch('Only', true, 1L)]
        def result = new WaitForLogResult('single', patternMatches, true)

        expect:
        result.matchedCount == 1
        result.totalPatterns == 1
        result.ready == true
    }

    def "handles service name with special characters"() {
        when:
        def result = new WaitForLogResult('my-service_v2.0', [], false)

        then:
        result.serviceName == 'my-service_v2.0'
    }
}
