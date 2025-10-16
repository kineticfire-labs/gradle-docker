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

package com.kineticfire.gradle.docker.spock

import spock.lang.Specification

/**
 * Unit tests for {@link LifecycleMode}.
 */
class LifecycleModeTest extends Specification {

    def "enum should have CLASS value"() {
        expect:
        LifecycleMode.CLASS != null
        LifecycleMode.CLASS.name() == 'CLASS'
    }

    def "enum should have METHOD value"() {
        expect:
        LifecycleMode.METHOD != null
        LifecycleMode.METHOD.name() == 'METHOD'
    }

    def "values() should return all enum values"() {
        when:
        def values = LifecycleMode.values()

        then:
        values.length == 2
        values.contains(LifecycleMode.CLASS)
        values.contains(LifecycleMode.METHOD)
    }

    def "valueOf() should return correct enum for CLASS"() {
        when:
        def result = LifecycleMode.valueOf('CLASS')

        then:
        result == LifecycleMode.CLASS
    }

    def "valueOf() should return correct enum for METHOD"() {
        when:
        def result = LifecycleMode.valueOf('METHOD')

        then:
        result == LifecycleMode.METHOD
    }

    def "valueOf() should throw exception for invalid value"() {
        when:
        LifecycleMode.valueOf('INVALID')

        then:
        thrown(IllegalArgumentException)
    }

    def "enum values should be unique"() {
        expect:
        LifecycleMode.CLASS != LifecycleMode.METHOD
    }
}
