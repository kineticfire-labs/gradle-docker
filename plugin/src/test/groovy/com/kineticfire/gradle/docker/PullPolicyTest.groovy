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
 * Unit tests for PullPolicy enum
 */
class PullPolicyTest extends Specification {

    // ===== ENUM VALUES TESTS =====

    def "enum has NEVER value"() {
        expect:
        PullPolicy.NEVER != null
        PullPolicy.NEVER.name() == 'NEVER'
    }

    def "enum has IF_MISSING value"() {
        expect:
        PullPolicy.IF_MISSING != null
        PullPolicy.IF_MISSING.name() == 'IF_MISSING'
    }

    def "enum has ALWAYS value"() {
        expect:
        PullPolicy.ALWAYS != null
        PullPolicy.ALWAYS.name() == 'ALWAYS'
    }

    def "enum has exactly three values"() {
        expect:
        PullPolicy.values().length == 3
        PullPolicy.values() as Set == [PullPolicy.NEVER, PullPolicy.IF_MISSING, PullPolicy.ALWAYS] as Set
    }

    // ===== fromString TESTS =====

    @Unroll
    def "fromString converts '#input' to #expected"() {
        expect:
        PullPolicy.fromString(input) == expected

        where:
        input        | expected
        'never'      | PullPolicy.NEVER
        'NEVER'      | PullPolicy.NEVER
        'Never'      | PullPolicy.NEVER
        'if_missing' | PullPolicy.IF_MISSING
        'IF_MISSING' | PullPolicy.IF_MISSING
        'ifmissing'  | PullPolicy.IF_MISSING
        'IFMISSING'  | PullPolicy.IF_MISSING
        'always'     | PullPolicy.ALWAYS
        'ALWAYS'     | PullPolicy.ALWAYS
        'Always'     | PullPolicy.ALWAYS
    }

    def "fromString throws exception for null"() {
        when:
        PullPolicy.fromString(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('null')
    }

    def "fromString throws exception for invalid value"() {
        when:
        PullPolicy.fromString('invalid')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid pull policy value 'invalid'")
        e.message.contains("'never', 'if_missing', or 'always'")
    }

    def "fromString throws exception for empty string"() {
        when:
        PullPolicy.fromString('')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Invalid pull policy value ''")
    }

    // ===== fromPullIfMissing TESTS =====

    def "fromPullIfMissing returns NEVER for false"() {
        expect:
        PullPolicy.fromPullIfMissing(false) == PullPolicy.NEVER
    }

    def "fromPullIfMissing returns IF_MISSING for true"() {
        expect:
        PullPolicy.fromPullIfMissing(true) == PullPolicy.IF_MISSING
    }

    // ===== toPropertyValue TESTS =====

    def "toPropertyValue returns 'never' for NEVER"() {
        expect:
        PullPolicy.NEVER.toPropertyValue() == 'never'
    }

    def "toPropertyValue returns 'if-missing' for IF_MISSING"() {
        expect:
        PullPolicy.IF_MISSING.toPropertyValue() == 'if-missing'
    }

    def "toPropertyValue returns 'always' for ALWAYS"() {
        expect:
        PullPolicy.ALWAYS.toPropertyValue() == 'always'
    }

    // ===== SERIALIZATION TESTS =====

    def "enum is serializable"() {
        given:
        def policy = PullPolicy.IF_MISSING

        when:
        def baos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(baos)
        oos.writeObject(policy)
        oos.close()

        def bais = new ByteArrayInputStream(baos.toByteArray())
        def ois = new ObjectInputStream(bais)
        def deserialized = ois.readObject()
        ois.close()

        then:
        deserialized == policy
        deserialized.is(PullPolicy.IF_MISSING) // Same instance due to enum singleton
    }
}
