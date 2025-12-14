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

package com.kineticfire.gradle.docker

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Unit tests for Lifecycle enum
 */
class LifecycleTest extends Specification {

    // ===== ENUM VALUES TESTS =====

    def "enum has CLASS value"() {
        expect:
        Lifecycle.CLASS != null
        Lifecycle.CLASS.name() == 'CLASS'
    }

    def "enum has METHOD value"() {
        expect:
        Lifecycle.METHOD != null
        Lifecycle.METHOD.name() == 'METHOD'
    }

    def "enum has exactly two values"() {
        expect:
        Lifecycle.values().length == 2
        Lifecycle.values() as Set == [Lifecycle.CLASS, Lifecycle.METHOD] as Set
    }

    // ===== fromString TESTS =====

    @Unroll
    def "fromString converts '#input' to #expected"() {
        expect:
        Lifecycle.fromString(input) == expected

        where:
        input    | expected
        'class'  | Lifecycle.CLASS
        'CLASS'  | Lifecycle.CLASS
        'Class'  | Lifecycle.CLASS
        'method' | Lifecycle.METHOD
        'METHOD' | Lifecycle.METHOD
        'Method' | Lifecycle.METHOD
    }

    def "fromString throws exception for null"() {
        when:
        Lifecycle.fromString(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('null')
    }

    def "fromString throws exception for invalid value"() {
        when:
        Lifecycle.fromString('invalid')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid lifecycle value 'invalid'")
        e.message.contains("'class' or 'method'")
    }

    def "fromString throws exception for empty string"() {
        when:
        Lifecycle.fromString('')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid lifecycle value ''")
    }

    // ===== toPropertyValue TESTS =====

    def "toPropertyValue returns 'class' for CLASS"() {
        expect:
        Lifecycle.CLASS.toPropertyValue() == 'class'
    }

    def "toPropertyValue returns 'method' for METHOD"() {
        expect:
        Lifecycle.METHOD.toPropertyValue() == 'method'
    }

    // ===== ROUND-TRIP TESTS =====

    def "fromString and toPropertyValue are inverse operations for CLASS"() {
        given:
        def lifecycle = Lifecycle.CLASS

        expect:
        Lifecycle.fromString(lifecycle.toPropertyValue()) == lifecycle
    }

    def "fromString and toPropertyValue are inverse operations for METHOD"() {
        given:
        def lifecycle = Lifecycle.METHOD

        expect:
        Lifecycle.fromString(lifecycle.toPropertyValue()) == lifecycle
    }

    // ===== SERIALIZATION TESTS =====

    def "enum is serializable"() {
        given:
        def lifecycle = Lifecycle.CLASS

        when:
        def baos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(baos)
        oos.writeObject(lifecycle)
        oos.close()

        def bais = new ByteArrayInputStream(baos.toByteArray())
        def ois = new ObjectInputStream(bais)
        def deserialized = ois.readObject()
        ois.close()

        then:
        deserialized == lifecycle
        deserialized.is(Lifecycle.CLASS) // Same instance due to enum singleton
    }
}
