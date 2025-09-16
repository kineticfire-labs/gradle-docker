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

package com.kineticfire.gradle.docker.spec

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for SaveSpec
 */
class SaveSpecTest extends Specification {

    def project
    def saveSpec

    def setup() {
        project = ProjectBuilder.builder().build()
        saveSpec = project.objects.newInstance(SaveSpec, project)
    }

    // ===== CONSTRUCTOR TESTS =====

    def "constructor initializes with defaults"() {
        expect:
        saveSpec != null
        !saveSpec.compression.present  // No default compression anymore
        saveSpec.pullIfMissing.get() == false  // pullIfMissing still has default
    }

    // ===== PROPERTY TESTS =====

    def "compression property works correctly"() {
        when:
        saveSpec.compression.set('bzip2')

        then:
        saveSpec.compression.present
        saveSpec.compression.get() == 'bzip2'
    }

    def "compression has no default value"() {
        expect:
        !saveSpec.compression.present
    }

    def "outputFile property works correctly"() {
        given:
        def outputFile = project.file('myimage.tar')

        when:
        saveSpec.outputFile.set(outputFile)

        then:
        saveSpec.outputFile.present
        saveSpec.outputFile.get().asFile == outputFile
    }

    def "pullIfMissing property works correctly"() {
        when:
        saveSpec.pullIfMissing.set(true)

        then:
        saveSpec.pullIfMissing.present
        saveSpec.pullIfMissing.get() == true
    }

    def "pullIfMissing has default value"() {
        expect:
        saveSpec.pullIfMissing.present
        saveSpec.pullIfMissing.get() == false
    }

    // ===== COMPLETE CONFIGURATION TESTS =====

    def "complete configuration with all properties"() {
        given:
        def outputFile = project.file('complete-image.tar.gz')

        when:
        saveSpec.compression.set('gzip')
        saveSpec.outputFile.set(outputFile)
        saveSpec.pullIfMissing.set(true)

        then:
        saveSpec.compression.get() == 'gzip'
        saveSpec.outputFile.get().asFile == outputFile
        saveSpec.pullIfMissing.get() == true
    }

    def "configuration with different compression types"() {
        expect:
        // Test different compression types
        ['none', 'gzip', 'bzip2', 'xz', 'zip'].each { compressionType ->
            saveSpec.compression.set(compressionType)
            assert saveSpec.compression.get() == compressionType
        }
    }

    // ===== PROPERTY UPDATE TESTS =====

    def "properties can be updated after initial configuration"() {
        given:
        def initialFile = project.file('initial.tar')
        def updatedFile = project.file('updated.tar.gz')

        when:
        saveSpec.outputFile.set(initialFile)
        saveSpec.compression.set('none')
        saveSpec.pullIfMissing.set(false)

        then:
        saveSpec.outputFile.get().asFile == initialFile
        saveSpec.compression.get() == 'none'
        saveSpec.pullIfMissing.get() == false

        when:
        saveSpec.outputFile.set(updatedFile)
        saveSpec.compression.set('gzip')
        saveSpec.pullIfMissing.set(true)

        then:
        saveSpec.outputFile.get().asFile == updatedFile
        saveSpec.compression.get() == 'gzip'
        saveSpec.pullIfMissing.get() == true
    }

    // ===== EDGE CASES =====

    def "outputFile is initially not present"() {
        expect:
        !saveSpec.outputFile.present
    }

    def "various file types can be set"() {
        given:
        def tarFile = project.file('image.tar')
        def gzipFile = project.file('image.tar.gz')
        def bz2File = project.file('image.tar.bz2')

        expect:
        [tarFile, gzipFile, bz2File].each { file ->
            saveSpec.outputFile.set(file)
            assert saveSpec.outputFile.get().asFile == file
        }
    }

    def "compression property accepts various valid values"() {
        expect:
        ['none', 'gzip', 'bzip2', 'xz', 'zip'].each { compression ->
            saveSpec.compression.set(compression)
            assert saveSpec.compression.get() == compression
        }
    }

    // ===== NEW VALIDATION TESTS =====

    def "compression property can be explicitly set to each valid type"() {
        expect:
        // Test all valid compression types
        ['none', 'gzip', 'bzip2', 'xz', 'zip'].each { compressionType ->
            saveSpec.compression.set(compressionType)
            assert saveSpec.compression.present
            assert saveSpec.compression.get() == compressionType
        }
    }

    def "compression property accepts mixed case values"() {
        when:
        saveSpec.compression.set('GZIP')

        then:
        saveSpec.compression.present
        saveSpec.compression.get() == 'GZIP'
    }

    def "compression property can be set to empty string"() {
        when:
        saveSpec.compression.set('')

        then:
        saveSpec.compression.present
        saveSpec.compression.get() == ''
    }

    def "compression property can be reset to not present"() {
        given:
        saveSpec.compression.set('gzip')

        when:
        saveSpec.compression.set(null)

        then:
        !saveSpec.compression.present
    }
}