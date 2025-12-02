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

import spock.lang.Specification

/**
 * Unit tests for {@link WorkflowLifecycle}.
 */
class WorkflowLifecycleTest extends Specification {

    def "enum should have CLASS value"() {
        expect:
        WorkflowLifecycle.CLASS != null
        WorkflowLifecycle.CLASS.name() == 'CLASS'
    }

    def "enum should have METHOD value"() {
        expect:
        WorkflowLifecycle.METHOD != null
        WorkflowLifecycle.METHOD.name() == 'METHOD'
    }

    def "values() should return all enum values"() {
        when:
        def values = WorkflowLifecycle.values()

        then:
        values.length == 2
        values.contains(WorkflowLifecycle.CLASS)
        values.contains(WorkflowLifecycle.METHOD)
    }

    def "valueOf() should return correct enum for CLASS"() {
        when:
        def result = WorkflowLifecycle.valueOf('CLASS')

        then:
        result == WorkflowLifecycle.CLASS
    }

    def "valueOf() should return correct enum for METHOD"() {
        when:
        def result = WorkflowLifecycle.valueOf('METHOD')

        then:
        result == WorkflowLifecycle.METHOD
    }

    def "valueOf() should throw exception for invalid value"() {
        when:
        WorkflowLifecycle.valueOf('INVALID')

        then:
        thrown(IllegalArgumentException)
    }

    def "enum values should be unique"() {
        expect:
        WorkflowLifecycle.CLASS != WorkflowLifecycle.METHOD
    }

    def "CLASS ordinal should be 0"() {
        expect:
        WorkflowLifecycle.CLASS.ordinal() == 0
    }

    def "METHOD ordinal should be 1"() {
        expect:
        WorkflowLifecycle.METHOD.ordinal() == 1
    }
}
