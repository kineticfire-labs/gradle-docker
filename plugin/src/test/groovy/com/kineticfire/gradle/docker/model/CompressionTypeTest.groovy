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
 * Unit tests for CompressionType
 */
class CompressionTypeTest extends Specification {

    def "enum contains expected compression types"() {
        when:
        def types = CompressionType.values()

        then:
        types.length == 2
        types.contains(CompressionType.NONE)
        types.contains(CompressionType.GZIP)
    }

    def "NONE compression type has correct properties"() {
        expect:
        CompressionType.NONE.type == "none"
        CompressionType.NONE.extension == "tar"
        CompressionType.NONE.fileExtension == "tar"
        CompressionType.NONE.toString() == "none"
    }

    def "GZIP compression type has correct properties"() {
        expect:
        CompressionType.GZIP.type == "gzip"
        CompressionType.GZIP.extension == "tar.gz"
        CompressionType.GZIP.fileExtension == "tar.gz"
        CompressionType.GZIP.toString() == "gzip"
    }

    def "fromString parses gzip variants correctly"() {
        expect:
        CompressionType.fromString("gzip") == CompressionType.GZIP
        CompressionType.fromString("GZIP") == CompressionType.GZIP
        CompressionType.fromString("gz") == CompressionType.GZIP
        CompressionType.fromString("GZ") == CompressionType.GZIP
    }

    def "fromString parses none variants correctly"() {
        expect:
        CompressionType.fromString("none") == CompressionType.NONE
        CompressionType.fromString("NONE") == CompressionType.NONE
        CompressionType.fromString("tar") == CompressionType.NONE
        CompressionType.fromString("TAR") == CompressionType.NONE
    }

    def "fromString defaults to NONE for unknown values"() {
        expect:
        CompressionType.fromString("unknown") == CompressionType.NONE
        CompressionType.fromString("zip") == CompressionType.NONE
        CompressionType.fromString("bzip2") == CompressionType.NONE
        CompressionType.fromString("xz") == CompressionType.NONE
    }

    def "fromString handles null and empty values"() {
        expect:
        CompressionType.fromString(null) == CompressionType.NONE
        CompressionType.fromString("") == CompressionType.NONE
    }

    def "fromString handles whitespace"() {
        expect:
        CompressionType.fromString("  gzip  ") == CompressionType.NONE // whitespace not trimmed
        CompressionType.fromString("gzip ") == CompressionType.NONE // trailing space
        CompressionType.fromString(" gzip") == CompressionType.NONE // leading space
    }

    def "fromString is case insensitive for valid values"() {
        expect:
        CompressionType.fromString("Gzip") == CompressionType.GZIP
        CompressionType.fromString("None") == CompressionType.NONE
        CompressionType.fromString("TAr") == CompressionType.NONE
        CompressionType.fromString("Gz") == CompressionType.GZIP
    }

    def "getFileExtension method works correctly"() {
        expect:
        CompressionType.NONE.getFileExtension() == "tar"
        CompressionType.GZIP.getFileExtension() == "tar.gz"
    }

    def "enum can be used in switch statements"() {
        given:
        def getDescription = { CompressionType type ->
            switch (type) {
                case CompressionType.NONE:
                    return "No compression"
                case CompressionType.GZIP:
                    return "GZIP compression"
                default:
                    return "Unknown"
            }
        }

        expect:
        getDescription(CompressionType.NONE) == "No compression"
        getDescription(CompressionType.GZIP) == "GZIP compression"
    }

    def "enum values are immutable"() {
        expect:
        // Enum values should be constants
        CompressionType.NONE.is(CompressionType.valueOf("NONE"))
        CompressionType.GZIP.is(CompressionType.valueOf("GZIP"))
    }

    def "default case in fromString switch returns NONE"() {
        expect:
        // Test that default case is reached and returns NONE
        CompressionType.fromString("invalid") == CompressionType.NONE
        CompressionType.fromString("123") == CompressionType.NONE
        CompressionType.fromString("symbols") == CompressionType.NONE
    }
}