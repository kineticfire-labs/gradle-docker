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
 * Comprehensive unit tests for SaveCompression enum
 */
class SaveCompressionComprehensiveTest extends Specification {

    def "enum has all expected values"() {
        expect:
        SaveCompression.values().length == 5
        SaveCompression.values() as Set == [
            SaveCompression.NONE,
            SaveCompression.GZIP,
            SaveCompression.ZIP,
            SaveCompression.BZIP2,
            SaveCompression.XZ
        ] as Set
    }

    def "enum values have correct names"() {
        expect:
        SaveCompression.NONE.name() == "NONE"
        SaveCompression.GZIP.name() == "GZIP"
        SaveCompression.ZIP.name() == "ZIP"
        SaveCompression.BZIP2.name() == "BZIP2"
        SaveCompression.XZ.name() == "XZ"
    }

    def "enum values can be compared"() {
        expect:
        SaveCompression.NONE == SaveCompression.NONE
        SaveCompression.GZIP == SaveCompression.GZIP
        SaveCompression.NONE != SaveCompression.GZIP
        SaveCompression.ZIP != SaveCompression.BZIP2
    }

    def "enum valueOf works correctly"() {
        expect:
        SaveCompression.valueOf("NONE") == SaveCompression.NONE
        SaveCompression.valueOf("GZIP") == SaveCompression.GZIP
        SaveCompression.valueOf("ZIP") == SaveCompression.ZIP
        SaveCompression.valueOf("BZIP2") == SaveCompression.BZIP2
        SaveCompression.valueOf("XZ") == SaveCompression.XZ
    }

    def "enum valueOf throws exception for invalid values"() {
        when:
        SaveCompression.valueOf("INVALID")

        then:
        thrown(IllegalArgumentException)
    }

    def "enum can be used in switch statements"() {
        expect:
        getFileExtension(compression) == expectedExtension

        where:
        compression              | expectedExtension
        SaveCompression.NONE     | ".tar"
        SaveCompression.GZIP     | ".tar.gz"
        SaveCompression.ZIP      | ".zip"
        SaveCompression.BZIP2    | ".tar.bz2"
        SaveCompression.XZ       | ".tar.xz"
    }

    def "enum can be used in collections"() {
        given:
        def compressions = [SaveCompression.GZIP, SaveCompression.ZIP, SaveCompression.NONE]
        def compressionSet = [SaveCompression.XZ, SaveCompression.BZIP2] as Set

        expect:
        compressions.size() == 3
        compressions.contains(SaveCompression.GZIP)
        !compressions.contains(SaveCompression.XZ)
        
        compressionSet.size() == 2
        compressionSet.contains(SaveCompression.XZ)
        !compressionSet.contains(SaveCompression.NONE)
    }

    def "enum ordinal values are stable"() {
        expect:
        SaveCompression.NONE.ordinal() == 0
        SaveCompression.GZIP.ordinal() == 1
        SaveCompression.ZIP.ordinal() == 2
        SaveCompression.BZIP2.ordinal() == 3
        SaveCompression.XZ.ordinal() == 4
    }

    def "enum toString returns name"() {
        expect:
        SaveCompression.NONE.toString() == "NONE"
        SaveCompression.GZIP.toString() == "GZIP"
        SaveCompression.ZIP.toString() == "ZIP"
        SaveCompression.BZIP2.toString() == "BZIP2"
        SaveCompression.XZ.toString() == "XZ"
    }

    def "enum can be serialized and deserialized"() {
        given:
        def compression = SaveCompression.GZIP

        when:
        def serialized = compression.name()
        def deserialized = SaveCompression.valueOf(serialized)

        then:
        deserialized == compression
        deserialized.is(compression)  // Enum singleton
    }

    private String getFileExtension(SaveCompression compression) {
        switch (compression) {
            case SaveCompression.NONE:
                return ".tar"
            case SaveCompression.GZIP:
                return ".tar.gz"
            case SaveCompression.ZIP:
                return ".zip"
            case SaveCompression.BZIP2:
                return ".tar.bz2"
            case SaveCompression.XZ:
                return ".tar.xz"
            default:
                return ".unknown"
        }
    }
}