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

    def "sleep with 1 millisecond completes quickly"() {
        given:
        long startTime = System.currentTimeMillis()

        when:
        timeService.sleep(1L)

        then:
        long elapsed = System.currentTimeMillis() - startTime
        elapsed >= 0
        elapsed <= 50
        noExceptionThrown()
    }

    def "now called multiple times shows time progression"() {
        when:
        def times = []
        for (int i = 0; i < 5; i++) {
            times << timeService.now()
            Thread.sleep(2)  // Small delay
        }

        then:
        // Each subsequent call should be at or after the previous
        for (int i = 1; i < times.size(); i++) {
            assert times[i].isAfter(times[i-1]) || times[i].isEqual(times[i-1])
        }
    }

    def "now returns time with valid components"() {
        when:
        LocalDateTime result = timeService.now()

        then:
        result != null
        result.year >= 2023
        result.monthValue >= 1 && result.monthValue <= 12
        result.dayOfMonth >= 1 && result.dayOfMonth <= 31
        result.hour >= 0 && result.hour <= 23
        result.minute >= 0 && result.minute <= 59
        result.second >= 0 && result.second <= 59
    }

    def "sleep can be interrupted and throws InterruptedException"() {
        given:
        def interrupted = false
        Thread testThread = new Thread({
            try {
                new DefaultTimeService().sleep(5000L)
            } catch (InterruptedException e) {
                interrupted = true
            }
        })

        when:
        testThread.start()
        Thread.sleep(50)  // Give it time to start sleeping
        testThread.interrupt()
        testThread.join(1000)

        then:
        !testThread.isAlive()
        interrupted
    }

    def "sleep with Long.MAX_VALUE would sleep indefinitely"() {
        // We can't actually test sleeping forever, but we can test that it accepts the value
        // and starts sleeping. We'll interrupt it immediately.
        given:
        Thread testThread = new Thread({
            try {
                new DefaultTimeService().sleep(Long.MAX_VALUE)
            } catch (InterruptedException e) {
                // Expected
            }
        })

        when:
        testThread.start()
        Thread.sleep(50)  // Give it time to start
        testThread.interrupt()
        testThread.join(1000)

        then:
        !testThread.isAlive()
    }

    def "now returns consistent results within same millisecond"() {
        when:
        LocalDateTime time1 = timeService.now()
        LocalDateTime time2 = timeService.now()

        then:
        time1 != null
        time2 != null
        // Times should be very close, possibly equal
        java.time.Duration.between(time1, time2).toMillis() <= 10
    }

    def "sleep with maximum safe milliseconds completes"() {
        given:
        long sleepTime = 100L  // Use safe value instead of MAX_VALUE

        when:
        long startTime = System.currentTimeMillis()
        timeService.sleep(sleepTime)
        long elapsed = System.currentTimeMillis() - startTime

        then:
        elapsed >= sleepTime - 5
        noExceptionThrown()
    }

    def "now before and after sleep shows time difference"() {
        given:
        long sleepMillis = 50L

        when:
        LocalDateTime before = timeService.now()
        timeService.sleep(sleepMillis)
        LocalDateTime after = timeService.now()

        then:
        after.isAfter(before)
        long millisDiff = java.time.Duration.between(before, after).toMillis()
        millisDiff >= sleepMillis - 5
    }

    def "sleep with negative large value throws IllegalArgumentException"() {
        when:
        timeService.sleep(Long.MIN_VALUE)

        then:
        thrown(IllegalArgumentException)
    }
}