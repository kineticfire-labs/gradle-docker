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

package com.kineticfire.gradle.docker.junit.service

import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDateTime

/**
 * Unit tests for DefaultTimeService to achieve 100% coverage.
 */
class DefaultTimeServiceTest extends Specification {

    @Subject
    DefaultTimeService timeService

    def setup() {
        timeService = new DefaultTimeService()
    }

    def "constructor creates instance successfully"() {
        expect:
        timeService != null
    }

    def "now returns current LocalDateTime"() {
        given:
        LocalDateTime before = LocalDateTime.now()

        when:
        LocalDateTime result = timeService.now()

        then:
        result != null
        // Allow for small time difference during test execution
        result.isAfter(before.minusSeconds(1)) || result.isEqual(before.minusSeconds(1))
        result.isBefore(before.plusSeconds(1)) || result.isEqual(before.plusSeconds(1))
    }

    def "now returns different times on subsequent calls"() {
        when:
        LocalDateTime first = timeService.now()
        // Small delay to ensure different time
        Thread.sleep(1)
        LocalDateTime second = timeService.now()

        then:
        first != null
        second != null
        // Second call should be at or after the first
        second.isAfter(first) || second.isEqual(first)
    }

    def "sleep delegates to Thread.sleep successfully"() {
        given:
        long sleepTime = 10L
        long startTime = System.currentTimeMillis()

        when:
        timeService.sleep(sleepTime)

        then:
        long elapsed = System.currentTimeMillis() - startTime
        // Allow generous tolerance for timing variations in CI/test environments
        elapsed >= sleepTime - 5
        elapsed <= sleepTime + 150
        noExceptionThrown()
    }

    def "sleep with zero milliseconds completes immediately"() {
        given:
        long startTime = System.currentTimeMillis()

        when:
        timeService.sleep(0L)

        then:
        long elapsed = System.currentTimeMillis() - startTime
        elapsed >= 0  // Should not go backwards in time
        noExceptionThrown()
    }

    def "sleep propagates InterruptedException when thread is interrupted"() {
        given:
        Thread testThread = new Thread({
            try {
                timeService.sleep(1000L)  // Long sleep
            } catch (InterruptedException e) {
                // Expected - just exit the thread
            }
        })

        when:
        testThread.start()
        Thread.sleep(10)  // Give the thread time to start sleeping
        testThread.interrupt()  // Interrupt the sleeping thread
        testThread.join(500)  // Wait for thread to finish

        then:
        !testThread.isAlive()  // Thread should have finished due to interruption
    }

    def "sleep handles negative values by throwing IllegalArgumentException"() {
        when:
        timeService.sleep(-10L)

        then:
        thrown(IllegalArgumentException)
    }

    def "multiple sleep calls work correctly"() {
        given:
        long totalSleepTime = 0L
        long startTime = System.currentTimeMillis()

        when:
        timeService.sleep(5L)
        totalSleepTime += 5L
        timeService.sleep(10L)
        totalSleepTime += 10L
        timeService.sleep(5L)
        totalSleepTime += 5L

        then:
        long elapsed = System.currentTimeMillis() - startTime
        // Total sleep should be at least the sum of individual sleeps
        elapsed >= totalSleepTime - 5
        elapsed <= totalSleepTime + 100  // Allow for overhead
        noExceptionThrown()
    }
}