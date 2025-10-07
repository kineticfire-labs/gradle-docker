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
 * Unit tests for CompressionType enum
 */
class CompressionTypeTest extends Specification {

    // ===== FROMSTRING METHOD TESTS =====

    def "fromString handles null input"() {
        when:
        def result = CompressionType.fromString(null)

        then:
        result == CompressionType.NONE
    }

    def "fromString handles empty string"() {
        when:
        def result = CompressionType.fromString("")

        then:
        result == CompressionType.NONE
    }

    def "fromString handles 'none'"() {
        when:
        def result = CompressionType.fromString("none")

        then:
        result == CompressionType.NONE
    }

    def "fromString handles 'tar'"() {
        when:
        def result = CompressionType.fromString("tar")

        then:
        result == CompressionType.NONE
    }

    def "fromString handles 'gzip'"() {
        when:
        def result = CompressionType.fromString("gzip")

        then:
        result == CompressionType.GZIP
    }

    def "fromString handles 'gz' alias"() {
        when:
        def result = CompressionType.fromString("gz")

        then:
        result == CompressionType.GZIP
    }

    def "fromString handles 'bzip2'"() {
        when:
        def result = CompressionType.fromString("bzip2")

        then:
        result == CompressionType.BZIP2
    }

    def "fromString handles 'bz2' alias"() {
        when:
        def result = CompressionType.fromString("bz2")

        then:
        result == CompressionType.BZIP2
    }

    def "fromString handles 'xz'"() {
        when:
        def result = CompressionType.fromString("xz")

        then:
        result == CompressionType.XZ
    }

    def "fromString handles 'zip'"() {
        when:
        def result = CompressionType.fromString("zip")

        then:
        result == CompressionType.ZIP
    }

    def "fromString is case insensitive"() {
        expect:
        CompressionType.fromString("GZIP") == CompressionType.GZIP
        CompressionType.fromString("GZip") == CompressionType.GZIP
        CompressionType.fromString("Bzip2") == CompressionType.BZIP2
        CompressionType.fromString("XZ") == CompressionType.XZ
        CompressionType.fromString("ZIP") == CompressionType.ZIP
    }

    def "fromString handles unrecognized values"() {
        when:
        def result = CompressionType.fromString("unknown")

        then:
        result == CompressionType.NONE
    }

    // ===== GETFILEEXTENSION TESTS =====

    def "getFileExtension returns correct extension for NONE"() {
        expect:
        CompressionType.NONE.fileExtension == "tar"
    }

    def "getFileExtension returns correct extension for GZIP"() {
        expect:
        CompressionType.GZIP.fileExtension == "tar.gz"
    }

    def "getFileExtension returns correct extension for BZIP2"() {
        expect:
        CompressionType.BZIP2.fileExtension == "tar.bz2"
    }

    def "getFileExtension returns correct extension for XZ"() {
        expect:
        CompressionType.XZ.fileExtension == "tar.xz"
    }

    def "getFileExtension returns correct extension for ZIP"() {
        expect:
        CompressionType.ZIP.fileExtension == "zip"
    }

    // ===== TOSTRING TESTS =====

    def "toString returns type name for NONE"() {
        expect:
        CompressionType.NONE.toString() == "none"
    }

    def "toString returns type name for GZIP"() {
        expect:
        CompressionType.GZIP.toString() == "gzip"
    }

    def "toString returns type name for BZIP2"() {
        expect:
        CompressionType.BZIP2.toString() == "bzip2"
    }

    def "toString returns type name for XZ"() {
        expect:
        CompressionType.XZ.toString() == "xz"
    }

    def "toString returns type name for ZIP"() {
        expect:
        CompressionType.ZIP.toString() == "zip"
    }

    // ===== ENUM PROPERTIES TESTS =====

    def "NONE enum has correct properties"() {
        expect:
        CompressionType.NONE.type == "none"
        CompressionType.NONE.extension == "tar"
    }

    def "GZIP enum has correct properties"() {
        expect:
        CompressionType.GZIP.type == "gzip"
        CompressionType.GZIP.extension == "tar.gz"
    }

    def "BZIP2 enum has correct properties"() {
        expect:
        CompressionType.BZIP2.type == "bzip2"
        CompressionType.BZIP2.extension == "tar.bz2"
    }

    def "XZ enum has correct properties"() {
        expect:
        CompressionType.XZ.type == "xz"
        CompressionType.XZ.extension == "tar.xz"
    }

    def "ZIP enum has correct properties"() {
        expect:
        CompressionType.ZIP.type == "zip"
        CompressionType.ZIP.extension == "zip"
    }
}
