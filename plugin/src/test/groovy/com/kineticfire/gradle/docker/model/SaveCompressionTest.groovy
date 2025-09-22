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
 * Unit tests for SaveCompression
 */
class SaveCompressionTest extends Specification {

    def "enum contains all expected compression types"() {
        when:
        def types = SaveCompression.values()

        then:
        types.length == 5
        types.contains(SaveCompression.NONE)
        types.contains(SaveCompression.GZIP)
        types.contains(SaveCompression.BZIP2)
        types.contains(SaveCompression.XZ)
        types.contains(SaveCompression.ZIP)
    }

    def "NONE compression type has correct properties"() {
        expect:
        SaveCompression.NONE.type == "none"
        SaveCompression.NONE.extension == "tar"
        SaveCompression.NONE.fileExtension == "tar"
        SaveCompression.NONE.toString() == "NONE"
    }

    def "GZIP compression type has correct properties"() {
        expect:
        SaveCompression.GZIP.type == "gzip"
        SaveCompression.GZIP.extension == "tar.gz"
        SaveCompression.GZIP.fileExtension == "tar.gz"
        SaveCompression.GZIP.toString() == "GZIP"
    }

    def "BZIP2 compression type has correct properties"() {
        expect:
        SaveCompression.BZIP2.type == "bzip2"
        SaveCompression.BZIP2.extension == "tar.bz2"
        SaveCompression.BZIP2.fileExtension == "tar.bz2"
        SaveCompression.BZIP2.toString() == "BZIP2"
    }

    def "XZ compression type has correct properties"() {
        expect:
        SaveCompression.XZ.type == "xz"
        SaveCompression.XZ.extension == "tar.xz"
        SaveCompression.XZ.fileExtension == "tar.xz"
        SaveCompression.XZ.toString() == "XZ"
    }

    def "ZIP compression type has correct properties"() {
        expect:
        SaveCompression.ZIP.type == "zip"
        SaveCompression.ZIP.extension == "zip"
        SaveCompression.ZIP.fileExtension == "zip"
        SaveCompression.ZIP.toString() == "ZIP"
    }

    def "fromString parses GZIP variants correctly"() {
        expect:
        SaveCompression.fromString("gzip") == SaveCompression.GZIP
        SaveCompression.fromString("GZIP") == SaveCompression.GZIP
        SaveCompression.fromString("gz") == SaveCompression.GZIP
        SaveCompression.fromString("GZ") == SaveCompression.GZIP
    }

    def "fromString parses BZIP2 variants correctly"() {
        expect:
        SaveCompression.fromString("bzip2") == SaveCompression.BZIP2
        SaveCompression.fromString("BZIP2") == SaveCompression.BZIP2
        SaveCompression.fromString("bz2") == SaveCompression.BZIP2
        SaveCompression.fromString("BZ2") == SaveCompression.BZIP2
    }

    def "fromString parses XZ variants correctly"() {
        expect:
        SaveCompression.fromString("xz") == SaveCompression.XZ
        SaveCompression.fromString("XZ") == SaveCompression.XZ
    }

    def "fromString parses ZIP variants correctly"() {
        expect:
        SaveCompression.fromString("zip") == SaveCompression.ZIP
        SaveCompression.fromString("ZIP") == SaveCompression.ZIP
    }

    def "fromString parses NONE variants correctly"() {
        expect:
        SaveCompression.fromString("none") == SaveCompression.NONE
        SaveCompression.fromString("NONE") == SaveCompression.NONE
        SaveCompression.fromString("tar") == SaveCompression.NONE
        SaveCompression.fromString("TAR") == SaveCompression.NONE
    }

    def "fromString returns NONE for unknown values"() {
        expect:
        SaveCompression.fromString("unknown") == SaveCompression.NONE
        SaveCompression.fromString("lzma") == SaveCompression.NONE
        SaveCompression.fromString("7z") == SaveCompression.NONE
        SaveCompression.fromString("rar") == SaveCompression.NONE
    }

    def "fromString handles null and empty string"() {
        expect:
        SaveCompression.fromString(null) == SaveCompression.NONE
        SaveCompression.fromString("") == SaveCompression.NONE
    }

    def "fromString is case insensitive for valid types"() {
        expect:
        SaveCompression.fromString("Gzip") == SaveCompression.GZIP
        SaveCompression.fromString("None") == SaveCompression.NONE
        SaveCompression.fromString("TAr") == SaveCompression.NONE
        SaveCompression.fromString("Gz") == SaveCompression.GZIP
    }

    def "getFileExtension returns correct values"() {
        expect:
        SaveCompression.NONE.getFileExtension() == "tar"
        SaveCompression.GZIP.getFileExtension() == "tar.gz"
        SaveCompression.BZIP2.getFileExtension() == "tar.bz2"
        SaveCompression.XZ.getFileExtension() == "tar.xz"
        SaveCompression.ZIP.getFileExtension() == "zip"
    }

    def "can be used in switch statements"() {
        given:
        def getDescription = { SaveCompression type ->
            switch (type) {
                case SaveCompression.NONE:
                    return "No compression"
                case SaveCompression.GZIP:
                    return "GZIP compression"
                case SaveCompression.BZIP2:
                    return "BZIP2 compression"
                case SaveCompression.XZ:
                    return "XZ compression"
                case SaveCompression.ZIP:
                    return "ZIP compression"
                default:
                    return "Unknown compression"
            }
        }

        expect:
        getDescription(SaveCompression.NONE) == "No compression"
        getDescription(SaveCompression.GZIP) == "GZIP compression"
        getDescription(SaveCompression.BZIP2) == "BZIP2 compression"
        getDescription(SaveCompression.XZ) == "XZ compression"
        getDescription(SaveCompression.ZIP) == "ZIP compression"
    }

    def "valueOf works correctly"() {
        expect:
        SaveCompression.NONE.is(SaveCompression.valueOf("NONE"))
        SaveCompression.GZIP.is(SaveCompression.valueOf("GZIP"))
        SaveCompression.BZIP2.is(SaveCompression.valueOf("BZIP2"))
        SaveCompression.XZ.is(SaveCompression.valueOf("XZ"))
        SaveCompression.ZIP.is(SaveCompression.valueOf("ZIP"))
    }

    def "fromString handles edge cases"() {
        expect:
        SaveCompression.fromString("invalid") == SaveCompression.NONE
        SaveCompression.fromString("123") == SaveCompression.NONE
        SaveCompression.fromString("symbols") == SaveCompression.NONE
    }
}