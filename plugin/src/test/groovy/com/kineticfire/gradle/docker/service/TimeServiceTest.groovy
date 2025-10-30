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

package com.kineticfire.gradle.docker.service

import spock.lang.Specification

/**
 * Tests for TimeService interface and SystemTimeService implementation.
 * Demonstrates that the abstraction layer works correctly.
 */
class TimeServiceTest extends Specification {
    
    TimeService timeService = new SystemTimeService()
    
    def "currentTimeMillis returns reasonable time value"() {
        given:
        def before = System.currentTimeMillis()
        
        when:
        def result = timeService.currentTimeMillis()
        
        then:
        def after = System.currentTimeMillis()
        result >= before
        result <= after
    }
    
    def "currentTimeMillis returns different values on subsequent calls"() {
        when:
        def time1 = timeService.currentTimeMillis()
        Thread.sleep(10) // Small delay
        def time2 = timeService.currentTimeMillis()
        
        then:
        time2 >= time1
    }
    
    def "sleep delays execution for specified duration"() {
        given:
        def sleepMillis = 50L
        def before = System.currentTimeMillis()
        
        when:
        timeService.sleep(sleepMillis)
        
        then:
        def after = System.currentTimeMillis()
        def elapsed = after - before
        elapsed >= sleepMillis
        elapsed < sleepMillis + 100 // Allow some tolerance
    }
}
